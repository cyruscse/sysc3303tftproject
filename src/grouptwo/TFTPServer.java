package grouptwo;

// TFTPServer.java 
// This class is the server side of a simple TFTP server based on
// UDP/IP. The server receives a read or write packet from a client and
// sends back the appropriate response without any actual file transfer.
// One socket (69) is used to receive (it stays open) and another for each response. 

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

public class TFTPServer {
	public static enum Verbosity { NONE, SOME, ALL };
	public static enum Request { READ, WRITE, ERROR };
	private DatagramSocket receiveSocket;
	private DatagramPacket receivePacket;
	private List<Thread> clients;
	TFTPServerCommandLine cliThread;
	private Integer runningClientCount;
	private volatile Verbosity verbosity;
	private Boolean acceptConnections;
	private byte [] data;

	public TFTPServer()
	{
		try {
			receiveSocket = new DatagramSocket(69);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
		clients = new ArrayList<Thread>();
		runningClientCount = 0;
		acceptConnections = true;
		verbosity = Verbosity.NONE;
		cliThread = new TFTPServerCommandLine(verbosity, this);
	}

	public void threadDone(Thread t)
	{
		clients.remove(t);
		runningClientCount = clients.size();
	}

	public void initiateExit()
	{
		acceptConnections = false;
		receiveSocket.close();
	}

	private void receiveClients()
	{ 
		cliThread.start();

		while (runningClientCount > 0 || acceptConnections)
		{
			runningClientCount = clients.size();
			System.out.println("runningClientCount " + runningClientCount);

			if (acceptConnections)
			{
				data = new byte[100];
				receivePacket = new DatagramPacket(data, data.length);

				System.out.println("Server: Waiting for packet.");
				
				try {
					receiveSocket.receive(receivePacket);
				} catch (SocketException e) {
					System.out.println("No longer accepting new clients");
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}

				if (!receiveSocket.isClosed())
				{
					System.out.println("verbosity " + (verbosity == Verbosity.ALL));
					Thread client = new Thread(new ClientConnectionThread(receivePacket, this, verbosity));
					client.start();
					clients.add(client);
				}
			}
		}
	}

	public static void main(String[] args) 
	{
		TFTPServer s = new TFTPServer();
		s.receiveClients();
	}
}

class TFTPServerCommandLine extends Thread {
	//"some" verbosity prints packet details omitting data contents,
	//"all" verbosity prints everything (including the 512 data bytes)
	private TFTPServer.Verbosity verbosity;
	private TFTPServer parentServer;

	// UDP datagram packets and sockets used to receive
	private Boolean cliRunning;

	public TFTPServerCommandLine(TFTPServer.Verbosity serverVerbosity, TFTPServer parent)
	{
		parentServer = parent;
		verbosity = serverVerbosity;
		cliRunning = true;
	}

	public static String verbosityToString(TFTPServer.Verbosity ver)
	{
		if ( ver == TFTPServer.Verbosity.NONE )
		{
			return "normal";
		}
		else if ( ver == TFTPServer.Verbosity.SOME )
		{
			return "basic packet details";
		}
		else if ( ver == TFTPServer.Verbosity.ALL )
		{
			return "full packet details (including data contents)";
		}
		return "";
	}

	public void commandLine() 
	{
		Scanner sc = new Scanner(System.in);
		String scIn = new String();

		while (cliRunning)
		{
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
					verbosity = TFTPServer.Verbosity.NONE;
				}
				else if ( strVerbosity.equalsIgnoreCase("some") ) 
				{
					verbosity = TFTPServer.Verbosity.SOME;
				}
				else if ( strVerbosity.equalsIgnoreCase("all") )
				{
					verbosity = TFTPServer.Verbosity.ALL;
				}
				else 
				{
					System.out.println("Invalid request type");
				}
			}

			else if ( scIn.equalsIgnoreCase("q") ) 
			{
				parentServer.initiateExit();
				cliRunning = false;
			}
		}
	}

	public void run() {
		this.commandLine();
	}
}


