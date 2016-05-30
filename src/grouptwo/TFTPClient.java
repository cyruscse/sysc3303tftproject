package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;
import grouptwo.FileOperation;

/**
 * TFTPClient is the user interface class for the TFTP client, it sets up
 * all the variables for the TFTP transfer and then spawns a TFTPClientTransfer thread.
 * This class provides a test method that presets values in order to save time.
 *
 * @author        Cyrus Sadeghi
 */
public class TFTPClient 
{
	private String localFile, remoteFile;
	private String[] scInArr;
	private Boolean cliRunning, clientReady, clientTransferring, overwrite;
	private Thread tftpTransfer;
	private TFTPCommon.Request requestType;
	private TFTPCommon.Verbosity verbosity;
	private TFTPCommon.Mode mode;
	private int timeout;
	private InetAddress serverAddress;

	/**
	 *   Constructor for TFTPClient, initializes data that will be used in CLI
	 *
	 *   @param  none
	 *   @return TFTPClient
	 */
	public TFTPClient() 
	{
		cliRunning = true;
		clientReady = false;
		clientTransferring = false;
		overwrite = false;
		timeout = 1000;
		verbosity = TFTPCommon.Verbosity.NONE;
		mode = TFTPCommon.Mode.TEST;
		requestType = TFTPCommon.Request.ERROR;
		tftpTransfer = new Thread();
		serverAddress = InetAddress.getLoopbackAddress();

		localFile = new String();
		remoteFile = new String();
	}

	/**
	 *   Set client transferring status from client transfer thread, used for blocking certain CLI commands
	 *
	 *   @param  transferring Is the client thread transferring?
	 *   @return void
	 */
	public void clientTransferring (Boolean transferring)
	{
		clientTransferring = transferring;
		clientReady = transferring;
	}

	/**
	 *   CLI for TFTP client, loops and blocks on next Scanner line in order to choose
	 *   a menu item. Invalid arguments are ignored and the client transfer thread is not allowed
	 *   to be created until file names have been provided
	 *
	 *   @param  none
	 *   @return none
	 */
	public void commandLine() 
	{
		Scanner sc = new Scanner(System.in);
		String scIn = new String();
		Boolean printMenu = true;

		while ( cliRunning ) 
		{
			scInArr = new String[100];
			localFile = new String();
			remoteFile = new String();
			requestType = TFTPCommon.Request.ERROR;

			if (printMenu)
			{
				System.out.println("");
				System.out.println("TFTP Client");
				System.out.println("-----------");
				System.out.println("Enter transfer as string, with file names in quotes");
				System.out.println("(i.e. read \"readFile.txt\" to \"dest.txt\", write \"writeFile.txt\" to \"dest2.txt\"");
				System.out.println("");
				System.out.println("--Options--");
				System.out.println("i: Set IP address of TFTP Server (current: " + serverAddress + ")");							
				System.out.println("m: Set mode (current: " + TFTPCommon.modeToString(mode) + ")");
				System.out.println("o: Overwrite existing files (current: " + overwrite + ")");
				System.out.println("t: Set retransmission timeout (current: " + timeout + ")");
				System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
				System.out.println("q: Quit (blocked if transfer in progress)");
			}

			printMenu = true;

			scIn = sc.nextLine();
			scIn = scIn.toLowerCase();
			scInArr = scIn.split("\"");
			
			if ( scInArr.length == 4 && !clientTransferring)
			{
				if (scInArr[0].trim().equals("read"))
				{
					requestType = TFTPCommon.Request.READ;	
					remoteFile = scInArr[1].replaceAll("\"", "");
				}
				else if (scInArr[0].trim().equals("write"))
				{
					requestType = TFTPCommon.Request.WRITE;
					localFile = scInArr[1].replaceAll("\"", "");
				}
				else
				{
					System.out.println("Invalid TFTP Operation");
					requestType = TFTPCommon.Request.ERROR;
				}

				if (scInArr[2].trim().equals("to"))
				{	
					if (requestType == TFTPCommon.Request.READ)
					{
						localFile = scInArr[3].replaceAll("\"", "");
					}
					else if (requestType == TFTPCommon.Request.WRITE)
					{
						remoteFile = scInArr[3].replaceAll("\"", "");
					}
				}

				if (remoteFile.length() > 0 && localFile.length() > 0 && requestType != TFTPCommon.Request.ERROR)
				{
					tftpTransfer = new TFTPClientTransfer("clientTransfer", serverAddress, remoteFile, localFile, this, requestType, mode, verbosity, timeout, overwrite);
					tftpTransfer.start();
					printMenu = false;
				}
			}

			else if ( scIn.equalsIgnoreCase("i") )
			{
				System.out.print("Enter IP address of server: ");
				scIn = sc.nextLine();

				try
				{
					serverAddress = InetAddress.getByName(scIn);
				} 
				catch (UnknownHostException e)
				{
					System.out.println("Invalid IP address");
				}
			}

			else if ( scIn.equalsIgnoreCase("m") ) 
			{
				System.out.print("Enter mode (test, normal): ");
				scIn = sc.nextLine();

				if ( scIn.equalsIgnoreCase("test") ) 
				{
					mode = TFTPCommon.Mode.TEST;
				}
				else if ( scIn.equalsIgnoreCase("normal") ) 
				{
					mode = TFTPCommon.Mode.NORMAL;
				}
				else 
				{
					System.out.println("Invalid mode");
				}
			}

			else if ( scIn.equalsIgnoreCase("o") )
			{
				System.out.print("Enter overwrite setting (true, false): ");
				scIn = sc.nextLine();

				if ( scIn.equalsIgnoreCase("true") )
				{
					overwrite = true;
				}
				else if ( scIn.equalsIgnoreCase("false") )
				{
					overwrite = false;
				}
				else
				{
					System.out.println("Invalid setting");
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
			}

			else if ( scIn.equalsIgnoreCase("v") ) 
			{
				System.out.print("Enter verbosity (none, some, all): ");
				scIn = sc.nextLine();

				if ( scIn.equalsIgnoreCase("none") ) 
				{
					verbosity = TFTPCommon.Verbosity.NONE;
				}
				else if ( scIn.equalsIgnoreCase("some") ) 
				{
					verbosity = TFTPCommon.Verbosity.SOME;
				}
				else if ( scIn.equalsIgnoreCase("all") )
				{
					verbosity = TFTPCommon.Verbosity.ALL;
				}
				else 
				{
					System.out.println("Invalid request type");
				}
			}

			else if ( scIn.equalsIgnoreCase("q") ) 
			{   
				if (!clientTransferring)
				{
					sc.close();
					System.exit(1);
				}
				System.out.println("Can't exit during a TFTP transfer!");
			}

			else if ( !scIn.equalsIgnoreCase("") ) 
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

/**
 * TFTPClientTransfer is the file transfer class. It extends Thread and is spawned by the CLI thread
 * Only one TFTPClientTransfer instance exists at any given time and it handles reading and writing from/to
 * the server. The class contains methods that constructs ACK, RRQ/WRQ, and DATA packets, and a method to 
 * communicate with the server (both reads and writes are handled there). Once the current transfer is complete
 * the thread exits and returns control to the CLI thread (to optionally start another transfer).
 *
 * @author        Cyrus Sadeghi
 */
class TFTPClientTransfer extends Thread 
{
	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket sendReceiveSocket;
	private FileOperation  fileOp;
	private TFTPCommon.Mode run;
	private TFTPCommon.Request requestType;
	private TFTPCommon.Verbosity verbose;
	private String remoteName, localName, fileMode;
	private TFTPClient parent;
	private int sendPort;
	//hardTimeout - timeout when waiting for DATA
	private int hardTimeout;
	//timeout - timeout when sending DATA
	private int timeout;
	//maxTimeout - number of timeouts to wait before giving up
	private int maxTimeout;
	private Boolean overwrite;
	private InetAddress serverAddress;
	private final String consolePrefix = ("Client: ");

	/**
	 *   Constructor for TFTPClientTransfer, initializes data used in class and creates DatagramSocket
	 *   that will be used for sending and receiving packets.
	 *
	 *   @param  String thread name that is sent to superclass (Thread)
	 *   @param  String name of file on server
	 *   @param  String name of file on local machine
	 *   @param  Request request type (read or write)
	 *   @param  Mode run mode (normal (direct to server) or test (through error sim))
	 *   @param  Verbosity verbosity of info (ranging from none to full packet details)
	 *   @return TFTPClientTransfer
	 */
	public TFTPClientTransfer(String threadName, InetAddress serverAddress, String remoteFile, String localFile, TFTPClient cliThread, TFTPCommon.Request transferType, TFTPCommon.Mode runMode, TFTPCommon.Verbosity verMode, int reTimeout, Boolean overwrite)
	{
		super(threadName);

		fileMode = "octet";
		this.serverAddress = serverAddress;
		remoteName = new String(remoteFile);
		localName = new String(localFile);
		requestType = transferType;
		run = runMode;
		verbose = verMode;
		parent = cliThread;
		timeout = reTimeout;
		this.overwrite = overwrite;
		maxTimeout = 10;
		hardTimeout = 60000;

		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 *   Constructs the request packet, which consists of the opcode (01 for read, 02 for write), 0 byte,
	 *   filename and another 0 byte.
	 *
	 *   @param  byte[] filename as byte array
	 *   @return int length of request packet
	 */
	private Boolean sendRequestPacket (byte[] msg) throws SocketTimeoutException
	{
		byte[] data;
		int    len;

		if ( requestType == TFTPCommon.Request.READ ) 
		{
			len = TFTPCommon.constructReqPacket(msg, 1, remoteName, fileMode);
		}
		else 
		{
			len = TFTPCommon.constructReqPacket(msg, 2, remoteName, fileMode);
		}

		data = new byte[TFTPCommon.maxPacketSize];

		receivePacket = new DatagramPacket(data, data.length);

		for (int i = 0; i < maxTimeout; i++)
		{
			if (verbose != TFTPCommon.Verbosity.NONE)
			{
				System.out.println("Client: sending request packet");
			}

			sendPacket = new DatagramPacket(msg, len, serverAddress, sendPort);
			TFTPCommon.printPacketDetails(sendPacket, verbose, true);

			// Send the datagram packet to the server via the send/receive socket.
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

			try 
			{
				TFTPCommon.receivePacketWTimeout(receivePacket, sendReceiveSocket, timeout);
				sendPort = receivePacket.getPort();

				if ( ( requestType == TFTPCommon.Request.WRITE && TFTPCommon.validACKPacket(receivePacket, 0) ) || ( requestType == TFTPCommon.Request.READ && TFTPCommon.validDATAPacket(receivePacket, 1) ) )
				{
					return true;
				}
				else if (TFTPCommon.validERRORPacket(receivePacket))
				{
					System.out.println(consolePrefix + "Received error packet:");
					TFTPCommon.printPacketDetails(receivePacket, TFTPCommon.Verbosity.ALL, false);
					TFTPCommon.parseErrorPacket(receivePacket, consolePrefix);
					return false;
				}
				else
				{
					System.out.println(consolePrefix + "Received invalid packet:");
					TFTPCommon.printPacketDetails(receivePacket, TFTPCommon.Verbosity.ALL, false);
					TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, "Invalid request response received from server", TFTPCommon.ErrorCode.ILLEGAL, consolePrefix, verbose);
					return false;
				}
			} 
			catch (SocketTimeoutException e) 
			{
				System.out.println("Client: Server did not respond to request within " + timeout + " ms, trying again: attempt " + (i + 1) + " of " + maxTimeout);
			}
		}

		throw new SocketTimeoutException("Client: Maximum requests reached, cancelling transfer");
	}

	/**
	 *   Creates RRQ/WRQ, DATA, and ACK packets (using above methods) and sends them to server/receives from server
	 *   This method deals with creating/receiving DatagramPackets
	 *
	 *   @param  none
	 *   @return none
	 */
	private void sendAndReceive() 
	{
		byte[] msg = new byte[TFTPCommon.maxPacketSize];
		int blockNum, len, timeoutCount;
		Boolean sendReceiveStatus = false;

		if (run == TFTPCommon.Mode.NORMAL) 
		{
			sendPort = TFTPCommon.TFTPListenPort;
		}

		else
		{
			sendPort = TFTPCommon.TFTPErrorSimPort;
		}

		if (requestType == TFTPCommon.Request.WRITE)
		{
			try {
				fileOp = new FileOperation(localName, true, 512, overwrite);
			} catch (FileNotFoundException e) {
				System.out.println(consolePrefix + "Local file \"" + localName + "\" does not exist!");
				sendReceiveSocket.close();
				return;
			} catch (FileOperation.FileOperationException e) {
				System.out.println(consolePrefix + e);
				sendReceiveSocket.close();
				return;
			}
		}
		else
		{
			try {
				fileOp = new FileOperation(localName, false, 512, overwrite);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't write to " + localName);
				sendReceiveSocket.close();
				return;
			} catch (FileOperation.FileOperationException e) {
				System.out.println(consolePrefix + e);
				sendReceiveSocket.close();
				return;
			}
		}

		try 
		{
			if (!sendRequestPacket(msg))
			{
				System.out.println(consolePrefix + "Cancelling transfer");
			 	sendReceiveSocket.close();

				if ( requestType == TFTPCommon.Request.READ )
				{
					if (fileOp.delete())
					{
						System.out.println(consolePrefix + "Incomplete file \"" + localName + "\" deleted");
					}
					else
					{
						System.out.println(consolePrefix + "Failed to delete incomplete file \"" + localName + "\"");
					}
				}
				return;
			} 
		}
		catch (SocketTimeoutException e)
		{
			e.printStackTrace();
			return;
		}

		len = receivePacket.getLength();

		TFTPCommon.printPacketDetails(receivePacket, verbose, false);

		System.out.println("Client: Beginning file transfer");
		parent.clientTransferring(true);

		if ( requestType == TFTPCommon.Request.WRITE ) 
		{		
			sendReceiveStatus = TFTPCommon.sendDataWTimeout(sendPacket, receivePacket, sendReceiveSocket, serverAddress, timeout, maxTimeout, sendPort, fileOp, verbose, consolePrefix);

			//Close file now that we are done sending it to server
			if (sendReceiveStatus)
			{
				try {
					fileOp.closeFileRead();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
	    }

	    else if ( requestType == TFTPCommon.Request.READ ) 
		{
			sendReceiveStatus = TFTPCommon.receiveDataWTimeout(sendPacket, receivePacket, sendReceiveSocket, serverAddress, true, hardTimeout, fileOp, verbose, consolePrefix);

			if (sendReceiveStatus)
			{
				try {
					fileOp.finalizeFileWrite();
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		}
		
		if (sendReceiveStatus)
		{
			System.out.println(consolePrefix + "File transfer complete");
		}
		else
		{
			System.out.println(consolePrefix + "Error occurred, transfer incomplete");
			
			if ( requestType == TFTPCommon.Request.READ )
			{
				if (fileOp.delete())
				{
					System.out.println(consolePrefix + "Incomplete file \"" + localName + "\" deleted");
				}
				else
				{
					System.out.println(consolePrefix + "Failed to delete incomplete file \"" + localName + "\"");
				}
			}
		}
		
		// We're finished, so close the socket.
		sendReceiveSocket.close();
		parent.clientTransferring(false);
	}

	public void run()
	{
		this.sendAndReceive();
	}
}