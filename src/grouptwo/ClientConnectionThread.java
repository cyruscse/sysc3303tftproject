package grouptwo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import grouptwo.FileOperation;
//import src.grouptwo.TFTPClientTransfer.Verbosity;

public class ClientConnectionThread implements Runnable {
	// types of requests we can receive
	public static enum Request {
		READ, WRITE
	};

	private Request requestType;

	// "some" verbosity prints packet details omitting data contents,
	// "all" verbosity prints everything (including the 512 data bytes)
	public static enum Verbosity {
		NONE, SOME, ALL
	};

	private Verbosity verbose;

	DatagramPacket receivePacket;
	DatagramPacket sendPacket;
	DatagramSocket sendReceiveSocket;

	private FileOperation fileOp;
	private TFTPServer parent;
	private String localName;
	private int port;
	private InetAddress address;


	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPServer.Request requestResponse,
			TFTPServer.Verbosity verbosity, String filename) {

		this.receivePacket = receivePckt;
		this.parent = parent;
		this.port = receivePckt.getPort();
		this.address = receivePckt.getAddress();

		if (requestResponse == TFTPServer.Request.READ)
			this.requestType = Request.READ;
		else if (requestResponse == TFTPServer.Request.WRITE)
			this.requestType = Request.WRITE;

		if (verbosity == TFTPServer.Verbosity.NONE)
			this.verbose = Verbosity.NONE;
		else if (verbosity == TFTPServer.Verbosity.SOME)
			this.verbose = Verbosity.SOME;
		else if (verbosity == TFTPServer.Verbosity.ALL)
			this.verbose = Verbosity.ALL;

		this.localName = filename;

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
		int len, j = 0, k = 0;

		System.out.println("Server: new thread created");

		// Create a response.
		if (requestType == Request.READ) // for Read it's 03 then block number
		{

			try {
				fileOp = new FileOperation(localName, true, 512); // change me
			} catch (FileNotFoundException e) {
				System.out.println("Local file " + localName + " does not exist!");
				e.printStackTrace();
				return;
			}

			System.out.println("block in file:" + fileOp.getNumTFTPBlocks());
			for (j = 0; j < fileOp.getNumTFTPBlocks(); j++) {
				msg = new byte[516];

				try {
					len = constructNextReadPacket(msg, j, fileOp);
				} catch (FileNotFoundException e) {
					System.out.println("File not found!");
					return;
				}

				System.out.println("Server: Sending TFTP packet " + (j + 1) + "/" + fileOp.getNumTFTPBlocks());

				//if (verbosity == Verbosity.ALL) {
				for (k = 0; k < len; k++) {
					System.out.println("byte " + k + " " + (msg[k] & 0xFF));
				}
				//}

				sendPacket = new DatagramPacket(msg, len, address, port);
				System.out.println("Server: sending packet.");
				System.out.println("to host: " + sendPacket.getAddress());
				System.out.println("to port: " + sendPacket.getPort());

				System.out.println("Length: " + sendPacket.getLength());
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

				System.out.println("Server: Waiting for data read acknowledgement");

				try {
					sendReceiveSocket.receive(receivePacket);

				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				// check if received is an ack
				byte[] rcvd = receivePacket.getData();
				System.out.println("ack " + rcvd[0] + " " + rcvd[1] + " " + rcvd[2] + " " + rcvd[3] + " " + (byte) j/256 + " " + (byte) j % 256);

				if (rcvd[0] == 0 && rcvd[1] == 4 && (rcvd[2] & 0xFF) == ((byte)(j / 256) & 0xFF) && (rcvd[3] & 0xFF) ==  ((byte)(j % 256) & 0xFF)) {
					System.out.println("ACK valid!");


				}
				else{
					System.out.println("ACK is invalid!");
					return;
				}

				System.out.println("done Block" + j);
			}

			//Close file now that we are done sending it to client
			fileOp.closeFileRead();
			/*
			 * TODO Here is where we set up the file stream OUT of the file on
			 * the server to be read read by the client: 1) Have to setup a loop
			 * through all blocks of the file 2) For each block in the file we
			 * have to (in order) : -set up the message (with 512 bytes being a
			 * data block from the file) -create the datagram -send the datagram
			 * -receive the response -verify the response is an ACK -loop again
			 * (now on the next block)
			 * 
			 * Look at Cyrus' Client code for an idea on how this is done the
			 * other way.
			 */

		} else if (requestType == Request.WRITE) // for Write it's 0400
		{

			try {
				fileOp = new FileOperation(localName, false, 512);
			} catch (FileNotFoundException e) {
				System.out.println("Couldn't write to " + localName);
				return;
			}

			if (verbose != Verbosity.NONE )
			{
				System.out.println("Server: Sending WRQ response.");
			}

			// Send the initial ACK (block 0) that will establish a connection with the client 
			// for the file transfer.
			response = new byte[]{0,4,0,0};
			sendPacket = new DatagramPacket(response, response.length, receivePacket.getAddress(), receivePacket.getPort());

			if ( verbose != Verbosity.NONE ) 
			{
				printPacketDetails(sendPacket);
				if ( verbose == Verbosity.ALL )
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

			while (writingFile)
			{
				msg = new byte[516];
				receivePacket = new DatagramPacket(msg, msg.length);

				System.out.println("Client: Waiting for next data packet");

				try {
					sendReceiveSocket.receive(receivePacket);
				} catch(IOException e) {
					e.printStackTrace();
					return;
				}

				len = receivePacket.getLength();

				if ( verbose != Verbosity.NONE ) 
				{
					printPacketDetails(receivePacket);
					if ( verbose == Verbosity.ALL )
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

				//Form ACK packet
				msg = new byte[4];

				constructAckPacketData(msg, blockNum);

				sendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), receivePacket.getPort());

				if ( verbose != Verbosity.NONE ) 
				{
					System.out.println("Client: Sending ACK packet " + k);
					printPacketDetails(sendPacket);
					if ( verbose == Verbosity.ALL )
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


			/*
			 * TODO Here is where we set up the file stream INTO the file on the
			 * server which is a write by the client. Generally the same as
			 * above to-do comment block but with different roles.
			 */

		} else { // it was invalid, just quit
			// throw new Exception("Not yet implemented");
		}

		/*
		 * sendPacket = new DatagramPacket(response, response.length,
		 * receivePacket.getAddress(), receivePacket.getPort());
		 * 
		 * System.out.println("Server: Sending packet:"); System.out.println(
		 * "To host: " + sendPacket.getAddress()); System.out.println(
		 * "Destination host port: " + sendPacket.getPort()); len =
		 * sendPacket.getLength(); System.out.println("Length: " + len);
		 * System.out.println("Containing: "); for (j=0;j<len;j++) {
		 * System.out.println("byte " + j + " " + response[j]); }
		 * 
		 * // Send the datagram packet to the client via a new socket.
		 * 
		 * try { // Construct a new datagram socket and bind it to any port //
		 * on the local host machine. This socket will be used to // send UDP
		 * Datagram packets. sendReceiveSocket = new DatagramSocket(); } catch
		 * (SocketException se) { se.printStackTrace(); System.exit(1); }
		 * 
		 * try { sendReceiveSocket.send(sendPacket); } catch (IOException e) {
		 * e.printStackTrace(); System.exit(1); }
		 * 
		 * System.out.println("Server: packet sent using port " +
		 * sendReceiveSocket.getLocalPort()); System.out.println();
		 */

		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone();
		// done the thread will die automatically
	}

	// Constructs data packet for TFTP writes
	// Consists of DATA opcode, block number and actual data
	// Uses the FileOperation class to divide the file into data packets
	private int constructNextReadPacket(byte[] msg, int blockNumber, FileOperation writeFile)
			throws FileNotFoundException {
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
			if (verbose != Verbosity.NONE)
			{
				System.out.println("Received final write packet from server");
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
