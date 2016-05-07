// TFTPClient.java
// This class is the client side for a very simple assignment based on TFTP on
// UDP/IP. The client uses one port and sends a read or write request and gets 
// the appropriate response from the server.  No actual file transfer takes place.   
package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;
import grouptwo.FileOperation;

public class TFTPClient {

   private String fileName, requestType;
   private Boolean clientTransferring, safeExit, attemptExit, clientReady, verbosity;
   private Thread tftpTransfer;

   public TFTPClient() {
      clientTransferring = false;
      safeExit = false;
      attemptExit = false;
      clientReady = false;
      verbosity = false;

      fileName = new String();
      requestType = new String();
   }

   public void commandLine() {
      Scanner sc = new Scanner(System.in);
      String scIn = new String();

      while ( safeExit == false ) {
         if ( attemptExit == true && clientTransferring == false ) {
            System.exit(1);
         }

         if ( clientTransferring == false && fileName.isEmpty() == false && requestType.isEmpty() == false ) {
             clientReady = true;
         }

         if ( clientTransferring == true && tftpTransfer.getState() == Thread.State.TERMINATED ) {
             System.out.println("File transfer complete");
             clientTransferring = false;
         }

         System.out.println("TFTP Client");
         System.out.println("1: File to read/write on server (current: " + fileName + ")");
         System.out.println("2: Read Request or Write Request (current: " + requestType + ")");
         if ( clientReady == true )
         {
            System.out.println("3: Start transfer");
         }
         System.out.println("v: Toggle verbosity (current: " + verbosity + ")");
         System.out.println("q: Quit (finishes current transfer before quitting)");

         scIn = sc.nextLine();

         if ( scIn.equalsIgnoreCase("1") == true ) {
             System.out.print("Enter filename: ");
             fileName = sc.nextLine();
         }
         else if ( scIn.equalsIgnoreCase("2") == true ) {
             System.out.print("Enter request type (read or write): ");
             requestType = sc.nextLine();
         }
         else if ( scIn.equalsIgnoreCase("3") == true && clientReady == true ) {
             tftpTransfer = new TFTPClientTransfer("clientTransfer", fileName, requestType, TFTPClientTransfer.Mode.NORMAL, verbosity);
             tftpTransfer.start();
             clientTransferring = true;
         }
         else if ( scIn.equalsIgnoreCase("v") == true && clientTransferring == false ) {
             verbosity = !verbosity;
         }
         else if ( scIn.equalsIgnoreCase("q") == true ) {
             attemptExit = true;
         }
         else if ( scIn.equalsIgnoreCase("") == false ) {
             System.out.println("Invalid option");
         }
      }
   }

   public static void main(String args[]) {
      TFTPClient c = new TFTPClient();
      c.commandLine();
   }
}

class TFTPClientTransfer extends Thread {
   // we can run in normal (send directly to server) or test
   // (send to simulator) mode
   public static enum Mode { NORMAL, TEST};

   private DatagramPacket sendPacket, receivePacket;
   private DatagramSocket sendReceiveSocket;
   private FileOperation  fileOp;
   private Mode run;
   private String fileName, fileMode, requestType;
   private Boolean transferring, verbose;


   public TFTPClientTransfer(String threadName, String transferFile, String transferType, Mode runMode, Boolean verMode)
   {
      super(threadName);

      fileMode = "octet";
      fileName = new String(transferFile);
      requestType = new String(transferType);
      run = runMode;
      verbose = verMode;
      transferring = true;

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

   private int constructReqPacketData(byte[] msg) {
      byte[] fn, // filename as an array of bytes
             md; // mode as an array of bytes
      int    len;

      msg[0] = 0;

      if ( requestType == "read" ) {
          msg[1] = 1;
      }
      else {
          msg[1] = 2;
      }

      // convert to bytes
      fn = fileName.getBytes();
      
      // and copy into the msg
      System.arraycopy(fn,0,msg,2,fn.length);

      // now add a 0 byte
      msg[fn.length+2] = 0;

      // convert mode to bytes
      md = fileMode.getBytes();

      // and copy into the msg
      System.arraycopy(md,0,msg,fn.length+3,md.length);

      len = fn.length+md.length+4; // length of the message
      // length of filename + length of mode + opcode (2) + two 0s (2)
      // second 0 to be added next:

      // end with another 0 byte 
      msg[len-1] = 0;

      return len;
   }

   private void sendAndReceive() {
      byte[] msg = new byte[100];
      int j, len, sendPort;
            
      if (run==Mode.NORMAL) 
         sendPort = 69;
      else
         sendPort = 23;
      
      while ( transferring ) {

        System.out.println("Client: creating packet");
         
        // Prepare a DatagramPacket and send it via sendReceiveSocket
        // to sendPort on the destination host.

        len = constructReqPacketData(msg);
        try {
           sendPacket = new DatagramPacket(msg, len,
                               InetAddress.getLocalHost(), sendPort);
        } catch (UnknownHostException e) {
           e.printStackTrace();
           System.exit(1);
        }

        System.out.println("Client: sending packet");
        System.out.println("To host: " + sendPacket.getAddress());
        System.out.println("Destination host port: " + sendPacket.getPort());
        System.out.println("Length: " + sendPacket.getLength());
        System.out.println("Containing: ");
        
        if ( verbose ) {
           for (j = 0; j < sendPacket.getLength(); j++) {
               System.out.println("byte " + j + " " + msg[j]);
           }
        }
        
        // Form a String from the byte array, and print the string.
        String sending = new String(msg,0,sendPacket.getLength());
        System.out.println(sending);

        // Send the datagram packet to the server via the send/receive socket.

        try {
           sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
           e.printStackTrace();
           System.exit(1);
        }

        System.out.println("Client: Packet sent.");

        // Construct a DatagramPacket for receiving packets up
        // to 100 bytes long (the length of the byte array).

        msg = new byte[100];
        receivePacket = new DatagramPacket(msg, msg.length);

        System.out.println("Client: Waiting for packet.");
        try {
           // Block until a datagram is received via sendReceiveSocket.
           sendReceiveSocket.receive(receivePacket);
        } catch(IOException e) {
           e.printStackTrace();
           System.exit(1);
        }

        // Process the received datagram.
        System.out.println("Client: Packet received:");
        System.out.println("From host: " + receivePacket.getAddress());
        System.out.println("Host port: " + receivePacket.getPort());
        System.out.println("Length: " + receivePacket.getLength());
        System.out.println("Containing: ");
        
        if ( verbose ) {
           for (j = 0; j < receivePacket.getLength(); j++) {
               System.out.println("byte " + j + " " + msg[j]);
           }
        }
        
        System.out.println();
        transferring = false;

      } // end of loop

      // We're finished, so close the socket.
      sendReceiveSocket.close();
   }

   public void run()
   {
      this.sendAndReceive();
   }
}


