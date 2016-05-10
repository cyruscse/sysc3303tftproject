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

      int serverPort,clientPort, j=0, len;
      data = new byte[516];
      receivePacket = new DatagramPacket(data, data.length);

      System.out.println("Simulator: Waiting for intial request from Client.");
      try {
          receiveSocket.receive(receivePacket);
       } catch (IOException e) {
          e.printStackTrace();
          System.exit(1);
       }
      System.out.println("Simulator: Packet received:");
      System.out.println("From host: " + receivePacket.getAddress());
      clientPort = receivePacket.getPort();
      System.out.println("Host port: " + clientPort);
      len = receivePacket.getLength();
      System.out.println("Length: " + len);
      System.out.println("Containing: " );
      // print the bytes
      for (j=0;j<len;j++) {
         System.out.println("byte " + j + " " + data[j]);
      }
      // send initial request to server
      sendPacket = new DatagramPacket(data, len,
              receivePacket.getAddress(), 69);
	  System.out.println("Simulator: sending request packet.");
      System.out.println("To host: " + sendPacket.getAddress());
      System.out.println("Destination host port: " + sendPacket.getPort());
      len = sendPacket.getLength();
      System.out.println("Length: " + len);
      System.out.println("Containing: ");
      for (j=0;j<len;j++) {
          System.out.println("byte " + j + " " + data[j]);
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

         System.out.println("Simulator: Waiting for packet from server.");
         try {
            // Block until a datagram is received via sendReceiveSocket.
            sendReceiveSocket.receive(receivePacket);
         } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
         }

         // Process the received datagram.
         System.out.println("Simulator: Packet received from server:");
         System.out.println("From host: " + receivePacket.getAddress());
         serverPort = receivePacket.getPort();
         System.out.println("Host port: " + receivePacket.getPort());
         len = receivePacket.getLength();
         System.out.println("Length: " + len);
         System.out.println("Containing: ");
         for (j=0;j<len;j++) {
            System.out.println("byte " + j + " " + data[j]);
         }
         // send to client
         sendPacket = new DatagramPacket(data, receivePacket.getLength(),
                               receivePacket.getAddress(), clientPort);

         System.out.println("Simulator: Sending packet to client:");
         System.out.println("To host: " + sendPacket.getAddress());
         System.out.println("Destination host port: " + sendPacket.getPort());
         len = sendPacket.getLength();
         System.out.println("Length: " + len);
         System.out.println("Containing: ");
         for (j=0;j<len;j++) {
            System.out.println("byte " + j + " " + data[j]);
         }

         

         try {
        	 sendReceiveSocketClient.send(sendPacket);
         } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
         }

         System.out.println("Simulator: packet sent using port " + sendReceiveSocketClient.getLocalPort());
         System.out.println();
         
         
         // receive from client
         data = new byte[516];
         receivePacket = new DatagramPacket(data, data.length);

         System.out.println("Simulator: Waiting for packet from client.");
         try {
            // Block until a datagram is received via sendReceiveSocket.
        	 sendReceiveSocketClient.receive(receivePacket);
         } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
         }

         // Process the received datagram.
         System.out.println("Simulator: Packet received from client:");
         System.out.println("From host: " + receivePacket.getAddress());
         System.out.println("Host port: " + receivePacket.getPort());
         len = receivePacket.getLength();
         System.out.println("Length: " + len);
         System.out.println("Containing: ");
         for (j=0;j<len;j++) {
            System.out.println("byte " + j + " " + data[j]);
         }
         
         sendPacket = new DatagramPacket(data, len,
                 receivePacket.getAddress(), serverPort);
   	  		System.out.println("Simulator: sending packet to server.");
         System.out.println("To host: " + sendPacket.getAddress());
         System.out.println("Destination host port: " + sendPacket.getPort());
         len = sendPacket.getLength();
         System.out.println("Length: " + len);
         System.out.println("Containing: ");
         for (j=0;j<len;j++) {
             System.out.println("byte " + j + " " + data[j]);
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
      s.passOnTFTP();
   }
}


