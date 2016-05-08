package grouptwo;

// TFTPServer.java 
// This class is the server side of a simple TFTP server based on
// UDP/IP. The server receives a read or write packet from a client and
// sends back the appropriate response without any actual file transfer.
// One socket (69) is used to receive (it stays open) and another for each response. 

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TFTPServer {

   
   // UDP datagram packets and sockets used to send / receive
   private DatagramPacket receivePacket;
   private DatagramSocket receiveSocket;
   private boolean abort;
   private ArrayList<Thread> threads = new ArrayList<>(); // incase we need it later
   private int runningThreadCount = 0; 
   public TFTPServer()
   {
      try {
         // Construct a datagram socket and bind it to port 69
         // on the local host machine. This socket will be used to
         // receive UDP Datagram packets.
         receiveSocket = new DatagramSocket(69);
      } catch (SocketException se) {
         se.printStackTrace();
         System.exit(1);
      }
   }

   public void receiveAndSendTFTP() throws Exception
   {
	   byte [] data;
      for(;;) { // loop forever
         // Construct a DatagramPacket for receiving packets up
         // to 100 bytes long (the length of the byte array).
         
         data = new byte[100];
         receivePacket = new DatagramPacket(data, data.length);

         System.out.println("Server: Waiting for packet.");
         if (abort){
        	 while (runningThreadCount !=0){
        		 // wait till threads finish 
        	 }
        	 System.exit(1);
        	 }
         
         // Block until a datagram packet is received from receiveSocket.
         try {
            receiveSocket.receive(receivePacket);
         } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
         }
         Thread t = new Thread(new ClientConnectionThread(receivePacket, this));
         t.start();
         threads.add(t);
         runningThreadCount++;
      } // end of loop

   }
   public void threadDone(){
	   this.runningThreadCount--;
   }
   public static void main( String args[] ) throws Exception
   {
      TFTPServer c = new TFTPServer();
      c.receiveAndSendTFTP();
   }
}


