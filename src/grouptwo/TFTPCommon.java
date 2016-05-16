package grouptwo;

import java.io.*;
import java.net.*;

public class TFTPCommon {

	//Normal Mode: Skip error simulator
	//Test Mode: Use error simulator as intermediate host
	public static enum Mode { NORMAL, TEST };

	//Request Packet Types
	public static enum Request { ERROR, READ, WRITE };

	//"SOME" verbosity prints all packet information except for data contents
	//"ALL" prints all packet information
	public static enum Verbosity { NONE, SOME, ALL };

	//TFTP packet types
	public static enum PacketType { INVALID, ACK, DATA, REQUEST };

	//Error Simulator modes
	public static enum ModificationType { NONE, LOSE, DUPLICATE, DELAY };

	public static int TFTPListenPort = 69;

	public static int TFTPErrorSimPort = 23;

	/**
	 *   Send a DatagramPacket through a DatagramSocket.
	 *   @param  DatagramPacket to send
	 *   @param  DatagramSocket to send from
	 *   @return none
	 */
	public static void sendPacket(DatagramPacket packet, DatagramSocket socket)
	{
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}	
	}

	/**
	 *   Receive a DatagramPacket through a DatagramSocket.
	 *   @param  DatagramPacket instance to receive
	 *   @param  DatagramSocket to receive from
	 *   @return none
	 * 
	 */
	public static void receivePacket(DatagramPacket packet, DatagramSocket socket)
	{
		try {
			socket.receive(packet);
		} catch (SocketException e) {
			System.out.println("Socket closed, no longer accepting new packets.");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}	
	}

	/**
	 *   Receive a DatagramPacket through a DatagramSocket with a timeout.
	 *   @param  DatagramPacket instance to receive
	 *   @param  DatagramSocket to receive from
	 *   @param timeout time before timeout in milliseconds
	 *   @throws SocketTimeoutException
	 *   @return none
	 * 
	 */
	public static void receivePacketWTimeout(DatagramPacket packet, DatagramSocket socket, int timeout) throws SocketTimeoutException 
	{ 
		try {
			socket.setSoTimeout(timeout);
			socket.receive(packet);
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (SocketException e) {
			System.out.println("Local socket closed, no longer accepting new packets.");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	public static void sendACKPacket(int blockNum, DatagramPacket send, DatagramPacket receive, DatagramSocket socket, Verbosity verbose, String consolePrefix)
	{
		byte[] msg = new byte[4];
		constructAckPacket(msg, blockNum);
		send = new DatagramPacket(msg, msg.length, receive.getAddress(), receive.getPort());
		System.out.println(consolePrefix + "Sending ACK " + blockNum);
		printPacketDetails(send, verbose, false);
		sendPacket(send, socket);
	}

	public static Boolean sendDataWTimeout (DatagramPacket send, DatagramPacket receive, DatagramSocket sendReceiveSocket, InetAddress address, int timeout, int maxTimeout, int port, FileOperation fileOp, Verbosity verbose, String consolePrefix)
	{
		int timeoutCount = 0;
		int blockNum = 1;  ///CHANGED from 0
		int len = 0;
		Boolean sendData = true;
		byte[] dataMsg = new byte[516];
		byte[] ackMsg = new byte[4];

		while (blockNum-1 < fileOp.getNumTFTPBlocks())
		{
			if (sendData)
			{
				if (timeoutCount == 0)
				{
					dataMsg = new byte[516];
					len = constructDataPacket(dataMsg, blockNum, fileOp);
				}

				System.out.println(consolePrefix + "Sending DATA " + blockNum + "/" + (fileOp.getNumTFTPBlocks()));

				send = new DatagramPacket(dataMsg, len, address, port);

				printPacketDetails(send, verbose, false);
				sendPacket(send, sendReceiveSocket);
			}

			// Receive the client response for the data packet we just sent
			ackMsg = new byte[4];
			receive = new DatagramPacket(ackMsg, ackMsg.length);

			if (timeoutCount < maxTimeout)
			{	
				if (verbose != Verbosity.NONE)
				{
					System.out.println(consolePrefix + "Waiting for data read acknowledgement");
				}
				
				try {
					receivePacketWTimeout(receive, sendReceiveSocket, timeout);
				} catch (SocketTimeoutException e) {
					timeoutCount++;
					System.out.println(consolePrefix + "Receive timed out after " + timeout + " ms");
					System.out.println(consolePrefix + "Resending DATA " + blockNum  + ": Attempt " + timeoutCount);
					sendData = true;
				}
			}
			else
			{
				System.out.println(consolePrefix + "Maximum timeouts reached for DATA " + blockNum + ". Thread returning.");
				return false;
			}

			if (receive.getPort() != -1)
			{ 
				if (validACKPacket(ackMsg, blockNum)) 
				{
					System.out.println(consolePrefix + "Received valid ACK " + blockNum);
					printPacketDetails(receive, verbose, false);

					timeoutCount = 0; //Reset timeout count once a successful ACK is received
					blockNum++;
					sendData = true;
				}
				else if (getPacketType(ackMsg) == PacketType.ACK && blockNumToPacket(ackMsg) < blockNum) 
				{
					System.out.println(consolePrefix + "Duplicate ACK " + blockNumToPacket(ackMsg) + " received, ignoring");
					sendData = false;
				} 
				else 
				{
					System.out.println(consolePrefix + "Invalid packet received, ignoring");
					printPacketDetails(receive, verbose, false);
					sendData = false;
				}
			}			
		}

		return true;
	}

	public static Boolean receiveDataWTimeout (DatagramPacket send, DatagramPacket receive, DatagramSocket sendReceiveSocket, Boolean client, int hardTimeout, FileOperation fileOp, Verbosity verbose, String consolePrefix)
	{
		Boolean writingFile = true;
		Boolean willExit = false;
		Boolean receiveSet = client;
		byte[] dataMsg, ackMsg;
		int blockNum = 1; //Changed from 0
		int len = 0;

		while (writingFile)
		{
			dataMsg = new byte[516];
			
			if (!receiveSet)
			{
				receive = new DatagramPacket(dataMsg, dataMsg.length);

				if (verbose != Verbosity.NONE)
				{
					System.out.println(consolePrefix + "Waiting for next data packet");
				}

				try {
					receivePacketWTimeout(receive, sendReceiveSocket, hardTimeout);
				} catch (SocketTimeoutException e) {
					System.out.println(consolePrefix + "Haven't received packet in " + hardTimeout + "ms, giving up");
					return false;
				}
			}
			else
			{
				dataMsg = receive.getData();
				receiveSet = false;
			}

			len = receive.getLength();
			
			printPacketDetails(receive, verbose, false);

			System.out.println(consolePrefix + "Received DATA " + blockNum);


			//We received a DATA packet but it is not the block number we were expecting (i.e. delayed/lost DATA)
			if (getPacketType(dataMsg) == PacketType.DATA) 
			{
				//Duplicate DATA received (i.e. block number has already been acknowledged)
				if (blockNumToPacket(dataMsg) < blockNum)
				{	
					System.out.println(consolePrefix + "Duplicate or delayed DATA " + blockNumToPacket(dataMsg) + " received, not writing to file");
					sendACKPacket(blockNumToPacket(dataMsg), send, receive, sendReceiveSocket, verbose, consolePrefix);
				}	
				//We received the DATA packet we were expecting, look for next DATA
				else if (blockNumToPacket(dataMsg) == blockNum)
				{
					try {
						willExit = writeDataPacket(dataMsg, len, fileOp, verbose);
					} catch (Exception e) {
						return false;
					}

					sendACKPacket(blockNum, send, receive, sendReceiveSocket, verbose, consolePrefix);					
					blockNum++;
				}
			}
			else
			{
				System.out.println(consolePrefix + "Non-DATA packet received, ignoring");
			}
			
			//Can't exit right after we receive the last data packet,
			//we have to acknowledge the data first
			if (willExit)
			{
				break;
			}
		}

		return true;
	}

	/**
	 *   Prints basic packet details based on the verbosity of the host
	 *
	 *   @param  DatagramPacket to print details of
	 *   @param	 Verbosity level of the host
	 *   @param	 Boolean to decide to print the packet data as a string or not
	 *   @return none
	 */
	public static void printPacketDetails(DatagramPacket packet, Verbosity verbosity, Boolean printAsString) 
	{
		int j;

		if ( verbosity != TFTPCommon.Verbosity.NONE ) 
		{
			System.out.println("Host: " + packet.getAddress());
			System.out.println("Host port: " + packet.getPort());
			System.out.println("Length: " + packet.getLength());
			System.out.println("Packet type: " + opcodeToString(packet.getData()));

			if ( verbosity == TFTPCommon.Verbosity.ALL )
			{
				System.out.println("Containing: ");
				for (j = 0; j < packet.getLength(); j++) 
				{
					System.out.print((packet.getData()[j] & 0xFF) + " ");
				}
			}
		}

		if (printAsString)
		{
			// Form a String from the byte array, and print the string.
			String sending = new String(packet.getData(), 0, packet.getLength());

			if (verbosity == TFTPCommon.Verbosity.ALL)
			{ 
				System.out.println("Client: request packet contains " + sending);
			}
		}		
	}

	public static String packetTypeAndNumber (byte[] data)
	{
		if ( getPacketType(data) == PacketType.REQUEST)
		{
			return (opcodeToString(data) + " packet");
		}
		return (opcodeToString(data) + " packet " + blockNumToPacket(data));
	}

    public static PacketType getPacketType(byte[] data)
    {
        if (data[0] != 0)
        {
            return PacketType.INVALID;
        }

        if (data[1] == 1 || data[1] == 2)
        {
            return PacketType.REQUEST;
        }
        else if (data[1] == 3)
        {
            return PacketType.DATA;
        }
        else if (data[1] == 4)
        {
            return PacketType.ACK;
        }

        return PacketType.INVALID;
    }

	/**
	 *   Converts Request to String (used by CLI)
	 *
	 *   @param  Request to convert to String
	 *   @return String of converted Request
	 */
	public static String requestToString(Request req) 
	{
		if ( req == Request.READ ) 
		{
			return "read";
		}
		else if ( req == Request.WRITE ) 
		{
			return "write";
		}
		return "";
	}

	/**
	 *   Converts Mode to String (used by CLI)
	 *
	 *   @param  Mode to convert to String
	 *   @return String of converted Mode
	 */
	public static String modeToString(Mode req) 
	{
		if ( req == Mode.NORMAL ) 
		{
			return "normal";
		}
		else if ( req == Mode.TEST ) 
		{
			return "test";
		}
		return "";
	}

	/**
	 *   Converts Verbosity to String (used by CLI)
	 *
	 *   @param  Verbosity to convert to String
	 *   @return String of converted Verbosity
	 */
	public static String verbosityToString(Verbosity ver)
	{
		if ( ver == Verbosity.NONE )
		{
			return "none";
		}
		else if ( ver == Verbosity.SOME )
		{
			return "some (basic packet details)";
		}
		else if ( ver == Verbosity.ALL )
		{
			return "all (full packet details, including data contents)";
		}
		return "";
	}

	/**
	 *   Converts opcode to String
	 *
	 *   @param  byte[] containing opcode in first two bytes
	 *   @return String of converted opcode
	 */
	public static String opcodeToString(byte[] data)
	{
		if (data[0] != 0)
		{
			return "invalid";
		}

		if (data[1] == 1)
		{
			return "RRQ";
		}
		else if (data[1] == 2)
		{
			return "WRQ";
		}
		else if (data[1] == 3)
		{
			return "DATA";
		}
		else if (data[1] == 4)
		{
			return "ACK";
		}

		return "invalid";
	}

	//find way to merge this with opcodeToString
	public static String packetTypeToString(PacketType type)
	{
		if (type == PacketType.ACK)
		{
			return "ACK";
		}
		else if (type == PacketType.DATA)
		{
			return "DATA";
		}
		else if (type == PacketType.REQUEST)
		{
			return "WRQ/RRQ";
		}

		return "invalid";
	}

    public static String errorSimulateToString (ModificationType error)
    {
        if (error == ModificationType.LOSE)
        {
            return "lose";
        }
        else if (error == ModificationType.DUPLICATE)
        {
            return "duplicate";
        }
        else if (error == ModificationType.DELAY)
        {
            return "delay";
        }

        return "invalid";
    }

	/**
	 *   Constructs ACK packet, converts int blockNumber to byte representation
	 *
	 *   @param  byte[] array to store packet data in
	 *   @param  int current block number
	 *   @return none
	 */
	public static void constructAckPacket(byte[] msg, int blockNumber)
	{
		msg[0] = 0;
		msg[1] = 4;
		msg[2] = (byte) (blockNumber / 256);
		msg[3] = (byte) (blockNumber % 256);        
	}

	/**
	 *   Constructs a DATA packet
	 *
	 *   @param  byte[] next data block to send
	 *   @param  int current block number
	 *   @param  FileOperation current file we are reading data from
	 *   @return int length of DATA packet
	 */
	public static int constructDataPacket(byte[] msg, int blockNumber, FileOperation file) 
	{
		msg[0] = 0;
		msg[1] = 3;
		msg[2] = (byte) (blockNumber / 256);
		msg[3] = (byte) (blockNumber % 256);   

		try {
			return file.readNextDataPacket(msg, 4);
		} catch (FileNotFoundException e) {			//This exception should never happen, we check the file before creating packets
			e.printStackTrace();					//It still needs to be caught though
			System.exit(1);
			return -1;   							//Need this return for compilation (even though it's after exit)
		}
	}

	/**
	 *   Convert 16 bit block number to int packet number
	 *
	 *   @param  byte[] contents of packet
	 *   @return int block number
	 */
	public static int blockNumToPacket(byte[] data)
    {
	 	Byte a = data[2];
	 	Byte b = data[3];
	 	
        return Byte.toUnsignedInt(a) * 256 + Byte.toUnsignedInt(b);
    }
    
	/**
	 *   Processes DATA packet
	 *
	 *   @param  byte[] data sent from server to client
	 *   @param  int length of data
	 *   @param  FileOperation current file we are writing to
	 *   @return Boolean indicating if this was the final DATA packet
	 */
	public static Boolean writeDataPacket(byte[] msg, int len, FileOperation file, Verbosity verbose) throws Exception
	{
		if (msg[0] != 0 || msg[1] != 3)
		{
			throw new Exception("Invalid data packet");
		}
		
		file.writeNextDataPacket(msg, 4, len - 4);

		if (len < 516)
		{
			if (verbose != Verbosity.NONE)
			{
				System.out.println("Received final data packet");
				file.finalizeFileWrite();
			}

			return true;
		}

		return false;
	}

	/**
	 *   Check validity of received DATA packet
	 *
	 *   @param  byte[] packet data
	 *   @param  int length of data
	 *   @return Boolean indicating if DATA is valid or not
	 */
	public static Boolean validDATAPacket(byte[] data, int blockNum)
	{
		return ( getPacketType(data) == PacketType.DATA && blockNumToPacket(data) == blockNum );
	}
	
	/**
	 *   Check validity of received ACK packet
	 *
	 *   @param  byte[] packet data
	 *   @param  int length of data
	 *   @return Boolean indicating if ACK is valid or not
	 */
	public static Boolean validACKPacket(byte[] data, int blockNum)
	{
		return ( getPacketType(data) == PacketType.ACK && blockNumToPacket(data) == blockNum );
	}
}
