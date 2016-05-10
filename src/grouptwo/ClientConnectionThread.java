package grouptwo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import grouptwo.FileOperation;

public class ClientConnectionThread implements Runnable {
	// types of requests we can receive
	public static enum Request {
		READ, WRITE
	};

	private TFTPServer.Request requestType;

	// "some" verbosity prints packet details omitting data contents,
	// "all" verbosity prints everything (including the 512 data bytes)
	public static enum Verbosity {
		NONE, SOME, ALL
	};

	private TFTPServer.Verbosity verbose;

	DatagramPacket receivePacket;
	DatagramPacket sendPacket;
	DatagramSocket sendReceiveSocket;

	private FileOperation fileOp;
	private TFTPServer parent;
	private String localName, mode;
	private int port;
	private InetAddress address;
	private int threadNumber;

	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPServer.Verbosity verbosity, int threadNumber) {
		this.threadNumber = threadNumber;
		this.receivePacket = receivePckt;
		this.parent = parent;
		this.port = receivePckt.getPort();
		this.address = receivePckt.getAddress();
		mode = new String();

		if (verbosity == TFTPServer.Verbosity.NONE)
			this.verbose = TFTPServer.Verbosity.NONE;
		else if (verbosity == TFTPServer.Verbosity.SOME)
			this.verbose = TFTPServer.Verbosity.SOME;
		else if (verbosity == TFTPServer.Verbosity.ALL)
			this.verbose = TFTPServer.Verbosity.ALL;

		try {
			// Construct a new datagram socket and bind it to any port
			// on the local host machine. This socket will be used to
			// send UDP Datagram packets.
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			System.out.println("cant create socket");
			se.printStackTrace();
			return;
		}
	}

	@Override
	public void run() {

		byte[] data, msg, response;
		int len = 0, j = 0, k = 0;

		if (verbose == TFTPServer.Verbosity.ALL)
		{
			System.out.println("Server: new thread created, Thread: " + threadNumber);
		}
		data = new byte[100];
		data = receivePacket.getData();
		len = receivePacket.getLength();

		// If it's a read, send back DATA (03) block 1
		// If it's a write, send back ACK (04) block 0
		// Otherwise, ignore it
		if (data[0]!=0) requestType = TFTPServer.Request.ERROR; // bad
		else if (data[1]==1) requestType = TFTPServer.Request.READ; // could be read
		else if (data[1]==2) requestType = TFTPServer.Request.WRITE; // could be write
		else requestType = TFTPServer.Request.ERROR; // bad

		if (requestType !=TFTPServer.Request.ERROR) { // check for filename
			// search for next all 0 byte
			for(j=2;j<len;j++) {
				if (data[j] == 0) break;
			}
			if (j==len) requestType=TFTPServer.Request.ERROR; // didn't find a 0 byte
			if (j==2) requestType=TFTPServer.Request.ERROR; // filename is 0 bytes long
			// otherwise, extract filename
			localName = new String(data,2,j-2);
			
		}

		if(requestType!=TFTPServer.Request.ERROR) { // check for mode
			// search for next all 0 byte
			for(k=j+1;k<len;k++) { 
				if (data[k] == 0) break;
			}
			if (k==len) requestType=TFTPServer.Request.ERROR; // didn't find a 0 byte
			if (k==j+1) requestType=TFTPServer.Request.ERROR; // mode is 0 bytes long
			mode = new String(data,j,k-j);
			
			if ( mode.equalsIgnoreCase("octet") || mode.equalsIgnoreCase("netascii")) 
			{
				requestType=TFTPServer.Request.ERROR; // mode was not passed correctly
			}
		}

		if(k!=len-1) requestType=TFTPServer.Request.ERROR; // other stuff at end of packet

		// Create a response.
		if (requestType == TFTPServer.Request.READ) // for Read it's 03 then block number
		{

			try {
				fileOp = new FileOperation(localName, true, 512); // change me
			} catch (FileNotFoundException e) {
				System.out.println("Server Thread " + threadNumber +": "+" Local file " + localName + " does not exist!");
				e.printStackTrace();
				return;
			}

			if (verbose == TFTPServer.Verbosity.ALL)
			{
				System.out.println("Server Thread " + threadNumber +": "+ "block in file:" + fileOp.getNumTFTPBlocks());
			}

			for (j = 0; j < fileOp.getNumTFTPBlocks(); j++) {
				msg = new byte[516];

				try {
					len = constructNextReadPacket(msg, j, fileOp);
				} catch (FileNotFoundException e) {
					System.out.println("File not found!");
					return;
				}

				if (verbose != TFTPServer.Verbosity.NONE)
				{
					System.out.println("Server: "+ "Thread " + threadNumber +": "+ "Sending TFTP packet " + (j + 1) + "/" + fileOp.getNumTFTPBlocks());
				}

				if (verbose == TFTPServer.Verbosity.ALL) {
					for (k = 0; k < len; k++) {
						System.out.println("byte " + k + " " + (msg[k] & 0xFF));
					}
				}

				sendPacket = new DatagramPacket(msg, len, address, port);
				if (verbose != TFTPServer.Verbosity.NONE)
				{
					System.out.println("Server Thread "+threadNumber+ ": sending packet.");
					System.out.println("to host: " + sendPacket.getAddress());
					System.out.println("to port: " + sendPacket.getPort());

					System.out.println("Length: " + sendPacket.getLength());
				}
				// Send the datagram packet to the server via sendReceiveSocket
				// socket.
				try {
					sendReceiveSocket.send(sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}

				msg = new byte[4];
				receivePacket = new DatagramPacket(msg, msg.length);

				if (verbose != TFTPServer.Verbosity.NONE)
				{
					System.out.println("Server Thread " + threadNumber +": Waiting for data read acknowledgement");
				}

				try {
					sendReceiveSocket.receive(receivePacket);

				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				// check if received is an ack
				byte[] rcvd = receivePacket.getData();
				System.out.println("ack " + rcvd[0] + " " + rcvd[1] + " " + rcvd[2] + " " + rcvd[3] + " " + (byte) j/256 + " " + (byte) j % 256);

				if (verbose == TFTPServer.Verbosity.ALL && rcvd[0] == 0 && rcvd[1] == 4 && (rcvd[2] & 0xFF) == ((byte)(j / 256) & 0xFF) && (rcvd[3] & 0xFF) ==  ((byte)(j % 256) & 0xFF)) {
					System.out.println("Server Thread " + threadNumber +": "+ "ACK valid!");


				}
				else{
					System.out.println("Server Thread " + threadNumber +": "+ "ACK is invalid!");
					return;
				}

				if (verbose == TFTPServer.Verbosity.ALL)
					System.out.println("Server Thread " + threadNumber +": "+"done Block " + j);
			}

			//Close file now that we are done sending it to client
			fileOp.closeFileRead();


		} else if (requestType == TFTPServer.Request.WRITE) // for Write it's 0400
		{

			try {
				fileOp = new FileOperation(localName, false, 512);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't write to " + localName);
				return;
			}

			if (verbose != TFTPServer.Verbosity.NONE )
			{
				System.out.println("Server Thread " + threadNumber +": Sending WRQ ACK0.");
			}

			// Send the initial ACK (block 0) that will establish a connection with the client 
			// for the file transfer.
			response = new byte[]{0,4,0,0};
			sendPacket = new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());

			if ( verbose != TFTPServer.Verbosity.NONE ) 
			{
				printPacketDetails(sendPacket);
				if ( verbose == TFTPServer.Verbosity.ALL )
				{
					System.out.println("Containing: ");
					for (j = 0; j < sendPacket.getLength(); j++) 
					{
						System.out.println("byte " + j + " " + sendPacket.getData()[j]);
					}
				}
			}

			// Send the datagram packet to the server via the send/receive socket.
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			Boolean writingFile = true;
			Boolean willExit = false;
			int blockNum = 0;

			System.out.println("Server Thread " + threadNumber + ": Beginning file transfer");

			while (writingFile)
			{
				msg = new byte[516];
				receivePacket = new DatagramPacket(msg, msg.length);

				if (verbose != TFTPServer.Verbosity.NONE)
				{
					System.out.println("Server Thread " + threadNumber + ": Waiting for next data packet");
				}

				try {
					sendReceiveSocket.receive(receivePacket);
				} catch(IOException e) {
					e.printStackTrace();
					return;
				}

				len = receivePacket.getLength();

				if ( verbose != TFTPServer.Verbosity.NONE ) 
				{
					printPacketDetails(receivePacket);
					if ( verbose == TFTPServer.Verbosity.ALL )
					{
						System.out.println("Containing: ");
						for (j = 0; j < receivePacket.getLength(); j++) 
						{
							System.out.println("byte " + j + " " + msg[j]);
						}
					}
				}

				//Process data packet, processNextReadPacket will determine
				//if its the last data packet
				try {
					willExit = processNextWritePacket(msg, len, fileOp);
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}

				if (verbose != TFTPServer.Verbosity.NONE)
				{
					System.out.println("Server Thread " + threadNumber + ": Received TFTP packet " + blockNum);
				}

				//Form ACK packet
				msg = new byte[4];

				constructAckPacketData(msg, blockNum);

				sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());

				if ( verbose != TFTPServer.Verbosity.NONE ) 
				{
					System.out.println("Server Thread " + threadNumber + ": Sending ACK packet " + blockNum);
					printPacketDetails(sendPacket);
					if ( verbose == TFTPServer.Verbosity.ALL )
					{
						System.out.println("Containing: ");
						for (j = 0; j < sendPacket.getLength(); j++) 
						{
							System.out.println("byte " + j + " " + (msg[j] & 0xFF));
						}
					}
				}

				try {
					sendReceiveSocket.send(sendPacket);
				} catch(IOException e) {
					e.printStackTrace();
					return;
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

		System.out.println("Server Thread " + threadNumber + ": File transfer complete");


		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone(Thread.currentThread());
		// done the thread will die automatically
	}

	// Constructs data packet for TFTP writes
	// Consists of DATA opcode, block number and actual data
	// Uses the FileOperation class to divide the file into data packets
	private int constructNextReadPacket(byte[] msg, int blockNumber, FileOperation writeFile) throws FileNotFoundException {
		msg[0] = 0;
		msg[1] = 3;
		msg[2] = (byte) (blockNumber / 256);
		msg[3] = (byte) (blockNumber % 256);

		return writeFile.readNextDataPacket(msg, 4);
	}

	/**
	 *   Prints basic packet details
	 *
	 *   @param  DatagramPacket to print details of
	 *   @return none
	 */
	private void printPacketDetails(DatagramPacket packet) 
	{
		System.out.println("Host: " + packet.getAddress());
		System.out.println("Host port: " + packet.getPort());
		System.out.println("Length: " + packet.getLength());
	}

	/**
	 *   Processes DATA write packet
	 *
	 *   @param  byte[] data sent from client to server
	 *   @param  int length of data
	 *   @param  FileOperation current file we are writing to
	 *   @return Boolean indicating if this was the final DATA packet
	 */
	private Boolean processNextWritePacket(byte[] msg, int len, FileOperation writeFile) throws Exception
	{
		if (msg[0] != 0 || msg[1] != 3)
		{
			throw new Exception("Invalid data packet");
		}

		writeFile.writeNextDataPacket(msg, 4, len - 4);

		if (len < 516)
		{
			if (verbose != TFTPServer.Verbosity.NONE)
			{
				System.out.println("Server Thread"+ threadNumber+ ": Received final write packet from client");
				writeFile.finalizeFileWrite();
			}

			return true;
		}
		else
		{
			return false;
		}

	}

	/**
	 *   Constructs ACK packet, converts int blockNumber to byte representation
	 *
	 *   @param  byte[] array to store packet data in
	 *   @param  int current block number
	 *   @return none
	 */
	private void constructAckPacketData (byte[] msg, int blockNumber)
	{
		msg[0] = 0;
		msg[1] = 4;
		msg[2] = (byte) (blockNumber / 256);
		msg[3] = (byte) (blockNumber % 256);        
	}
}
