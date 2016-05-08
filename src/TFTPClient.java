// TFTPClient.java
// This class is the client side for a very simple assignment based on TFTP on
// UDP/IP. The client uses one port and sends a read or write request and gets 
// the appropriate response from the server.  No actual file transfer takes place.   
package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;
import grouptwo.FileOperation;

public class TFTPClient 
{
    private String localFile, remoteFile, strRequestType;
    private Boolean clientTransferring, safeExit, attemptExit, clientReady, verbosity;
    private Thread tftpTransfer;
    private TFTPClientTransfer.Request requestType;

    public TFTPClient() 
    {
       clientTransferring = false;
       safeExit = false;
       attemptExit = false;
       clientReady = false;
       verbosity = false;

       localFile = new String();
       remoteFile = new String();
    }

    public void commandLine() 
    {
       Scanner sc = new Scanner(System.in);
       String scIn = new String();

       while ( safeExit == false ) 
       {
          if ( attemptExit == true && clientTransferring == false ) 
          {
             System.exit(1);
          }

          //need to check req type as well
          if ( clientTransferring == false && remoteFile.isEmpty() == false && localFile.isEmpty() == false ) 
          {
              clientReady = true;
          }

          if ( clientTransferring == true && tftpTransfer.getState() == Thread.State.TERMINATED ) 
          {
              System.out.println("File transfer complete");
              clientTransferring = false;
          }

          System.out.println("TFTP Client");
          System.out.println("1: File to read from/write to on server (current: " + remoteFile + ")");
          System.out.println("2. File to read from/write to on client (current: " + localFile + ")");
          System.out.println("3: Read Request or Write Request (current: " + TFTPClientTransfer.requestToString(requestType) + ")");
          if ( clientReady == true )
          {
             System.out.println("4: Start transfer");
          }
          System.out.println("v: Toggle verbosity (current: " + verbosity + ")");
          System.out.println("q: Quit (finishes current transfer before quitting)");

          scIn = sc.nextLine();

          if ( scIn.equalsIgnoreCase("1") == true ) 
          {
              System.out.print("Enter filename: ");
              remoteFile = sc.nextLine();
          }

          else if ( scIn.equalsIgnoreCase("2") == true )
          {
             System.out.print("Enter filename: ");
             localFile = sc.nextLine();
          }

          else if ( scIn.equalsIgnoreCase("3") == true ) 
          {
              System.out.print("Enter request type (read or write): ");
              strRequestType = sc.nextLine();

              if ( strRequestType.equalsIgnoreCase("write") == true ) 
              {
                  requestType = TFTPClientTransfer.Request.WRITE;
              }
              else if ( strRequestType.equalsIgnoreCase("read") == true ) 
              {
                  requestType = TFTPClientTransfer.Request.READ;
              }
              else 
              {
                  System.out.println("Invalid request type");
              }
          }

          else if ( scIn.equalsIgnoreCase("3") == true && clientReady == true ) 
          {
              tftpTransfer = new TFTPClientTransfer("clientTransfer", remoteFile, localFile, requestType, TFTPClientTransfer.Mode.TEST, verbosity);
              tftpTransfer.start();
              clientTransferring = true;
          }

         // else if ( scIn.equalsIgnoreCase("m") == true && clientTransferring == false ) {

         // }

          else if ( scIn.equalsIgnoreCase("v") == true && clientTransferring == false ) 
          {
              verbosity = !verbosity;
          }

          else if ( scIn.equalsIgnoreCase("q") == true ) 
          {
              attemptExit = true;
          }

          else if ( scIn.equalsIgnoreCase("") == false ) 
          {
              System.out.println("Invalid option");
          }
       }
    }

    public static void main(String args[]) 
    {
       TFTPClient c = new TFTPClient();
       c.commandLine();
    }
}

class TFTPClientTransfer extends Thread 
{
    // we can run in normal (send directly to server) or test
    // (send to simulator) mode
    public static enum Mode { NORMAL, TEST };
    public static enum Request { READ, WRITE };
    public static enum Verbosity { NONE, }    

    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendReceiveSocket;
    private FileOperation  fileOp;
    private Mode run;
    private Request requestType;
    private String remoteName, localName, fileMode;
    private Boolean verbose;

    public TFTPClientTransfer(String threadName, String remoteFile, String localFile, Request transferType, Mode runMode, Boolean verMode)
    {
       super(threadName);

       fileMode = "octet";
       remoteName = new String(remoteFile);
       localName = new String(localFile);
       requestType = transferType;
       run = runMode;
       verbose = verMode;

       try {
          // Construct a datagram socket and bind it to any available
          // port on the local host machine. This socket will be used to
          // send and receive UDP Datagram packets.
          sendReceiveSocket = new DatagramSocket();
       } catch (SocketException se) {   // Can't create the socket.
          se.printStackTrace();
          System.exit(1);
       }
    }

    public static String requestToString(Request req) 
    {
       if ( req == Request.READ ) {
          return "read";
       }
       else if ( req == Request.WRITE ) {
          return "write";
       }
       return "";
    }

    public static String modeToString(Mode req) 
    {
       if ( req == Mode.NORMAL ) {
          return "normal";
       }
       else if ( req == Mode.TEST ) {
          return "test";
       }
       return "";
    }

    private int constructReqPacketData(byte[] msg) 
    {
       byte[] fn, // filename as an array of bytes
              md; // mode as an array of bytes
       int    len;

       msg[0] = 0;

       if ( requestType == Request.READ ) {
           msg[1] = 1;
       }
       else {
           msg[1] = 2;
       }

       // convert to bytes
       fn = remoteName.getBytes();
       
       // and copy into the msg
       System.arraycopy(fn, 0, msg, 2, fn.length);

       // now add a 0 byte
       msg[fn.length+2] = 0;

       // convert mode to bytes
       md = fileMode.getBytes();

       // and copy into the msg
       System.arraycopy(md, 0, msg, fn.length+3, md.length);

       len = fn.length+md.length+4; // length of the message
       // length of filename + length of mode + opcode (2) + two 0s (2)
       // second 0 to be added next:

       // end with another 0 byte 
       msg[len-1] = 0;

       return len;
    }

    private void constructAckPacketData (byte[] msg, int blockNumber)
    {
        msg[0] = 0;
        msg[1] = 4;
        msg[2] = (byte) (blockNumber / 128);
        msg[3] = (byte) (blockNumber % 128);
    }

    private int constructNextWritePacket(byte[] msg, int blockNumber, FileOperation writeFile) throws FileNotFoundException 
    {     
       msg[0] = 0;
       msg[1] = 3;
       msg[2] = (byte) (blockNumber / 128);
       msg[3] = (byte) (blockNumber % 128);

       return writeFile.readNextDataPacket(msg, 4) + 4;
    }

    private void processNextReadPacket(byte[] msg, FileOperation readFile) throws Exception
    {
        if ( msg[0] != 0 || msg[1] != 3 )
        {
            throw new Exception("Invalid data packet");
        }

        readFile.writeNextDataPacket(msg, 4);
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
            System.exit(1);
        }
      
        if ( verbose ) 
        {
            System.out.println("To host: " + sendPacket.getAddress());
            System.out.println("Destination host port: " + sendPacket.getPort());
            System.out.println("Length: " + sendPacket.getLength());
            System.out.println("Containing: ");
            for (j = 0; j < sendPacket.getLength(); j++) 
            {
                System.out.println("byte " + j + " " + msg[j]);
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
            System.exit(1);
        }

        msg = new byte[4];
        receivePacket = new DatagramPacket(msg, msg.length);

        System.out.println("Client: Waiting for request acknowledgement");
      
        try {
              sendReceiveSocket.receive(receivePacket);
        } catch(IOException e) {
              e.printStackTrace();
              System.exit(1);
        }

        // Process the received datagram.
        if ( verbose ) 
        {
            System.out.println("From host: " + receivePacket.getAddress());
            System.out.println("Host port: " + receivePacket.getPort());
            System.out.println("Length: " + receivePacket.getLength());
            System.out.println("Containing: ");
            for (j = 0; j < receivePacket.getLength(); j++) 
            {
                System.out.println("byte " + j + " " + msg[j]);
            }
        }

        if ( requestType == Request.WRITE ) 
        {
            fileOp = new FileOperation(localName, true, 512);

            for (j = 0; j < fileOp.getNumTFTPBlocks(); j++ ) 
            {
                msg = new byte[516];

                try {
                    len = constructNextWritePacket(msg, j, fileOp);
                } catch (FileNotFoundException e) {
                    System.out.println("File not found!");
                    System.exit(1); //TODO - can't exit here, need to get a new filename
                }
                
                System.out.println("Client: Sending TFTP packet " + (j + 1) + "/" + fileOp.getNumTFTPBlocks());

                if ( verbose ) 
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
                    System.exit(1);
                }

                // Send the datagram packet to the server via the send/receive socket.
                try {
                    sendReceiveSocket.send(sendPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                msg = new byte[4];
                receivePacket = new DatagramPacket(msg, msg.length);

                System.out.println("Client: Waiting for data write acknowledgement");
              
                try {
                      sendReceiveSocket.receive(receivePacket);
                } catch(IOException e) {
                      e.printStackTrace();
                      System.exit(1);
                }
            }
        }
        else if ( requestType == Request.READ ) 
        {
            fileOp = new FileOperation(localName, false, 512);

            Boolean readingFile = true;
            k = 0;

            while ( readingFile )
            {
                msg = new byte[516];
                receivePacket = new DatagramPacket(msg, msg.length);

                System.out.println("Client: Waiting for next data packet");
                
                try {
                      sendReceiveSocket.receive(receivePacket);
                } catch(IOException e) {
                      e.printStackTrace();
                      System.exit(1);
                }

                if ( verbose ) 
                {
                    System.out.println("From host: " + receivePacket.getAddress());
                    System.out.println("Host port: " + receivePacket.getPort());
                    System.out.println("Length: " + receivePacket.getLength());
                    System.out.println("Containing: ");
                    for (j = 0; j < receivePacket.getLength(); j++) 
                    {
                        System.out.println("byte " + j + " " + msg[j]);
                    }
                }

                try {
                    processNextReadPacket(msg, fileOp);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                msg = new byte[4];

                constructAckPacketData(msg, k);

                try {
                    sendPacket = new DatagramPacket(msg, msg.length,
                                     InetAddress.getLocalHost(), sendPort);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                k++;
            }
        }
        
        System.out.println();

        // We're finished, so close the socket.
        sendReceiveSocket.close();
    }

    public void run()
    {
        this.sendAndReceive();
    }
}


