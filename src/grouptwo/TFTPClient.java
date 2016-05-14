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
	private String localFile, remoteFile, strRequestType, strVerbosity, strMode;
	private Boolean cliRunning, clientReady, clientTransferring;
	private Thread tftpTransfer;
	private TFTPCommon.Request requestType;
	private TFTPCommon.Verbosity verbosity;
	private TFTPCommon.Mode mode;
	private boolean TESTING = true;

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
		verbosity = TFTPCommon.Verbosity.NONE;
		mode = TFTPCommon.Mode.TEST;
		requestType = TFTPCommon.Request.READ;

		localFile = new String();
		remoteFile = new String();
	}

	public void clientTransferring(Boolean transferring)
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

		if (TESTING == true){
			Testvalues();
		}

		while ( cliRunning ) 
		{
			if ( !remoteFile.isEmpty() && !localFile.isEmpty() && !clientTransferring ) 
			{
				clientReady = true;
			}
			else
			{
				clientReady = false;
			}

			System.out.println("TFTP Client");
			System.out.println("-----------");
			
			if (requestType == TFTPCommon.Request.WRITE)
			{
				System.out.println("1: File to write to on server (current: " + remoteFile + ")");
				System.out.println("2. File to read from on client (current: " + localFile + ")");
			}
			else
			{
				System.out.println("1: File to read from on server (current: " + remoteFile + ")");
				System.out.println("2. File to write to on client (current: " + localFile + ")");
			}
			
			System.out.println("3: Read Request or Write Request (current: " + TFTPCommon.requestToString(requestType) + ")");
			
			if ( clientReady == true )
			{
				System.out.println("4: Start transfer");
			
			}
			System.out.println("m: Set mode (current: " + TFTPCommon.modeToString(mode) + ")");
			System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
			System.out.println("q: Quit (once transfers are complete)");

			scIn = sc.nextLine();

			if ( scIn.equalsIgnoreCase("1") ) 
			{
				System.out.print("Enter filename: ");
				remoteFile = sc.nextLine();
			}

			else if ( scIn.equalsIgnoreCase("2") )
			{
				System.out.print("Enter filename: ");
				localFile = sc.nextLine();
			}

			else if ( scIn.equalsIgnoreCase("3") ) 
			{
				System.out.print("Enter request type (read or write): ");
				strRequestType = sc.nextLine();

				if ( strRequestType.equalsIgnoreCase("write") ) 
				{
					requestType = TFTPCommon.Request.WRITE;
				}
				else if ( strRequestType.equalsIgnoreCase("read") ) 
				{
					requestType = TFTPCommon.Request.READ;
				}
				else 
				{
					System.out.println("Invalid request type");
				}
			}

			else if ( scIn.equalsIgnoreCase("4") && clientReady == true ) 
			{
				tftpTransfer = new TFTPClientTransfer("clientTransfer", remoteFile, localFile, this, requestType, mode, verbosity);
				tftpTransfer.start();
				clientTransferring = true;
			}

			else if ( scIn.equalsIgnoreCase("m") ) {
				System.out.println("Enter mode (inthost, normal): ");
				strMode = sc.nextLine();

				if ( strMode.equalsIgnoreCase("inthost") ) 
				{
					mode = TFTPCommon.Mode.TEST;
				}
				else if ( strMode.equalsIgnoreCase("normal") ) 
				{
					mode = TFTPCommon.Mode.NORMAL;
				}
				else 
				{
					System.out.println("Invalid mode");
				}
			}

			else if ( scIn.equalsIgnoreCase("v") ) 
			{
				System.out.println("Enter verbosity (none, some, all): ");
				strVerbosity = sc.nextLine();

				if ( strVerbosity.equalsIgnoreCase("none") ) 
				{
					verbosity = TFTPCommon.Verbosity.NONE;
				}
				else if ( strVerbosity.equalsIgnoreCase("some") ) 
				{
					verbosity = TFTPCommon.Verbosity.SOME;
				}
				else if ( strVerbosity.equalsIgnoreCase("all") )
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

			else if ( scIn.equalsIgnoreCase("") == false ) 
			{
				System.out.println("Invalid option");
			}
		}
	}

	/**
	 *   Sets up test values for CLI to save time
	 *
	 *   @param  none
	 *   @return none
	 */
	public void Testvalues() {
		localFile = "/var/log/daily.out";
		remoteFile = "/Users/cyrus/Documents/test.txt";
		requestType = TFTPCommon.Request.READ;
		mode = TFTPCommon.Mode.TEST;
		verbosity = TFTPCommon.Verbosity.ALL;
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
	//    // we can run in normal (send directly to server) or test
	//    // (send to simulator) mode
	//    public static enum Mode { NORMAL, TEST };
	//    public static enum Request { READ, WRITE };
	//    //"some" verbosity prints packet details omitting data contents,
	//    //"all" verbosity prints everything (including the 512 data bytes)
	//    public static enum Verbosity { NONE, SOME, ALL };    

	private InetAddress localAddress;
	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket sendReceiveSocket;
	private FileOperation  fileOp;
	private TFTPCommon.Mode run;
	private TFTPCommon.Request requestType;
	private TFTPCommon.Verbosity verbose;
	private String remoteName, localName, fileMode;
	private TFTPClient parent;
	private int writeTimeout = 100;
	private int maxWriteTimeouts = 6000;

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
	public TFTPClientTransfer(String threadName, String remoteFile, String localFile, TFTPClient cliThread, TFTPCommon.Request transferType, TFTPCommon.Mode runMode, TFTPCommon.Verbosity verMode)
	{
		super(threadName);

		fileMode = "octet";
		remoteName = new String(remoteFile);
		localName = new String(localFile);
		requestType = transferType;
		run = runMode;
		verbose = verMode;
		parent = cliThread;

		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}

		try {
			localAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 *   Constructs the request packet, which consists of the opcode (01 for read, 02 for write), 0 byte,
	 *   filename and another 0 byte.
	 *
	 *   @param  byte[] filename as byte array
	 *   @return int length of request packet
	 */
	private int constructReqPacketData(byte[] msg) 
	{
		byte[] fn, // filename as an array of bytes
		md; // mode as an array of bytes
		int    len;

		msg[0] = 0;

		if ( requestType == TFTPCommon.Request.READ ) 
		{
			msg[1] = 1;
		}
		else 
		{
			msg[1] = 2;
		}

		fn = remoteName.getBytes();

		System.arraycopy(fn, 0, msg, 2, fn.length);

		msg[fn.length+2] = 0;

		md = fileMode.getBytes();

		System.arraycopy(md, 0, msg, fn.length+3, md.length);

		len = fn.length+md.length+4; // (filename + mode + opcode (2) + 0s (2))

		msg[len-1] = 0;

		return len;
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
		byte[] msg = new byte[100];
		int blockNum, len, sendPort, timeoutCount;
		boolean packetReceivedBeforeTimeout;

		if (run == TFTPCommon.Mode.NORMAL) 
		{
			sendPort = 69;
		}

		else
		{
			sendPort = 23;
		}

		if (requestType == TFTPCommon.Request.WRITE)
		{
			try {
				fileOp = new FileOperation(localName, true, 512);
			} catch (FileNotFoundException e) {
				System.out.println("Local file " + localName + " does not exist!");
				return;
			} catch (Exception e) {
				System.out.println("File is too big!");
				return;
			}
		}

		else
		{
			try {
				fileOp = new FileOperation(localName, false, 512);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't write to " + localName);
				return;
			} catch (Exception e) {
				System.out.println("File is too big!");
				return;
			}
		}

		if (verbose != TFTPCommon.Verbosity.NONE)
		{
			System.out.println("Client: sending request packet");
		}

		len = constructReqPacketData(msg);

		try {
			sendPacket = new DatagramPacket(msg, len, InetAddress.getLocalHost(), sendPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return;
		}

		TFTPCommon.printPacketDetails(sendPacket,verbose,true);

		// Send the datagram packet to the server via the send/receive socket.
		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		System.out.println("Client: Beginning file transfer");
		parent.clientTransferring(true);

		if ( requestType == TFTPCommon.Request.WRITE ) 
		{
			msg = new byte[4];
			receivePacket = new DatagramPacket(msg, msg.length);

			if (verbose != TFTPCommon.Verbosity.NONE)
			{
				System.out.println("Client: Waiting for request acknowledgement");
			}

			try {
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				parent.clientTransferring(false);
				return;
			}

			TFTPCommon.printPacketDetails(receivePacket,verbose,false);

			//After receiving from server, communicate over server(/host)-supplied port to allow
			//other clients to use the request port
			sendPort = receivePacket.getPort();

			for (blockNum = 0; blockNum < fileOp.getNumTFTPBlocks(); blockNum++ ) 
			{
				msg = new byte[516];

				len = TFTPCommon.constructDataPacket(msg, blockNum, fileOp);

				if (verbose != TFTPCommon.Verbosity.NONE) 
				{
					System.out.println("Client: Sending TFTP packet " + blockNum + "/" + (fileOp.getNumTFTPBlocks() - 1));
				}

				sendPacket = new DatagramPacket(msg, len, localAddress, sendPort);

				TFTPCommon.printPacketDetails(sendPacket, verbose, false);

				TFTPCommon.sendPacket(sendPacket, sendReceiveSocket);

				msg = new byte[4];
				receivePacket = new DatagramPacket(msg, msg.length);
				
				timeoutCount = 0;
				packetReceivedBeforeTimeout = false;
				
				while (timeoutCount < maxWriteTimeouts && !packetReceivedBeforeTimeout) 
				{
					if (verbose != TFTPCommon.Verbosity.NONE)
					{
						System.out.println("Client: Waiting for data write acknowledgement");
					}
					
					try {
						TFTPCommon.receivePacketWTimeout(receivePacket, sendReceiveSocket, writeTimeout);
						// received packet
						packetReceivedBeforeTimeout = true;
					} catch (SocketTimeoutException e) {
						System.out.println("TIMED OUT after " + writeTimeout + "seconds");
						System.out.println("sending data again for the " + timeoutCount + 1 + "th time");
						TFTPCommon.sendPacket(sendPacket, sendReceiveSocket);// send data again
					}
					
					}//end while
					
					// check if received is an ack
					//handle duplicate acks

					byte[] rcvd = receivePacket.getData();
					if (TFTPCommon.validACKPacket(receivePacket, blockNum)) 
					{
						if (verbose != TFTPCommon.Verbosity.NONE)
						{
							System.out.println("Client: "+ "ACK valid!");
							if (verbose == TFTPCommon.Verbosity.ALL)
								System.out.println("Client: "+"done Block ");
						}
					}

					else if (rcvd[0] == 0 && rcvd[1] == 4) 
					{
						// duplicate ack received
						System.out.println("Client: " + "ACK is duplicated");
						System.out.println("Client: " + "Packet ignored");
						
					} 

					else 
					{
						System.out.println("Client: " + "ACK is invalid!");
						
					}
				
				}

			//Close file now that we are done sending it to client
			fileOp.closeFileRead();
			
		
	    }

	    else if ( requestType == TFTPCommon.Request.READ ) 
		{
			Boolean readingFile = true;
			Boolean willExit = false;
			blockNum = 0;

			while ( readingFile )
			{
				msg = new byte[516];

				receivePacket = new DatagramPacket(msg, msg.length);

				if (verbose != TFTPCommon.Verbosity.NONE )
				{
					System.out.println("Client: Waiting for next data packet");
				}

				try {
					TFTPCommon.receivePacketWTimeout(receivePacket,sendReceiveSocket, 60000);
				} catch (SocketTimeoutException e1) {
					// packet not received within time
					// dont resend ack just die 
					// time here must be longer than time for write requests
					System.out.println("conection must have been lost havent recieved any data packets in 60000ms");
					parent.clientTransferring(false);
					return; 
				}

				len = receivePacket.getLength();
	

				TFTPCommon.printPacketDetails(receivePacket,verbose,false);

				//Process data packet, processNextReadPacket will determine
				//if its the last data packet
				
				if ((msg[2] & 0xFF) == ((byte) (blockNum / 256) & 0xFF)&& (msg[3] & 0xFF) == ((byte) (blockNum % 256) & 0xFF)) 
				{
					// we received the correct data block with the right number
					try {
						willExit = TFTPCommon.validDataPacket(msg, len, fileOp, verbose);
					} catch (Exception e) {
						// invalid data
						parent.clientTransferring(false);
						return;
					}
					
					if (verbose != TFTPCommon.Verbosity.NONE)
					{
						System.out.println("Client" + ": Received TFTP packet " + blockNum);
					}

					//Form ACK packet
					msg = new byte[4];

					TFTPCommon.constructAckPacket(msg, blockNum);

					sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());

					TFTPCommon.printPacketDetails(sendPacket,verbose,false);

					TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);
				} 

				else if ((msg[2] & 0xFF) == ((byte) ((blockNum - 1) / 256) & 0xFF) && (msg[3] & 0xFF) == ((byte) ((blockNum - 1) % 256) & 0xFF)) 
				{
						///// duplicate received don't write but acknowledge
						blockNum--;
						System.out.println("duplicate data block data " + blockNum + "received, not writing to file");
						msg = new byte[4]; 
	
						TFTPCommon.constructAckPacket(msg, blockNum);
	
						sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());
	
						TFTPCommon.printPacketDetails(sendPacket,verbose,false);
	
						TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);
					
				} 

				else if (msg[0] == 0 && msg[1] == 3) 
				{ //  might not even need this else
					// delayed data received
					// find block num of received date and send corresponding
					//int dataPacketNum = getpNum(data);
					// send ack dataPacketNum
					//^^still need to do^^^
					// decrement counter
					blockNum--;
				}

				//Can't exit right after we receive the last data packet,
				//we have to acknowledge the data first
				if (willExit)
				{
					readingFile = false;
				}

				blockNum++;
			}
		}

		System.out.println("Client: File transfer complete");
		// We're finished, so close the socket.
		sendReceiveSocket.close();

		parent.clientTransferring(false);
	}
	
	public static int getpNum(byte[] data) {
		Byte a = data[2];
		Byte b = data[3];

		return Byte.toUnsignedInt(a) * 256 + Byte.toUnsignedInt(b);
		
	}

	public void run()
	{
		this.sendAndReceive();
	}
}