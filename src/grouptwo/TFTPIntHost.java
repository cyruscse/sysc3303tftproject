// TFTPSim.java
// This class is the beginnings of an error simulator for a simple TFTP server 
// based on UDP/IP. The simulator receives a read or write packet from a client and
// passes it on to the server.  Upon receiving a response, it passes it on to the 
// client.
// One socket (23) is used to receive from the client, and another to send/receive
// from the server.  A new socket is used for each communication back to the client.   
package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;

public class TFTPIntHost {
   
   // UDP datagram packets and sockets used to send / receive
   private DatagramPacket sendPacket, receivePacket;
   private DatagramSocket receiveSocket, sendReceiveSocketClient, sendReceiveSocket;
   public static enum Verbosity { NONE, SOME, ALL };
   private static Verbosity verbosity;
   
   public TFTPIntHost()
   {
      try {
         // Construct a datagram socket and bind it to port 23
         // on the local host machine. This socket will be used to
         // receive UDP Datagram packets from clients.
         receiveSocket = new DatagramSocket(23);
         // Construct a datagram socket and bind it to any available
         // port on the local host machine. This socket will be used to
         // send and receive UDP Datagram packets from the server.
         sendReceiveSocket = new DatagramSocket();
          sendReceiveSocketClient = new DatagramSocket();
      } catch (SocketException se) {
         se.printStackTrace();
         System.exit(1);
      }
   }

   public void passOnTFTP()
   {
      byte[] data;

      int serverPort=0, clientPort=0, j=0, len=0;
      data = new byte[516];
      receivePacket = new DatagramPacket(data, data.length);

      if (verbosity != Verbosity.NONE)
      {
         System.out.println("Simulator: Waiting for intial request from Client.");
      }
      else
      {
         System.out.println("Simulator: Ready to exchange packets");
      }
       
      try {
         receiveSocket.receive(receivePacket);
      } catch (IOException e) {
         e.printStackTrace();
         System.exit(1);
      }

      clientPort = receivePacket.getPort();
      len = receivePacket.getLength();
      if (verbosity != Verbosity.NONE)
      {
            System.out.println("Simulator: Packet received:");
            System.out.println("From host: " + receivePacket.getAddress());
            System.out.println("Host port: " + clientPort);
            System.out.println("Length: " + len);

            if (verbosity == Verbosity.ALL)
            {
               System.out.println("Containing: " );
               // print the bytes
               for (j=0;j<len;j++) {
                  System.out.println("byte " + j + " " + data[j]);
               }
            }
      }
      // send initial request to server
      sendPacket = new DatagramPacket(data, len,
              receivePacket.getAddress(), 69);

      len = sendPacket.getLength();
      if (verbosity != Verbosity.NONE)
      {
   	   System.out.println("Simulator: sending request packet.");
         System.out.println("To host: " + sendPacket.getAddress());
         System.out.println("Destination host port: " + sendPacket.getPort());
         System.out.println("Length: " + len);
         if (verbosity == Verbosity.ALL)
         {
            System.out.println("Containing: ");
            for (j=0;j<len;j++) {
                System.out.println("byte " + j + " " + data[j]);
            }
         }
      }

      // Send the request packet to the server via the send/receive socket.

      try {
         sendReceiveSocket.send(sendPacket);
      } catch (IOException e) {
         e.printStackTrace();
         System.exit(1);
      }      
      for(;;) { // loop forever
    	 // recieve from server
         data = new byte[516];
         receivePacket = new DatagramPacket(data, data.length);

         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: Waiting for packet from server.");
         }

         try {
            // Block until a datagram is received via sendReceiveSocket.
            sendReceiveSocket.receive(receivePacket);
         } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
         }

         serverPort = receivePacket.getPort();
         len = receivePacket.getLength();
         // Process the received datagram.
         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: Packet received from server:");
            System.out.println("From host: " + receivePacket.getAddress());
            System.out.println("Host port: " + receivePacket.getPort());
            System.out.println("Length: " + len);
            if (verbosity == Verbosity.ALL)
            {
               System.out.println("Containing: ");
               for (j=0;j<len;j++) {
                  System.out.println("byte " + j + " " + data[j]);
               }
            }
         }
         // send to client
         sendPacket = new DatagramPacket(data, receivePacket.getLength(),
                               receivePacket.getAddress(), clientPort);

         len = sendPacket.getLength();
         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: Sending packet to client:");
            System.out.println("To host: " + sendPacket.getAddress());
            System.out.println("Destination host port: " + sendPacket.getPort());
            System.out.println("Length: " + len);
            if (verbosity == Verbosity.ALL)
            {
               System.out.println("Containing: ");
               for (j=0;j<len;j++) {
                  System.out.println("byte " + j + " " + data[j]);
               }   
            }
         }   

         try {
        	 sendReceiveSocketClient.send(sendPacket);
         } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
         }

         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: packet sent using port " + sendReceiveSocketClient.getLocalPort());
            System.out.println();
         }
         
         // receive from client
         data = new byte[516];
         receivePacket = new DatagramPacket(data, data.length);

         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: Waiting for packet from client.");
         }
         
         try {
            // Block until a datagram is received via sendReceiveSocket.
        	 sendReceiveSocketClient.receive(receivePacket);
         } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
         }

         len = receivePacket.getLength();
         // Process the received datagram.
         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: Packet received from client:");
            System.out.println("From host: " + receivePacket.getAddress());
            System.out.println("Host port: " + receivePacket.getPort());
            System.out.println("Length: " + len);
            if (verbosity == Verbosity.ALL)
            {
               System.out.println("Containing: ");
               for (j=0;j<len;j++) {
                  System.out.println("byte " + j + " " + data[j]);
               }
            }
         }
         
         sendPacket = new DatagramPacket(data, len,
                 receivePacket.getAddress(), serverPort);
   	  	
         if (verbosity != Verbosity.NONE)
         {
            System.out.println("Simulator: sending packet to server.");
         }

         len = sendPacket.getLength();
         if (verbosity != Verbosity.NONE)
         {
            System.out.println("To host: " + sendPacket.getAddress());
            System.out.println("Destination host port: " + sendPacket.getPort());
            System.out.println("Length: " + len);
            if (verbosity == Verbosity.ALL)
            {
               System.out.println("Containing: ");
               for (j=0;j<len;j++) {
                   System.out.println("byte " + j + " " + data[j]);
               }
            }
         }

         // Send to server.
         try {
            sendReceiveSocket.send(sendPacket);
         } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
         }
         
      } // end of loop
      
   }

   public static void main( String args[] )
   {
      TFTPIntHost s = new TFTPIntHost();
      Scanner sc = new Scanner(System.in);
      System.out.println("Enter verbosity (none, some, all): ");
      String strVerbosity = sc.nextLine();

      if ( strVerbosity.equalsIgnoreCase("all") ) 
      {
          verbosity = Verbosity.ALL;
      }
      else if ( strVerbosity.equalsIgnoreCase("some") ) 
      {
          verbosity = Verbosity.SOME;
      }
      else
      {
          verbosity = Verbosity.NONE;
      }

      s.passOnTFTP();
   }
}


