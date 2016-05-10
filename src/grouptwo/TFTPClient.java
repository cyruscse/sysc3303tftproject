package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;
import grouptwo.FileOperation;

/**
* TFTPClient is the user interface class for the TFTP client, it sets up
* all the variables for the TFTP transfer and then spawns a TFTPClientTransfer thread.
* This class provides a test method that presets values in order to save time.
*
* @author        Cyrus Sadeghi
*/
public class TFTPClient 
{
    private String localFile, remoteFile, strRequestType, strVerbosity, strMode;
    private Boolean cliRunning, clientReady;
    private Thread tftpTransfer;
    private TFTPClientTransfer.Request requestType;
    private TFTPClientTransfer.Verbosity verbosity;
    private TFTPClientTransfer.Mode mode;
    private boolean TESTING = true;

    /**
    *   Constructor for TFTPClient, initializes data that will be used in CLI
    *
    *   @param  none
    *   @return TFTPClient
    */
    public TFTPClient() 
    {
       cliRunning = true;
       clientReady = false;
       verbosity = TFTPClientTransfer.Verbosity.NONE;
       mode = TFTPClientTransfer.Mode.TEST;
       requestType = TFTPClientTransfer.Request.READ;

       localFile = new String();
       remoteFile = new String();
    }

    /**
    *   CLI for TFTP client, loops and blocks on next Scanner line in order to choose
    *   a menu item. Invalid arguments are ignored and the client transfer thread is not allowed
    *   to be created until file names have been provided
    *
    *   @param  none
    *   @return none
    */
    public void commandLine() 
    {
        Scanner sc = new Scanner(System.in);
        String scIn = new String();
        
        if (TESTING == true){
        	Testvalues();
        }

        while ( cliRunning ) 
        {
            if ( remoteFile.isEmpty() == false && localFile.isEmpty() == false ) 
            {
                clientReady = true;
            }

            System.out.println("TFTP Client");
            System.out.println("-----------");
            System.out.println("1: File to read from/write to on server (current: " + remoteFile + ")");
            System.out.println("2. File to read from/write to on client (current: " + localFile + ")");
            System.out.println("3: Read Request or Write Request (current: " + TFTPClientTransfer.requestToString(requestType) + ")");
            if ( clientReady == true )
            {
                System.out.println("4: Start transfer");
            }
            System.out.println("m: Set mode (current: " + TFTPClientTransfer.modeToString(mode) + ")");
            System.out.println("v: Set verbosity (current: " + TFTPClientTransfer.verbosityToString(verbosity) + ")");
            System.out.println("q: Quit");

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
            }

            else if ( scIn.equalsIgnoreCase("m") ) {
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

            else if ( scIn.equalsIgnoreCase("v") ) 
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
                System.exit(1);
            }

            else if ( scIn.equalsIgnoreCase("") == false ) 
            {
                System.out.println("Invalid option");
            }
       }
    }
    
    /**
    *   Sets up test values for CLI to save time
    *
    *   @param  none
    *   @return none
    */
    public void Testvalues() {
		remoteFile = "/Users/cyrus/Documents/gittest/sysc3303tftproject/build/output.dat";
		localFile = "/Users/cyrus/Documents/test.txt";
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

/**
* TFTPClientTransfer is the file transfer class. It extends Thread and is spawned by the CLI thread
* Only one TFTPClientTransfer instance exists at any given time and it handles reading and writing from/to
* the server. The class contains methods that constructs ACK, RRQ/WRQ, and DATA packets, and a method to 
* communicate with the server (both reads and writes are handled there). Once the current transfer is complete
* the thread exits and returns control to the CLI thread (to optionally start another transfer).
*
* @author        Cyrus Sadeghi
*/
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

    /**
    *   Constructor for TFTPClientTransfer, initializes data used in class and creates DatagramSocket
    *   that will be used for sending and receiving packets.
    *
    *   @param  String thread name that is sent to superclass (Thread)
    *   @param  String name of file on server
    *   @param  String name of file on local machine
    *   @param  Request request type (read or write)
    *   @param  Mode run mode (normal (direct to server) or test (through error sim))
    *   @param  Verbosity verbosity of info (ranging from none to full packet details)
    *   @return TFTPClientTransfer
    */
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

    /**
    *   Converts Request to String (used by CLI)
    *
    *   @param  Request to convert to String
    *   @return String of converted Request
    */
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

    /**
    *   Converts Mode to String (used by CLI)
    *
    *   @param  Mode to convert to String
    *   @return String of converted Mode
    */
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

    /**
    *   Converts Verbosity to String (used by CLI)
    *
    *   @param  Verbosity to convert to String
    *   @return String of converted Verbosity
    */
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

    /**
    *   Constructs the request packet, which consists of the opcode (01 for read, 02 for write), 0 byte,
    *   filename and another 0 byte.
    *
    *   @param  byte[] filename as byte array
    *   @return int length of request packet
    */
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

    /**
    *   Constructs ACK packet, converts int blockNumber to byte representation
    *
    *   @param  byte[] array to store packet data in
    *   @param  int current block number
    *   @return none
    */
    private void constructAckPacketData (byte[] msg, int blockNumber)
    {
        msg[0] = 0;
        msg[1] = 4;
        msg[2] = (byte) (blockNumber / 256);
        msg[3] = (byte) (blockNumber % 256);        
    }

    /**
    *   Constructs DATA write packet
    *
    *   @param  byte[] next data block to send
    *   @param  int current block number
    *   @param  FileOperation current file we are reading from to write to server
    *   @return int length of DATA packet
    */
    private int constructNextWritePacket(byte[] msg, int blockNumber, FileOperation writeFile) throws FileNotFoundException 
    {
       msg[0] = 0;
       msg[1] = 3;
       msg[2] = (byte) (blockNumber / 256);
       msg[3] = (byte) (blockNumber % 256);   

       return writeFile.readNextDataPacket(msg, 4);
    }

    /**
    *   Processes DATA read packet
    *
    *   @param  byte[] data sent from server to client
    *   @param  int length of data
    *   @param  FileOperation current file we are writing to
    *   @return Boolean indicating if this was the final DATA packet
    */
    private Boolean processNextReadPacket(byte[] msg, int len, FileOperation readFile) throws Exception
    {
        if (msg[0] != 0 || msg[1] != 3)
        {
            throw new Exception("Invalid data packet");
        }

        readFile.writeNextDataPacket(msg, 4, len - 4);

        if (len < 516)
        {
            if (verbose != Verbosity.NONE)
            {
                System.out.println("Received final read packet from server");
                readFile.finalizeFileWrite();
            }

            return true;
        }
        else
        {
            return false;
        }
    }

    /**
    *   Prints basic packet details
    *
    *   @param  DatagramPacket to print details of
    *   @return none
    */
    private void printPacketDetails(DatagramPacket packet) 
    {
        System.out.println("Host: " + packet.getAddress());
        System.out.println("Host port: " + packet.getPort());
        System.out.println("Length: " + packet.getLength());
    }

    /**
    *   Creates RRQ/WRQ, DATA, and ACK packets (using above methods) and sends them to server/receives from server
    *   This method deals with creating/receiving DatagramPackets
    *
    *   @param  none
    *   @return none
    */
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
                    System.out.println("byte " + j + " " + (msg[j] & 0xFF));
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
                        System.out.println("byte " + j + " " + (msg[j] & 0xFF));
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
                        System.out.println("byte " + k + " " + (msg[k] & 0xFF));
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
            Boolean willExit = false;
            k = 0;

            while ( readingFile )
            {
                msg = new byte[516];

                receivePacket = new DatagramPacket(msg, msg.length);

                if (verbose != Verbosity.NONE )
                {
                    System.out.println("Client: Waiting for next data packet");
                }
                
                try {
                      sendReceiveSocket.receive(receivePacket);
                } catch(IOException e) {
                      e.printStackTrace();
                      return;
                }

                //After receiving from server, communicate over server(/host)-supplied port to allow
                //other clients to use the request port
                sendPort = receivePacket.getPort();

                len = receivePacket.getLength();

                //Receive data packet from server
                if ( verbose != Verbosity.NONE ) 
                {
                    printPacketDetails(receivePacket);
                    if ( verbose == Verbosity.ALL )
                    {
                        System.out.println("Containing: ");
                        for (j = 0; j < receivePacket.getLength(); j++) 
                        {
                            System.out.println("byte " + j + " " + (msg[j] & 0xFF));
                        }
                    }
                }

                //Process data packet, processNextReadPacket will determine
                //if its the last data packet
                try {
                    willExit = processNextReadPacket(msg, len, fileOp);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                //Form ACK packet
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
                    System.out.println("Client: Sending ACK packet " + k);
                    printPacketDetails(sendPacket);
                    if ( verbose == Verbosity.ALL )
                    {
                        System.out.println("Containing: ");
                        for (j = 0; j < sendPacket.getLength(); j++) 
                        {
                            System.out.println("byte " + j + " " + (msg[j] & 0xFF));
                        }
                    }
                }

                try {
                      sendReceiveSocket.send(sendPacket);
                } catch(IOException e) {
                      e.printStackTrace();
                      return;
                }

                //Can't exit right after we receive the last data packet,
                //we have to acknowledge the data first
                if (willExit)
                {
                    readingFile = false;
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
