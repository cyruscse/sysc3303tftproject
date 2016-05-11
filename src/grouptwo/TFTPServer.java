package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import grouptwo.TFTPServer.Verbosity;

/**
* TFTPServer is the main class for the server, it creates an instance of the CLI class
* and handles creating multiple ClientConnectionThreads. TFTPServer delays graceful exits
* until each ClientConnectionThread has returned and blocks new ClientConnectionThreads
* once the operator has indicated that the server should shut down
*
* @author        Cyrus Sadeghi
*/
public class TFTPServer 
{
	public static enum Verbosity { NONE, SOME, ALL };
	public static enum Request { READ, WRITE, ERROR };
	private DatagramSocket receiveSocket;
	private DatagramPacket receivePacket;
	private List<Thread> clients;
	private TFTPServerCommandLine cliThread;
	private Integer runningClientCount;
	private volatile Verbosity verbosity;
	private Boolean acceptConnections;
	private byte [] data;

	/**
    *   Constructor for TFTPServer, initializes data that will be used to manage client transfer threads
    *
    *   @param  none
    *   @return TFTPServer
    */
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
    *   Called by returning ClientConnectionThread, indicates to TFTPServer that the transfer is complete
    *
    *   @param  Thread ClientConnectionThread that finished
    *   @return none
    */
	public void threadDone(Thread t)
	{
		clients.remove(t);
		runningClientCount = clients.size();
	}
	
	/**
    *   Called by CLI class, indicates to TFTPServer that it should begin exiting
    *   (i.e. by closing the receiveSocket)
    *
    *   @param  none
    *   @return none
    */
	public void initiateExit()
	{
		acceptConnections = false;
		receiveSocket.close();
	}
	
	/**
    *   Spawns CLI thread, and begins receiving DatagramPackets on port 69 from new clients
    *   Each new client spawns a ClientConnectionThread that communicates over a new port
    *   in order to allow the server to respond to new clients
    *
    *   @param  none
    *   @return none
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
    *   Called by CLI thread, sets the verbosity for new ClientConnectionThreads
    *   Note: This doesn't change verbosity for ongoing transfers
    *
    *   @param  Verbosity verbosity to set to
    *   @return none
    */
	public void setVerbosity(Verbosity v) 
	{
		this.verbosity = v;
	}
	
	public static void main(String[] args) 
	{
		TFTPServer s = new TFTPServer();
		s.receiveClients();
	}
}

/**
* TFTPServerCommandLine is the class that handles user input for TFTPServer
* Its responsibilites are to set the verbosity for new ClientConnectionThreads
* and notify the server that it should begin exiting gracefully
*
* @author        Cyrus Sadeghi
*/
class TFTPServerCommandLine extends Thread {
	//"some" verbosity prints packet details omitting data contents,
	//"all" verbosity prints everything (including the 512 data bytes)
	private TFTPServer.Verbosity verbosity;
	
	private TFTPServer parentServer;
	private Boolean cliRunning;

	/**
    *   Constructor for TFTPServerCommandLine, gets reference to TFTPServer object
    *   in order to set verbosity and initiate exit
    *
    *   @param  TFTPServer server
    *   @return TFTPServerCommandLine
    */
	public TFTPServerCommandLine(TFTPServer parent)
	{
		parentServer = parent;
		verbosity = TFTPServer.Verbosity.NONE;
		cliRunning = true;
	}

	/**
    *   Converts Verbosity to String
    *
    *   @param  Verbosity to convert to String
    *   @return String of converted Verbosity
    */
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

	/**
    *   CLI for TFTPServer, allows user to set verbosity and initiate server shutdown
    *
    *   @param  none
    *   @return none
    */
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
					System.out.println("Invalid verbosity");
				}
			}

			else if ( scIn.equalsIgnoreCase("q") ) 
			{
				parentServer.initiateExit();
				cliRunning = false;
			}
		}
	}

	public void run() 
	{
		this.commandLine();
	}
}


