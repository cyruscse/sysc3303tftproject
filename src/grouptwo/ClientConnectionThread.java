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
	private InetAddress address;
	private int threadNumber;
	//hardTimeout - timeout when waiting for DATA
	private int hardTimeout;
	//timeout - timeout when sending DATA
	private int timeout;
	//maxTimeout - number of timeouts to wait before giving up
	private int maxTimeout;
	private Boolean overwrite;
	private final String consolePrefix = ("Server Thread " + (threadNumber + 1) + ": ");

	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPCommon.Verbosity verbosity, int threadNumber, int reTimeout, Boolean overwrite) 
	{
		this.threadNumber = threadNumber;
		receivePacket = receivePckt;
		this.parent = parent;
		port = receivePckt.getPort();
		address = receivePckt.getAddress();
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

		if (verbose == TFTPCommon.Verbosity.ALL)
		{
			System.out.println(consolePrefix + "created");
		}
		
		data = receivePacket.getData();
		len = receivePacket.getLength();

		// If it's a read, send back DATA (03) block 1
		// If it's a write, send back ACK (04) block 0
		// Otherwise, ignore it
		if (data[0]!=0) requestType = TFTPCommon.Request.ERROR; // bad
		else if (data[1]==1) requestType = TFTPCommon.Request.READ; // could be read
		else if (data[1]==2) requestType = TFTPCommon.Request.WRITE; // could be write
		else requestType = TFTPCommon.Request.ERROR; // bad

		if (requestType != TFTPCommon.Request.ERROR) { // check for filename
			// search for next all 0 byte
			for(j=2;j<len;j++) {
				if (data[j] == 0) break;
			}
			if (j==len) requestType = TFTPCommon.Request.ERROR; // didn't find a 0 byte
			if (j==2) requestType = TFTPCommon.Request.ERROR; // filename is 0 bytes long
			// otherwise, extract filename
			localName = new String(data,2,j-2);
			
		}

		if(requestType!= TFTPCommon.Request.ERROR) { // check for mode
			// search for next all 0 byte
			for(k=j+1;k<len;k++) { 
				if (data[k] == 0) break;
			}
			if (k==len) requestType = TFTPCommon.Request.ERROR; // didn't find a 0 byte
			if (k==j+1) requestType = TFTPCommon.Request.ERROR; // mode is 0 bytes long

			mode = new String(data,j+1,k-j-1);

			if ( !mode.equalsIgnoreCase("octet") && !mode.equalsIgnoreCase("netascii")) 
			{
				requestType = TFTPCommon.Request.ERROR; // mode was not passed correctly
			}
		}

		if(k!=len-1) requestType = TFTPCommon.Request.ERROR; // other stuff at end of packet

		// If the request from the CLIENT is a read request, then we have to read blocks from 
		// the local file on the server, create DATA packets, send them to the client, and
		// then make sure we receive an ACK packet for the block we just sent.
		if (requestType == TFTPCommon.Request.READ)
		{
			try {
				fileOp = new FileOperation(localName, true, 512, overwrite); 
			} catch (FileNotFoundException e) {
				String fileNotFoundMessage = new String(consolePrefix + "Local file " + localName + " does not exist!");
				TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, fileNotFoundMessage, TFTPCommon.ErrorCode.FILENOTFOUND, consolePrefix, verbose);
				
				sendReceiveSocket.close();
				parent.threadDone(Thread.currentThread());
				
				return;
			} catch (FileOperation.FileOperationException e) {
				System.out.println(consolePrefix + e);
				TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, e.toString(), e.error, consolePrefix, verbose);

				sendReceiveSocket.close();
				parent.threadDone(Thread.currentThread());
				return;
			}

			sendReceiveStatus = TFTPCommon.sendDataWTimeout(sendPacket, receivePacket, sendReceiveSocket, address, timeout, maxTimeout, port, fileOp, verbose, consolePrefix);

			//Close file now that we are done sending it to client
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

		else if (requestType == TFTPCommon.Request.WRITE)
		{
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

			sendReceiveStatus = TFTPCommon.receiveDataWTimeout(sendPacket, receivePacket, sendReceiveSocket, false, hardTimeout, fileOp, verbose, consolePrefix);

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
			System.out.println(consolePrefix + "Received invalid request, sending ERROR");
			TFTPCommon.sendErrorPacket(receivePacket, sendReceiveSocket, "Bad request packet format", TFTPCommon.ErrorCode.ILLEGAL, consolePrefix, verbose);
			System.out.println(consolePrefix + "Shutting down");
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

			if ( !fileOp.delete() && requestType == TFTPCommon.Request.WRITE )
			{
				System.out.println("Failed to delete incomplete file");
			}
		}

		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone(Thread.currentThread());
	}
}
