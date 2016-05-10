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

	// types of requests we can receive
	public static enum Request { READ, WRITE, ERROR };

	//"some" verbosity prints packet details omitting data contents,
	//"all" verbosity prints everything (including the 512 data bytes)
	public static enum Verbosity { NONE, SOME, ALL };
	private Verbosity verbosity;

	// UDP datagram packets and sockets used to receive
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	private Boolean abort;
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

		verbosity = Verbosity.NONE;
		abort = false;
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

	public void commandLine() 
	{
		Scanner sc = new Scanner(System.in);
		String scIn = new String();

		System.out.println("TFTP Server");
		System.out.println("v: Set verbosity (current: " + verbosityToString(verbosity) + ")");
		System.out.println("q: Quit (finishes current transfer before quitting)");
		System.out.println("r: run ");
		scIn = sc.nextLine();

		if ( scIn.equalsIgnoreCase("v") ) 
		{
			System.out.println("Enter verbosity (none, some, all): ");
			String strVerbosity = sc.nextLine();

			if ( strVerbosity.equalsIgnoreCase("none") ) 
			{
				verbosity = Verbosity.NONE;
			}
			else if ( strVerbosity.equalsIgnoreCase("some") ) 
			{
				verbosity = Verbosity.SOME;
			}
			else if ( strVerbosity.equalsIgnoreCase("all") )
			{
				verbosity = Verbosity.ALL;
			}
			else 
			{
				System.out.println("Invalid request type");
			}
		}

		else if ( scIn.equalsIgnoreCase("q") ) 
		{
			abort = true;
		}
		else if ( scIn.equalsIgnoreCase("r") ){
			// run
		}
	}

	public void receiveAndSendTFTP() throws Exception
	{
		byte [] data;
		Request req; // READ, WRITE or ERROR
		int len, j=0, k=0;
		String filename = new String();
		String mode = new String();


		for(;;) { // loop forever

			commandLine();
			if (abort){
				while (runningThreadCount !=0){
					// wait till threads finish 
				}
				System.exit(1);
			}

			// Construct a DatagramPacket for receiving packets up
			// to 100 bytes long (the length of the byte array).
			data = new byte[100];
			receivePacket = new DatagramPacket(data, data.length);

			System.out.println("Server: Waiting for packet.");

			// Block until a datagram packet is received from receiveSocket.
			try {
				receiveSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			System.out.println("Server: Packet received.");
			System.out.println("From host: " + receivePacket.getAddress());
			System.out.println("Host port: " + receivePacket.getPort());
			len = receivePacket.getLength();
			System.out.println("Length: " + len);
			System.out.println("Containing: " );
			data = receivePacket.getData();
			// print the bytes
			for (j=0;j<len;j++) {
				System.out.println("byte " + j + " " + data[j]);
			}

			// Form a String from the byte array.
			String received = new String(data,0,len);
			System.out.println(received);

			// If it's a read, send back DATA (03) block 1
			// If it's a write, send back ACK (04) block 0
			// Otherwise, ignore it
			if (data[0]!=0) req = Request.ERROR; // bad
			else if (data[1]==1) req = Request.READ; // could be read
			else if (data[1]==2) req = Request.WRITE; // could be write
			else req = Request.ERROR; // bad

			if (req !=Request.ERROR) { // check for filename
				// search for next all 0 byte
				for(j=2;j<len;j++) {
					if (data[j] == 0) break;
				}
				if (j==len) req=Request.ERROR; // didn't find a 0 byte
				if (j==2) req=Request.ERROR; // filename is 0 bytes long
				// otherwise, extract filename
				filename = new String(data,2,j-2);
				
			}

			if(req!=Request.ERROR) { // check for mode
				// search for next all 0 byte
				for(k=j+1;k<len;k++) { 
					if (data[k] == 0) break;
				}
				if (k==len) req=Request.ERROR; // didn't find a 0 byte
				if (k==j+1) req=Request.ERROR; // mode is 0 bytes long
				mode = new String(data,j,k-j);
				
				if ( mode.equalsIgnoreCase("octet") || mode.equalsIgnoreCase("netascii")) 
				{
					req=Request.ERROR; // mode was not passed correctly
				}
			}

			if(k!=len-1) req=Request.ERROR; // other stuff at end of packet
			
			/*TODO 	Exception should be thrown here (when we implement it) by checking if req==Request.ERROR
			 * 		instead of checking inside ClientConnectionThread */

			Thread t = new Thread(new ClientConnectionThread(receivePacket, this, req, verbosity, filename));
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


