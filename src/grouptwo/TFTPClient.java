// TFTPClient.java
// The classes in this file are an implementation of a TFTP client (without error detection/correction)
// It uses the FileOperation class to divide a file into 512 byte chunks (for read requests)
// and re-assembles 512 byte chunks into a file (for write requests) 
package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;
import grouptwo.FileOperation;

// TFTPClient
// This class is the CLI (command line interface) for the client, it sets up file names, modes, etc.
// The settings are saved over client transfers
public class TFTPClient 
{
    private String localFile, remoteFile, strRequestType, strVerbosity, strMode;
    private Boolean clientTransferring, safeExit, attemptExit, clientReady;
    private Thread tftpTransfer;
    private TFTPClientTransfer.Request requestType;
    private TFTPClientTransfer.Verbosity verbosity;
    private TFTPClientTransfer.Mode mode;
    private boolean TESTING = false;

    public TFTPClient() 
    {
       clientTransferring = false;
       safeExit = false;
       attemptExit = false;
       clientReady = false;
       verbosity = TFTPClientTransfer.Verbosity.NONE;
       mode = TFTPClientTransfer.Mode.TEST;
       requestType = TFTPClientTransfer.Request.READ;

       localFile = new String();
       remoteFile = new String();
    }

    public void commandLine() 
    {
        Scanner sc = new Scanner(System.in);
        String scIn = new String();
        
        if (TESTING == true){
        	Testvalues();
        }

        while ( safeExit == false ) 
        {
            if ( attemptExit && clientTransferring == false ) 
            {
                System.exit(1);
            }

            if ( clientTransferring == false && remoteFile.isEmpty() == false && localFile.isEmpty() == false ) 
            {
                clientReady = true;
            }

            if ( clientTransferring == true && tftpTransfer.getState() == Thread.State.TERMINATED ) 
            {
                clientTransferring = false;
            }

            if (clientTransferring == false ) 
            {
                System.out.println("TFTP Client");
                System.out.println("1: File to read from/write to on server (current: " + remoteFile + ")");
                System.out.println("2. File to read from/write to on client (current: " + localFile + ")");
                System.out.println("3: Read Request or Write Request (current: " + TFTPClientTransfer.requestToString(requestType) + ")");
                if ( clientReady == true )
                {
                    System.out.println("4: Start transfer");
                }
                System.out.println("m: Set mode (current: " + TFTPClientTransfer.modeToString(mode) + ")");
                System.out.println("v: Set verbosity (current: " + TFTPClientTransfer.verbosityToString(verbosity) + ")");
                System.out.println("q: Quit (finishes current transfer before quitting)");
            }

            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("1") ) 
            {
                System.out.print("Enter filename: ");
                remoteFile = sc.nextLine();
            }

            else if ( scIn.equalsIgnoreCase("2") )
            {
                System.out.print("Enter filename: ");
                localFile = sc.nextLine();
            }

            else if ( scIn.equalsIgnoreCase("3") ) 
            {
                System.out.print("Enter request type (read or write): ");
                strRequestType = sc.nextLine();

                if ( strRequestType.equalsIgnoreCase("write") ) 
                {
                    requestType = TFTPClientTransfer.Request.WRITE;
                }
                else if ( strRequestType.equalsIgnoreCase("read") ) 
                {
                    requestType = TFTPClientTransfer.Request.READ;
                }
                else 
                {
                    System.out.println("Invalid request type");
                }
            }

            else if ( scIn.equalsIgnoreCase("4") && clientReady == true ) 
            {
                tftpTransfer = new TFTPClientTransfer("clientTransfer", remoteFile, localFile, requestType, mode, verbosity);
                tftpTransfer.start();
                clientTransferring = true;
            }

            else if ( scIn.equalsIgnoreCase("m") && clientTransferring == false ) {
                System.out.println("Enter mode (inthost, normal): ");
                strMode = sc.nextLine();
                
                if ( strMode.equalsIgnoreCase("inthost") ) 
                {
                    mode = TFTPClientTransfer.Mode.TEST;
                }
                else if ( strMode.equalsIgnoreCase("normal") ) 
                {
                    mode = TFTPClientTransfer.Mode.NORMAL;
                }
                else 
                {
                    System.out.println("Invalid mode");
                }
            }

            else if ( scIn.equalsIgnoreCase("v") && clientTransferring == false ) 
            {
                System.out.println("Enter verbosity (none, some, all): ");
                strVerbosity = sc.nextLine();

                if ( strVerbosity.equalsIgnoreCase("none") ) 
                {
                    verbosity = TFTPClientTransfer.Verbosity.NONE;
                }
                else if ( strVerbosity.equalsIgnoreCase("some") ) 
                {
                    verbosity = TFTPClientTransfer.Verbosity.SOME;
                }
                else if ( strVerbosity.equalsIgnoreCase("all") )
                {
                    verbosity = TFTPClientTransfer.Verbosity.ALL;
                }
                else 
                {
                    System.out.println("Invalid request type");
                }
            }

            else if ( scIn.equalsIgnoreCase("q") ) 
            {
                attemptExit = true;
            }

            else if ( scIn.equalsIgnoreCase("") == false ) 
            {
                System.out.println("Invalid option");
            }
       }
    }
    
    public void Testvalues() {
		remoteFile = "C:\\Users\\majeedmirza\\Desktop\\a.txt";
		localFile = "C:\\Users\\majeedmirza\\Desktop\\t.txt";
		//requestType = TFTPClientTransfer.Request.WRITE;
		requestType = TFTPClientTransfer.Request.READ;
		//mode = TFTPClientTransfer.Mode.TEST;
		mode = TFTPClientTransfer.Mode.NORMAL;
		verbosity = TFTPClientTransfer.Verbosity.ALL;
	}

    public static void main(String args[]) 
    {
       TFTPClient c = new TFTPClient();
       c.commandLine();
    }
}

// TFTPClientTransfer
// This class performs the actual file transfers with the server and error simulator
// It sends and receives request, acknowledge and data packets
class TFTPClientTransfer extends Thread 
{
    // we can run in normal (send directly to server) or test
    // (send to simulator) mode
    public static enum Mode { NORMAL, TEST };
    public static enum Request { READ, WRITE };
    //"some" verbosity prints packet details omitting data contents,
    //"all" verbosity prints everything (including the 512 data bytes)
    public static enum Verbosity { NONE, SOME, ALL };    

    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendReceiveSocket;
    private FileOperation  fileOp;
    private Mode run;
    private Request requestType;
    private Verbosity verbose;
    private String remoteName, localName, fileMode;

    public TFTPClientTransfer(String threadName, String remoteFile, String localFile, Request transferType, Mode runMode, Verbosity verMode)
    {
       super(threadName);

       fileMode = "octet";
       remoteName = new String(remoteFile);
       localName = new String(localFile);
       requestType = transferType;
       run = runMode;
       verbose = verMode;

       try {
          sendReceiveSocket = new DatagramSocket();
       } catch (SocketException se) {
          se.printStackTrace();
          return;
       }
    }

    public static String requestToString(Request req) 
    {
        if ( req == Request.READ ) 
        {
            return "read";
        }
        else if ( req == Request.WRITE ) 
        {
            return "write";
        }
        return "";
    }

    public static String modeToString(Mode req) 
    {
        if ( req == Mode.NORMAL ) 
        {
            return "normal";
        }
        else if ( req == Mode.TEST ) 
        {
            return "test";
        }
        return "";
    }

    public static String verbosityToString(Verbosity ver)
    {
        if ( ver == Verbosity.NONE )
        {
            return "normal";
        }
        else if ( ver == Verbosity.SOME )
        {
            return "basic packet details";
        }
        else if ( ver == Verbosity.ALL )
        {
            return "full packet details (including data contents)";
        }
        return "";
    }

    private int constructReqPacketData(byte[] msg) 
    {
       byte[] fn, // filename as an array of bytes
              md; // mode as an array of bytes
       int    len;

       msg[0] = 0;

       if ( requestType == Request.READ ) 
       {
           msg[1] = 1;
       }
       else 
       {
           msg[1] = 2;
       }

       fn = remoteName.getBytes();

       System.arraycopy(fn, 0, msg, 2, fn.length);

       msg[fn.length+2] = 0;

       md = fileMode.getBytes();

       System.arraycopy(md, 0, msg, fn.length+3, md.length);

       len = fn.length+md.length+4; // (filename + mode + opcode (2) + 0s (2))

       msg[len-1] = 0;

       return len;
    }

    // Constructs acknowledge packet for TFTP reads
    // Consists of ACK opcode and block number
    private void constructAckPacketData (byte[] msg, int blockNumber)
    {
        msg[0] = 0;
        msg[1] = 4;
        
        msg[2] = (byte) (blockNumber / 256);
        msg[3] = (byte) (blockNumber % 256);        
    }

    // Constructs data packet for TFTP writes
    // Consists of DATA opcode, block number and actual data
    // Uses the FileOperation class to divide the file into data packets
    private int constructNextWritePacket(byte[] msg, int blockNumber, FileOperation writeFile) throws FileNotFoundException 
    {
       msg[0] = 0;
       msg[1] = 3;

       msg[2] = (byte) (blockNumber / 256);
       msg[3] = (byte) (blockNumber % 256);   

       return writeFile.readNextDataPacket(msg, 4) + 4;
    }

    // Processes next read packet for TFTP reads
    // Uses the FileOperation class to stitch together the data packets
    private void processNextReadPacket(byte[] msg, FileOperation readFile) throws Exception
    {
        if ( msg[0] != 0 || msg[1] != 3 )
        {
            throw new Exception("Invalid data packet");
        }

        readFile.writeNextDataPacket(msg, 4);
    }

    private void printPacketDetails(DatagramPacket packet) 
    {
        System.out.println("Host: " + packet.getAddress());
        System.out.println("Host port: " + packet.getPort());
        System.out.println("Length: " + packet.getLength());
    }

    private void sendAndReceive() 
    {
        byte[] msg = new byte[100];
        int j, k, len, sendPort;
            
        if (run == Mode.NORMAL) 
        {
            sendPort = 69;
        }
        else
        {
            sendPort = 23;
        }
      
        System.out.println("Client: sending request packet");
       
        len = constructReqPacketData(msg);

        try {
            sendPacket = new DatagramPacket(msg, len,
                             InetAddress.getLocalHost(), sendPort);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return;
        }
      
        if ( verbose != Verbosity.NONE ) 
        {
            printPacketDetails(sendPacket);
            if ( verbose == Verbosity.ALL )
            {
                System.out.println("Containing: ");
                for (j = 0; j < sendPacket.getLength(); j++) 
                {
                    System.out.println("byte " + j + " " + msg[j]);
                }
            }
        }
      
        // Form a String from the byte array, and print the string.
        String sending = new String(msg, 0, sendPacket.getLength());
        System.out.println(sending);

        // Send the datagram packet to the server via the send/receive socket.
        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if ( requestType == Request.WRITE ) 
        {
            msg = new byte[4];
            receivePacket = new DatagramPacket(msg, msg.length);

            System.out.println("Client: Waiting for request acknowledgement");
          
            try {
                  sendReceiveSocket.receive(receivePacket);
            } catch(IOException e) {
                  e.printStackTrace();
                  return;
            }

            // Process the received datagram.
            if ( verbose != Verbosity.NONE ) 
            {
                printPacketDetails(receivePacket);
                if ( verbose == Verbosity.ALL )
                {
                    System.out.println("Containing: ");
                    for (j = 0; j < receivePacket.getLength(); j++) 
                    {
                        System.out.println("byte " + j + " " + msg[j]);
                    }
                }
            }

            //After receiving from server, communicate over server(/host)-supplied port to allow
            //other clients to use the request port
            sendPort = receivePacket.getPort();

            try {
                fileOp = new FileOperation(localName, true, 512);
            } catch (FileNotFoundException e) {
                System.out.println("Local file " + localName + " does not exist!");
                return;
            }

            for (j = 0; j < fileOp.getNumTFTPBlocks(); j++ ) 
            {
                msg = new byte[516];

                try {
                    len = constructNextWritePacket(msg, j, fileOp);
                } catch (FileNotFoundException e) {
                    System.out.println("File not found!");
                    return;
                }
                
                System.out.println("Client: Sending TFTP packet " + (j + 1) + "/" + fileOp.getNumTFTPBlocks());

                if ( verbose == Verbosity.ALL ) 
                {
                    for (k = 0; k < len; k++) 
                    {
                        System.out.println("byte " + k + " " + msg[k]);
                    }
                }

                try {
                    sendPacket = new DatagramPacket(msg, len,
                                     InetAddress.getLocalHost(), sendPort);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    return;
                }

                // Send the datagram packet to the server via the send/receive socket.
                try {
                    sendReceiveSocket.send(sendPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                msg = new byte[4];
                receivePacket = new DatagramPacket(msg, msg.length);

                System.out.println("Client: Waiting for data write acknowledgement");
              
                try {
                      sendReceiveSocket.receive(receivePacket);
                } catch(IOException e) {
                      e.printStackTrace();
                      return;
                }
            }
        }
        else if ( requestType == Request.READ ) 
        {
            try {
                fileOp = new FileOperation(localName, false, 512);
            } catch (FileNotFoundException e) {
                System.out.println("Couldn't write to " + localName);
                return;
            }

            Boolean readingFile = true;
            k = 0;

            while ( readingFile )
            {
                msg = new byte[516];
                System.out.println("len " + msg.length);
                receivePacket = new DatagramPacket(msg, msg.length);

                System.out.println("Client: Waiting for next data packet");
                
                try {
                      sendReceiveSocket.receive(receivePacket);
                } catch(IOException e) {
                      e.printStackTrace();
                      return;
                }

                //After receiving from server, communicate over server(/host)-supplied port to allow
                //other clients to use the request port
                sendPort = receivePacket.getPort();

                if ( verbose != Verbosity.NONE ) 
                {
                    printPacketDetails(receivePacket);
                    if ( verbose == Verbosity.ALL )
                    {
                        System.out.println("Containing: ");
                        for (j = 0; j < receivePacket.getLength(); j++) 
                        {
                            System.out.println("byte " + j + " " + msg[j]);
                        }
                    }
                }

                try {
                    processNextReadPacket(msg, fileOp);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                msg = new byte[4];

                constructAckPacketData(msg, k);

                try {
                    sendPacket = new DatagramPacket(msg, msg.length,
                                     InetAddress.getLocalHost(), sendPort);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    return;
                }

                if ( verbose != Verbosity.NONE ) 
                {
                    printPacketDetails(sendPacket);
                    if ( verbose == Verbosity.ALL )
                    {
                        System.out.println("Containing: ");
                        for (j = 0; j < sendPacket.getLength(); j++) 
                        {
                            System.out.println("byte " + j + " " + msg[j]);
                        }
                    }
                }

                k++;
            }
        }
        
        System.out.println("File transfer complete");

        // We're finished, so close the socket.
        sendReceiveSocket.close();
    }

    public void run()
    {
        this.sendAndReceive();
    }
}
