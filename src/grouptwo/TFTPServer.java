package grouptwo;

/**
 * This class is responsible for the UI of the server, it also listens on
 * port 69 for any r/w requests and creates a thread for each request to be handled
 * it is also responisble for the safe termination of the server 
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import grouptwo.TFTPServer.Verbosity;

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
		cliThread = new TFTPServerCommandLine(this);
	}

	/**
	 * used by thread t to notify the server that its done
	 * @param t
	 */
	public void threadDone(Thread t)
	{
		clients.remove(t);
		runningClientCount = clients.size();
	}
	/**
	 * stops accepting connections from new clients
	 */
	public void initiateExit()
	{
		acceptConnections = false;
		receiveSocket.close();
	}
	/**
	 * listens on port 69 and accepts any new requests and
	 * creates a thread to handle the request
	 */
	private void receiveClients()
	{ 
		cliThread.start();

		while (runningClientCount > 0 || acceptConnections)
		{
			runningClientCount = clients.size();

			if (acceptConnections)
			{
				data = new byte[100];
				receivePacket = new DatagramPacket(data, data.length);

				System.out.println("Server: Waiting for clients.");
				
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
					Thread client = new Thread(new ClientConnectionThread(receivePacket, this, verbosity, clients.size()+1));
					client.start();
					clients.add(client);
				}
			}
		}
	}
	
	/**
	 * used to set the verbosity of the server and its threads to the given value
	 * @param v
	 */
	public void setVerbosity(Verbosity v) {
		this.verbosity = v;
	}
	
	public static void main(String[] args) 
	{
		TFTPServer s = new TFTPServer();
		s.receiveClients();
	}

	
}
/**
 * class to implement the thread 
 * responsible for handling the command line and user input
 */
class TFTPServerCommandLine extends Thread {
	//"some" verbosity prints packet details omitting data contents,
	//"all" verbosity prints everything (including the 512 data bytes)
	private TFTPServer.Verbosity verbosity;
	
	private TFTPServer parentServer;
	private Boolean cliRunning;

	public TFTPServerCommandLine(TFTPServer parent)
	{
		parentServer = parent;
		verbosity = TFTPServer.Verbosity.NONE;
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
			
			scIn = sc.nextLine();

			if ( scIn.equalsIgnoreCase("v") ) 
			{
				System.out.println("Enter verbosity (none, some, all): ");
				String strVerbosity = sc.nextLine();

				if ( strVerbosity.equalsIgnoreCase("none") ) 
				{	verbosity = TFTPServer.Verbosity.NONE;
					parentServer.setVerbosity(verbosity);
				}
				else if ( strVerbosity.equalsIgnoreCase("some") ) 
				{	verbosity = TFTPServer.Verbosity.SOME;
					parentServer.setVerbosity(verbosity);
				}
				else if ( strVerbosity.equalsIgnoreCase("all") )
				{	verbosity = TFTPServer.Verbosity.ALL;
					parentServer.setVerbosity(verbosity);
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


