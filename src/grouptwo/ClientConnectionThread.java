package grouptwo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
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

	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPCommon.Verbosity verbosity, int threadNumber) {
		this.threadNumber = threadNumber;
		this.receivePacket = receivePckt;
		this.parent = parent;
		this.port = receivePckt.getPort();
		this.address = receivePckt.getAddress();
		this.verbose = verbosity;
		mode = new String();

		TFTPCommon.createSocket(sendReceiveSocket,0);
	}

	@Override
	public void run() {

		byte[] data, msg, response;
		int blockNum, len, j = 0, k = 0;

		if (verbose == TFTPCommon.Verbosity.ALL)
		{
			System.out.println("Server: new thread created, Thread: " + threadNumber);
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
			mode = new String(data,j,k-j);
			
			if ( mode.equalsIgnoreCase("octet") || mode.equalsIgnoreCase("netascii")) 
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
				fileOp = new FileOperation(localName, true, 512); // change me
			} catch (FileNotFoundException e) {
				System.out.println("Server Thread " + threadNumber +": "+" Local file " + localName + " does not exist!");
				e.printStackTrace();
				return;
			} catch (Exception e) {
				System.out.println("File is too big!");
				return;
			}

			for (blockNum = 0; blockNum < fileOp.getNumTFTPBlocks(); blockNum++) {
				msg = new byte[516];

				try {
					len = TFTPCommon.constructDataPacket(msg, blockNum, fileOp);
				} catch (FileNotFoundException e) {
					System.out.println("File not found!");
					return;
				}

				if (verbose != TFTPCommon.Verbosity.NONE)
				{
					System.out.println("Server: "+ "Thread " + threadNumber +": "+ "Sending TFTP packet " + (blockNum + 1) + "/" + fileOp.getNumTFTPBlocks());
				}

				TFTPCommon.createPacket(sendPacket,msg,len,address,port);
				
				TFTPCommon.printPacketDetails(sendPacket,verbose,false);
				
				TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);

				// Receive the client response for the data packet we just sent
				msg = new byte[4];
				receivePacket = new DatagramPacket(msg, msg.length);

				if (verbose != TFTPCommon.Verbosity.NONE)
				{
					System.out.println("Server Thread " + threadNumber +": Waiting for data read acknowledgement");
				}

				TFTPCommon.receivePacket(receivePacket,sendReceiveSocket);

				// check if received is an ack
				if (TFTPCommon.validACKPacket(receivePacket,blockNum)) 
				{
					if (verbose != TFTPCommon.Verbosity.NONE)
					{
						System.out.println("Server Thread " + threadNumber +": "+ "ACK valid!");
					}
				}
				else
				{
					System.out.println("Server Thread " + threadNumber +": "+ "ACK is invalid!");
					return;
				}

				if (verbose == TFTPCommon.Verbosity.ALL)
					System.out.println("Server Thread " + threadNumber +": "+"done Block " + j);
			}

			//Close file now that we are done sending it to client
			fileOp.closeFileRead();


		} else if (requestType == TFTPCommon.Request.WRITE) // for Write it's 0400
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

			if (verbose != TFTPCommon.Verbosity.NONE )
			{
				System.out.println("Server Thread " + threadNumber +": Sending WRQ ACK0.");
			}

			// Send the initial ACK (block 0) that will establish a connection with the client 
			// for the file transfer.
			data = new byte[]{0,4,0,0};
			
			TFTPCommon.createPacket(sendPacket,data,data.length,receivePacket.getAddress(),receivePacket.getPort());

			TFTPCommon.printPacketDetails(sendPacket,verbose,false);

			// Send the datagram packet to the server via the send/receive socket.
			TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);

			Boolean writingFile = true;
			Boolean willExit = false;
			blockNum = 0;

			System.out.println("Server Thread " + threadNumber + ": Beginning file transfer");

			while (writingFile)
			{
				msg = new byte[516];
				receivePacket = new DatagramPacket(msg, msg.length);

				if (verbose != TFTPCommon.Verbosity.NONE)
				{
					System.out.println("Server Thread " + threadNumber + ": Waiting for next data packet");
				}

				TFTPCommon.receivePacket(receivePacket,sendReceiveSocket);

				len = receivePacket.getLength();
				
				TFTPCommon.printPacketDetails(receivePacket,verbose,false);

				//Process data packet, processNextReadPacket will determine
				//if its the last data packet
				try {
					willExit = TFTPCommon.validDataPacket(msg, len, fileOp, verbose);
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}

				if (verbose != TFTPCommon.Verbosity.NONE)
				{
					System.out.println("Server Thread " + threadNumber + ": Received TFTP packet " + blockNum);
				}

				//Form ACK packet
				msg = new byte[4];

				TFTPCommon.constructAckPacket(msg, blockNum);

				sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());

				TFTPCommon.printPacketDetails(sendPacket,verbose,false);

				TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);

				//Can't exit right after we receive the last data packet,
				//we have to acknowledge the data first
				if (willExit)
				{
					writingFile = false;
				}

				blockNum++;
			}

		} else { // it was invalid, just quit
			// throw new Exception("Not yet implemented");
		}

		System.out.println("Server Thread " + threadNumber + ": File transfer complete");

		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone(Thread.currentThread());
		// done the thread will die automatically
	}
}
