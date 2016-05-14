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
	private int timeout = 1000;
	private int maxTimeout = 10; // number of timeouts before quiting
	private final String consolePrefix = ("Server Thread " + (threadNumber + 1) + ": ");

	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPCommon.Verbosity verbosity, int threadNumber) {
		this.threadNumber = threadNumber;
		receivePacket = receivePckt;
		this.parent = parent;
		port = receivePckt.getPort();
		address = receivePckt.getAddress();
		verbose = verbosity;
		mode = new String();

		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public void run() {

		byte[] data, msg, response;
		int blockNum, len, j = 0, k = 0, timeoutCount;

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
				fileOp = new FileOperation(localName, true, 512); 
			} catch (FileNotFoundException e) {
				System.out.println(consolePrefix + "Local file " + localName + " does not exist!");
				e.printStackTrace();
				return;
			} catch (Exception e) {
				System.out.println(consolePrefix + "File is too big!");
				return;
			}

			timeoutCount = 0;
			blockNum = 0;
			msg = new byte[516];

			while (blockNum < fileOp.getNumTFTPBlocks())
			{
				if (timeoutCount == 0)
				{
					msg = new byte[516];
					len = TFTPCommon.constructDataPacket(msg, blockNum, fileOp);
				}

				if (verbose != TFTPCommon.Verbosity.NONE)
				{
					System.out.println(consolePrefix + "Sending DATA " + blockNum + "/" + (fileOp.getNumTFTPBlocks() - 1) + " len " + len);
				}

				sendPacket = new DatagramPacket(msg, len, address, port);
				
				TFTPCommon.printPacketDetails(sendPacket, verbose, false);
	
				TFTPCommon.sendPacket(sendPacket, sendReceiveSocket);

				// Receive the client response for the data packet we just sent
				response = new byte[4];
				receivePacket = new DatagramPacket(response, response.length);

				if (timeoutCount < maxTimeout) 
				{	
					if (verbose != TFTPCommon.Verbosity.NONE)
					{
						System.out.println(consolePrefix + "Waiting for data read acknowledgement");
					}
					
					try {
						TFTPCommon.receivePacketWTimeout(receivePacket, sendReceiveSocket, timeout);
					} catch (SocketTimeoutException e) {
						timeoutCount++;
						System.out.println(consolePrefix + "Receive timed out after " + timeout + " ms");
						System.out.println(consolePrefix + "Resending DATA " + blockNum  + ": Attempt " + timeoutCount);
					}
				}
				else
				{
					System.out.println(consolePrefix + "Maximum timeouts reached for DATA " + blockNum + ". Thread returning.");
					break;
				}

				if (receivePacket.getPort() != -1)
				{ 
					if (TFTPCommon.validACKPacket(response, blockNum)) 
					{
						if (verbose != TFTPCommon.Verbosity.NONE)
						{
							System.out.println(consolePrefix + "ACK valid!");
							if (verbose == TFTPCommon.Verbosity.ALL)
								System.out.println(consolePrefix + "done Block " + blockNum);
						}

						timeoutCount = 0; //Reset timeout count once a successful ACK is received
						blockNum++;
					}
					else if (TFTPCommon.getPacketType(response) == TFTPCommon.PacketType.ACK && TFTPCommon.blockNumToPacket(response) < blockNum) 
					{
						// duplicate ack received
						System.out.println(consolePrefix + "Duplicate ACK received, ignoring");
					} 
					else 
					{
						System.out.println(consolePrefix + "Invalid packet received, ignoring");
						TFTPCommon.printPacketDetails(receivePacket, TFTPCommon.Verbosity.ALL, false);
					}
				}			
			}

			//Close file now that we are done sending it to client

			if ( blockNum == fileOp.getNumTFTPBlocks())
			{
				System.out.println("good read");
				fileOp.closeFileRead();
			}
		} 

		else if (requestType == TFTPCommon.Request.WRITE)
		{
			try {
				fileOp = new FileOperation(localName, false, 512);
			} catch (FileNotFoundException e) {
				System.out.println(consolePrefix + "Couldn't write to " + localName);
				return;
			} catch (Exception e) {
				System.out.println(consolePrefix + "File is too big!");
				return;
			}

			if (verbose != TFTPCommon.Verbosity.NONE )
			{
				System.out.println(consolePrefix + "Sending WRQ ACK0.");
			}

			// Send the initial ACK (block 0) that will establish a connection with the client 
			// for the file transfer.
			data = new byte[]{0,4,0,0};
			
			sendPacket = new DatagramPacket(data, data.length, receivePacket.getAddress(), receivePacket.getPort());

			TFTPCommon.printPacketDetails(sendPacket,verbose,false);

			// Send the datagram packet to the server via the send/receive socket.
			TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);

			Boolean writingFile = true;
			Boolean willExit = false;
			blockNum = 0;

			System.out.println(consolePrefix + "Beginning file transfer");

			while (writingFile)
			{
				msg = new byte[516];
				receivePacket = new DatagramPacket(msg, msg.length);

				if (verbose != TFTPCommon.Verbosity.NONE)
				{
					System.out.println(consolePrefix + "Waiting for next data packet");
				}

				try {
					TFTPCommon.receivePacketWTimeout(receivePacket,sendReceiveSocket, 60000);
				} catch (SocketTimeoutException e1) {
					// packet not received within time
					// dont resend ack just die 
					// time here must be longer than time for write requests
					System.out.println(consolePrefix + "conection must have been lost havent recieved any data packets in 60000ms");
					System.out.println(consolePrefix + "Killing Thread");
					parent.threadDone(Thread.currentThread());
					return; // kill thread
				}

				len = receivePacket.getLength();
				
				TFTPCommon.printPacketDetails(receivePacket,verbose,false);

				//Process data packet, processNextReadPacket will determine
				//if its the last data packet
				//determine if a duplicate or late
				//packet is received here
				
				if ((msg[2] & 0xFF) == ((byte) (blockNum / 256) & 0xFF)&& (msg[3] & 0xFF) == ((byte) (blockNum % 256) & 0xFF)) {
					// we received the correct data block with the right number
					try {
						willExit = TFTPCommon.validDataPacket(msg, len, fileOp, verbose);
					} catch (Exception e) {
						// invalid data
						return;
					}
					if (verbose != TFTPCommon.Verbosity.NONE)
					{
						System.out.println(consolePrefix + "Received TFTP packet " + blockNum);
					}

					//Form ACK packet
					msg = new byte[4];

					TFTPCommon.constructAckPacket(msg, blockNum);

					sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());

					TFTPCommon.printPacketDetails(sendPacket,verbose,false);

					TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);
					} else if ((msg[2] & 0xFF) == ((byte) ((blockNum - 1) / 256) & 0xFF)
						&& (msg[3] & 0xFF) == ((byte) ((blockNum - 1) % 256) & 0xFF)) {
						///// duplicate received don't write but acknowledge
						blockNum--;
						System.out.println(consolePrefix + "duplicate data block data " + blockNum + "received, not writing to file");
						msg = new byte[4]; 
	
						TFTPCommon.constructAckPacket(msg, blockNum);
	
						sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());
	
						TFTPCommon.printPacketDetails(sendPacket,verbose,false);
	
						TFTPCommon.sendPacket(sendPacket,sendReceiveSocket);
					
				} else if (msg[0] == 0 && msg[1] == 3) { //  might not even need this else
					// delayed data received
					// find block num of received date and send corresponding
					int dataPacketNum = getpNum(data);
					// send ack dataPacketNum
					//^^still need to do^^^
					// decrement counter
					blockNum--;
				}
				
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

		System.out.println(consolePrefix + "File transfer complete");	//if for this as well (when maxtimeouts have been reached)

		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone(Thread.currentThread());
		// done the thread will die automatically
	}
	// returns packet number
			public static int getpNum(byte[] data) {
				Byte a = data[2];
				Byte b = data[3];

				return Byte.toUnsignedInt(a) * 256 + Byte.toUnsignedInt(b);
				
			}
}
