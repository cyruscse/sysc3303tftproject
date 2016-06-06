package grouptwo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import grouptwo.FileOperation;
import grouptwo.TFTPCommon;
import grouptwo.TFTPServer;

/**
* ClientConnectionThread is the TFTP transferring class for the server.
* A new ClientConnectionThread is created for each client that sends a RRQ or WRQ
* Many methods in this class have the same functionality as the client (only "mirrored",
* i.e. WRQ writes to local machine on server, but read from local machine on client)
*
* @author        Kenan El-Gaouny
* @author        Eliab Woldeyes
*/
public class ClientConnectionThread implements Runnable {

	DatagramPacket receivePacket;
	DatagramPacket sendPacket;
	DatagramSocket sendReceiveSocket;

	private TFTPCommon.Request requestType;
	private TFTPCommon.Verbosity verbose;
	private TFTPServer parent;
	private FileOperation fileOp;
	private String localName, mode;
	private int port;
	private InetAddress clientAddress;
	private int threadNumber;
	//hardTimeout - timeout when waiting for DATA
	private int hardTimeout;
	//timeout - timeout when sending DATA
	private int timeout;
	//maxTimeout - number of timeouts to wait before giving up
	private int maxTimeout;
	private Boolean overwrite;
	private final String consolePrefix;

	/**
     *   Constructor for ClientConnectionThread - called by TFTPServer for each incoming client connection (called with information required for new transfer)
     *
     *   @param  DatagramPacket first packet received from client
     *   @param  TFTPServer client listening server
     *   @param  TFTPCommon.Verbosity thread verbosity
     *   @param  int thread number
     *   @param  int timeout value before resending packet
     *   @param  Boolean allow overwriting
     *   @return ClientConnectionThread
     */
	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPCommon.Verbosity verbosity, int threadNumber, int reTimeout, Boolean overwrite) 
	{
		this.threadNumber = threadNumber;
		consolePrefix = ("Server Thread " + (threadNumber ) + ": ");
		receivePacket = receivePckt;
		this.parent = parent;
		port = receivePckt.getPort();
		clientAddress = receivePckt.getAddress();
		verbose = verbosity;
		mode = new String();
		hardTimeout = 60000;
		timeout = reTimeout;
		this.overwrite = overwrite;
		maxTimeout = 10;

		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void run() 
	{
		byte[] data, msg, response;
		int len, j = 0, k = 0;
		Boolean sendReceiveStatus = false;
		TFTPCommon.ContentSubmod requestError = TFTPCommon.ContentSubmod.INVALID;

		if (verbose == TFTPCommon.Verbosity.ALL)
		{
			System.out.println(consolePrefix + "created");
		}
		
		data = receivePacket.getData();
		len = receivePacket.getLength();

		if (data[0] != 0)
		{
			requestType = TFTPCommon.Request.ERROR;
			requestError = TFTPCommon.ContentSubmod.OPCODE;
		}
		else if (data[1] == 1) 
		{
			requestType = TFTPCommon.Request.READ; // could be read
		}
		else if (data[1] == 2) 
		{
			requestType = TFTPCommon.Request.WRITE; // could be write
		}
		else
		{ 
			requestType = TFTPCommon.Request.ERROR;
			requestError = TFTPCommon.ContentSubmod.OPCODE;
		}

		if (requestType != TFTPCommon.Request.ERROR)  // check for filename
		{
			for (j = 2; j < len; j++)
			{
				if (data[j] == 0)
				{
					break;
				}
			}
			
			if (j == len) 
			{
				requestType = TFTPCommon.Request.ERROR; // didn't find 0 byte
			}
			if (j == 2) 
			{
				requestType = TFTPCommon.Request.ERROR; // filename is 0 bytes long
			}

			localName = new String(data, 2, j - 2);
		}

		if (requestType != TFTPCommon.Request.ERROR) // check for mode
		{
			for (k = j + 1; k < len; k++)
			{ 
				if (data[k] == 0) 
				{
					break;
				}
			}
			
			if (k == len) 
			{
				requestType = TFTPCommon.Request.ERROR; // didn't find a 0 byte
			}
			if (k == j + 1) 
			{
				requestType = TFTPCommon.Request.ERROR; // mode is 0 bytes long
			}

			mode = new String(data, j + 1, k - j - 1);

			if ( !mode.equalsIgnoreCase("octet") && !mode.equalsIgnoreCase("netascii") && requestType != TFTPCommon.Request.ERROR ) 
			{
				requestType = TFTPCommon.Request.ERROR; // mode was not passed correctly
				requestError = TFTPCommon.ContentSubmod.FILEMODE;
			}
		}

		if (k != len - 1)
		{
			requestType = TFTPCommon.Request.ERROR; // other stuff at end of packet
		}

		if (requestType == TFTPCommon.Request.READ)
		{
			TFTPCommon.printPacketDetails(receivePacket, consolePrefix, verbose, false, true);

			try {
				fileOp = new FileOperation(localName, true, 512, overwrite); 
			} catch (FileNotFoundException e) {
				String fileNotFoundMessage = new String("File: \"" + localName + "\" does not exist!");
				TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, fileNotFoundMessage, TFTPCommon.ErrorCode.FILENOTFOUND, consolePrefix, verbose);
				
				sendReceiveSocket.close();
				parent.threadDone(Thread.currentThread());
				
				return;
			} catch (FileOperation.FileOperationException e) {
				TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, e.toString(), e.error, consolePrefix, verbose);

				sendReceiveSocket.close();
				parent.threadDone(Thread.currentThread());
				return;
			}

			sendReceiveStatus = TFTPCommon.sendDataWTimeout(sendPacket, receivePacket, sendReceiveSocket, clientAddress, timeout, maxTimeout, port, fileOp, verbose, consolePrefix);
			
			try {
				fileOp.closeFileRead();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		} 

		else if (requestType == TFTPCommon.Request.WRITE)
		{
			TFTPCommon.printPacketDetails(receivePacket, consolePrefix, verbose, false, true);

			try {
				fileOp = new FileOperation(localName, false, 512, overwrite);
			} catch (FileNotFoundException e) {
				System.out.println(consolePrefix + "Couldn't write to " + localName);
				TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, "Couldn't write to local file", TFTPCommon.ErrorCode.FILENOTFOUND, consolePrefix, verbose);

				sendReceiveSocket.close();
				parent.threadDone(Thread.currentThread());
				return;
			} catch (FileOperation.FileOperationException e) {
				TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, e.toString(), e.error, consolePrefix, verbose);

				sendReceiveSocket.close();
				parent.threadDone(Thread.currentThread());
				return;
			}

			TFTPCommon.sendACKPacket(0, 0, sendPacket, receivePacket, sendReceiveSocket, verbose, consolePrefix); //Respond to WRQ

			System.out.println(consolePrefix + "Beginning file transfer");

			sendReceiveStatus = TFTPCommon.receiveDataWTimeout(sendPacket, receivePacket, sendReceiveSocket, clientAddress, false, hardTimeout, fileOp, verbose, consolePrefix);

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
		else 
		{
			String errorString = new String();
			System.out.println(consolePrefix + "Received invalid request:");
			TFTPCommon.printPacketDetails(receivePacket, consolePrefix, TFTPCommon.Verbosity.ALL, false, true);
			
			if (requestError == TFTPCommon.ContentSubmod.OPCODE)
			{
				errorString = "Request packet has invalid opcode: " + data[0] + "" + data[1];
			}
			else if (requestError == TFTPCommon.ContentSubmod.FILEMODE)
			{
				errorString = "Request packet has invalid file mode: " + mode;
			}
			else if (requestError == TFTPCommon.ContentSubmod.INVALID && (k == (len - 1) ) )
			{
				errorString = "Request packet missing file name or file mode";
			}
			else
			{
				errorString = "Request packet format incorrect";
			}

			TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, errorString, TFTPCommon.ErrorCode.ILLEGAL, consolePrefix, verbose);
			
			System.out.println(consolePrefix + "Shutting down");
			sendReceiveSocket.close();
			parent.threadDone(Thread.currentThread());
			return;
		}

		if (sendReceiveStatus)
		{
			System.out.println(consolePrefix + "File transfer complete");
		}
		else
		{
			System.out.println(consolePrefix + "Error occurred, transfer incomplete");

			if ( requestType == TFTPCommon.Request.WRITE )
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

		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone(Thread.currentThread());
	}
}
