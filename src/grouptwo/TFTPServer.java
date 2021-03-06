package grouptwo;

import java.net.*;
import java.util.*;

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
	private DatagramSocket receiveSocket;
	private DatagramPacket receivePacket;
	private List<Thread> clients;
	private List<Integer> clientPorts;
	private TFTPServerCommandLine cliThread;
	private Integer runningClientCount;
	private TFTPCommon.Verbosity verbosity;
	private Boolean acceptConnections, overwrite;
	private byte [] data;
	private int timeout;

	/**
	 *   Constructor for TFTPServer, initializes data that will be used to manage client transfer threads
	 *
	 *   @param  none
	 *   @return TFTPServer
	 */
	public TFTPServer()
	{
		try {
			receiveSocket = new DatagramSocket(TFTPCommon.TFTPListenPort);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}

		clients = new ArrayList<Thread>();
		clientPorts = new ArrayList<Integer>();
		runningClientCount = 0;
		timeout = 1000;
		acceptConnections = true;
		verbosity = TFTPCommon.Verbosity.NONE;
		overwrite = false;
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
			if (acceptConnections)
			{
				data = new byte[TFTPCommon.maxPacketSize];
				receivePacket = new DatagramPacket(data, data.length);

				TFTPCommon.receivePacket(receivePacket, receiveSocket);

				if (!receiveSocket.isClosed())
				{
					System.out.println("Server: Packet received.");

					if (!clientPorts.contains(receivePacket.getPort()))
					{
						Thread client = new Thread(new ClientConnectionThread(receivePacket, this, verbosity, clients.size() + 1, timeout, overwrite));
						clients.add(client);
						clientPorts.add(receivePacket.getPort());
						client.start();
					}
					else
					{
						System.out.println("Server: Duplicate request received from port " + receivePacket.getPort() + ". Ignoring");
					}
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
	public void setVerbosity(TFTPCommon.Verbosity v) 
	{
		this.verbosity = v;
	}

	/**
	 *   Called by CLI thread, sets the timeout for new ClientConnectionThreads
	 *   Note: This doesn't change the timeout for ongoing transfers
	 *
	 *   @param  int timeout to set to
	 *   @return none
	 */
	public void setTimeout(int newTimeout)
	{
		timeout = newTimeout;
	}

	/**
	 *   Called by CLI thread, sets the overwriting setting for new transfers
	 *
	 *   @param  Boolean overwriting setting to set
	 *   @return void
	 * 
	 */
	public void setOverwrite(Boolean overwrite)
	{
		this.overwrite = overwrite;
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

	private TFTPCommon.Verbosity verbosity;
	private TFTPServer parentServer;
	private Boolean cliRunning, overwrite;
	private int timeout;

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
		verbosity = TFTPCommon.Verbosity.NONE;
		cliRunning = true;
		overwrite = false;
		timeout = 1000;
	}

	/**
	 *   CLI for TFTPServer, allows user to set verbosity, timeout and initiate server shutdown
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
			System.out.println("-----------");
			System.out.println("o: Overwrite existing files (current: " + overwrite + ")");
			System.out.println("t: Set retransmission timeout (current: " + timeout + ")");
			System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
			System.out.println("q: Quit (quits once transfer in progress end)");

			scIn = sc.nextLine();

			if ( scIn.equalsIgnoreCase("v") ) 
			{
				System.out.print("Enter verbosity (none, some, all): ");
				String strVerbosity = sc.nextLine();

				if ( strVerbosity.equalsIgnoreCase("none") ) 
				{	
					verbosity = TFTPCommon.Verbosity.NONE;
					parentServer.setVerbosity(verbosity);
				}
				else if ( strVerbosity.equalsIgnoreCase("some") ) 
				{	
					verbosity = TFTPCommon.Verbosity.SOME;
					parentServer.setVerbosity(verbosity);
				}
				else if ( strVerbosity.equalsIgnoreCase("all") )
				{	
					verbosity = TFTPCommon.Verbosity.ALL;
					parentServer.setVerbosity(verbosity);
				}
				else 
				{
					System.out.println("Invalid verbosity");
				}
			}

			else if ( scIn.equalsIgnoreCase("t") )
			{
				System.out.print("Enter timeout (integer): ");
				scIn = sc.nextLine();

				try {
					timeout = Integer.parseInt(scIn);
				} catch (NumberFormatException e) {
					System.out.println("Input was not a number, not changing timeout");
				}

				parentServer.setTimeout(timeout);
			}

			else if ( scIn.equalsIgnoreCase("o") )
			{
				System.out.print("Enter overwrite setting (true, false): ");
				scIn = sc.nextLine();

				if ( scIn.equalsIgnoreCase("true") )
				{
					overwrite = true;
					parentServer.setOverwrite(overwrite);
				}
				else if ( scIn.equalsIgnoreCase("false") )
				{
					overwrite = false;
					parentServer.setOverwrite(overwrite);
				}
				else
				{
					System.out.println("Invalid setting");
				}
			}

			else if ( scIn.equalsIgnoreCase("q") ) 
			{
				sc.close();
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


