package grouptwo;

import java.io.*;
import java.net.*;

/**
 * TFTPCommon contains methods and enums that are used in all the components of this project.
 * The methods that handles sending and receiving DATA and ACK packets are located here.
 * Various toString methods for each enum are also stored here. This common class
 * allowed us to remove a significant amount of duplicate code from the client and server.
 *
 * @author        Cyrus Sadeghi
 * @author        Eliab Woldeyes
 * @author        Kenan El-Gaouny
 * @author        Majeed Mirza
 * @author        Rishabh Singh
 */
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
	public static enum PacketType { INVALID, ACK, DATA, REQUEST, ERROR };

	//Error Simulator modes
	public static enum ModificationType { NONE, LOSE, DUPLICATE, DELAY, CONTENTS, INVALIDTID };

	//TFTP Error Codes (Iteration 3 only uses ILLEGAL and UTID)
	public static enum ErrorCode { INVALID, FILENOTFOUND, ACCESSVIOLATE, DISKFULL, ILLEGAL, UNKNOWNTID, FILEEXISTS };
	
	//CONTENTS ModificationType subtypes
	public static enum ContentSubmod { INVALID, MANUAL, OPCODE, BLOCKNUM, LENGTH, FILENAME, FILEMODE };

	//Server Listen Port
	public static int TFTPListenPort = 69;

	//Error Sim Listen Port
	public static int TFTPErrorSimPort = 23;

	/**
	 *   Send a DatagramPacket through a DatagramSocket.
	 *
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
	 *
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
	 *
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

	/**
	 *   Send an ACK packet with the specified block number
	 *
	 *   @param  int block number to send ACK for
	 *   @param  DatagramPacket to send
	 *   @param  DatagramPacket to get destination details
	 *   @param  DatagramSocket to send ACK with
	 *   @param  Verbosity verbosity of caller
	 *   @param  String console prefix of caller
	 *   @return none
	 * 
	 */
	public static void sendACKPacket(int blockNum, DatagramPacket send, DatagramPacket receive, DatagramSocket socket, Verbosity verbose, String consolePrefix)
	{
		byte[] msg = new byte[4];
		constructAckPacket(msg, blockNum);
		send = new DatagramPacket(msg, msg.length, receive.getAddress(), receive.getPort());
		System.out.println(consolePrefix + "Sending ACK " + blockNum);
		printPacketDetails(send, verbose, false);
		sendPacket(send, socket);
	}
	/**
	 *   Send an Error Packet packet with the specified Error Code
	 *
	 *   @param  DatagramPacket to get destination details
	 *   @param  DatagramSocket to send ERROR Packet with
	 *   @param  String to be printed and inserted into the ERROR Packet
	 *   @param  ErrorCode to be included in the ERROR packet
	 *   @param  String console prefix of caller
	 *   @param  Verbosity for printing details
	 *   @return none
	 * 
	 */
	public static void sendErrorPacket(DatagramPacket receive, DatagramSocket socket, String errString, ErrorCode errCode, String consolePrefix, Verbosity verbose)
	{		
		byte[] errMsg = new byte[200];
		int errlen = constructErrorPacket(errMsg, errCode, errString);
		System.out.println(consolePrefix + errString);
		if(verbose != Verbosity.NONE){
			System.out.println(consolePrefix + "Sending ERROR packet to port " + receive.getPort());
		}
		else{
			System.out.println(consolePrefix + "Sending ERROR packet");
		}
		DatagramPacket sendErr = new DatagramPacket(errMsg, errlen, receive.getAddress(), receive.getPort());
		printPacketDetails(sendErr, verbose, false);
		sendPacket(sendErr, socket);
	}
	
	/**
	 *   Send a file with timeouts and retransmits
	 *
	 *   @param  DatagramPacket to send with
	 *   @param  DatagramPacket to receive with
	 *   @param  DatagramSocket to send and receive packets with
	 *   @param  InetAddress of packet destination
	 *   @param  int timeout per packet sent, before sending packet again
	 *   @param  int number of timeouts to wait before giving up
	 *   @param  int port to send packet to
	 *   @param  FileOperation file to read from
	 *   @param  Verbosity verbosity of caller
	 *   @param  String console prefix of caller
	 *   @return Boolean true if file was sent successfully
	 * 
	 */
	public static Boolean sendDataWTimeout (DatagramPacket send, DatagramPacket receive, DatagramSocket sendReceiveSocket, InetAddress address, int timeout, int maxTimeout, int port, FileOperation fileOp, Verbosity verbose, String consolePrefix)
	{
		int timeoutCount = 0;
		int blockNum = 1;
		int len = 0;
		Boolean sendData = true;
		byte[] dataMsg = new byte[516];
		byte[] ackMsg = new byte[516];

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
			ackMsg = new byte[516];
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
				if (validACKPacket(ackMsg, blockNum) && receive.getLength() == 4) 
				{
					System.out.println(consolePrefix + "Received valid ACK " + blockNum);
					printPacketDetails(receive, verbose, false);

					timeoutCount = 0; //Reset timeout count once a successful ACK is received
					blockNum++;
					sendData = true;
					
					if (receive.getPort() != port) {
						String errString = "Received packet from unknown port: " + receive.getPort();
						sendErrorPacket(receive, sendReceiveSocket, errString, ErrorCode.UNKNOWNTID, consolePrefix, Verbosity.NONE);
					}
				}
				else if (getPacketType(ackMsg) == PacketType.ACK && blockNumToPacket(ackMsg) < blockNum) 
				{
					System.out.println(consolePrefix + "Duplicate ACK " + blockNumToPacket(ackMsg) + " received, ignoring");
					sendData = false;
				} 
				else 
				{
					String errString = "";
					
					if (getPacketType(ackMsg) == PacketType.ERROR)
					{
						parseErrorPacket(ackMsg, consolePrefix);
						return false;
					}
					else if (getPacketType(ackMsg) != PacketType.ACK)
					{
						errString = "Expecting ACK, received invalid Opcode";
					}
					else if(blockNumToPacket(ackMsg) != blockNum)
					{
						errString = "Expecting block number " + blockNum + " instead received " + blockNumToPacket(ackMsg);
					}
					else if(receive.getLength() != 4 && getPacketType(ackMsg) == PacketType.ACK){
						errString = "Expecting ACK packet of length 4 instead received packet with length " + receive.getLength();
					}
					else{
						errString = "An error occurred";
					}

					sendErrorPacket(receive, sendReceiveSocket, errString, ErrorCode.ILLEGAL, consolePrefix, Verbosity.NONE);
					return false;
				}
			}			
		}

		return true;
	}

	/**
	 *   Receive a file with timeouts and retransmits
	 *
	 *   @param  DatagramPacket to send with
	 *   @param  DatagramPacket to receive with
	 *   @param  DatagramSocket to send and receive packets with
	 *   @param  Boolean true if client is calling, allows us to resend request packets
	 *   @param  int timeout to stop waiting (i.e. side sending DATA packets is gone)
	 *   @param  FileOperation file to read from
	 *   @param  Verbosity verbosity of caller
	 *   @param  String console prefix of caller
	 *   @return Boolean true if file was received successfully
	 * 
	 */
	public static Boolean receiveDataWTimeout (DatagramPacket send, DatagramPacket receive, DatagramSocket sendReceiveSocket, Boolean client, int hardTimeout, FileOperation fileOp, Verbosity verbose, String consolePrefix)
	{
		Boolean writingFile = true;
		Boolean willExit = false;
		Boolean receiveSet = client;
		byte[] dataMsg;
		int blockNum = 1;
		int len = 0;
		int port = -1;
	
		
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

			if (port == -1)
			{
				port = receive.getPort();
			}
			
		    if (receive.getPort() != port) 
		    {
				String errString = "Received packet from unknown port: " + receive.getPort();
				sendErrorPacket(receive, sendReceiveSocket, errString, ErrorCode.UNKNOWNTID, consolePrefix, Verbosity.NONE);
			}

			//We received a DATA packet but it is not the block number we were expecting (i.e. delayed/lost DATA)
			else if (getPacketType(dataMsg) == PacketType.DATA && receive.getLength() >= 4) 
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
					System.out.println(consolePrefix + "Received DATA " + blockNum);
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
				String errString = "";

				if(receive.getLength() < 4){
					errString = "The packet is too small";
				}
				else if (getPacketType(dataMsg) == PacketType.ERROR)
				{
					parseErrorPacket(dataMsg, consolePrefix);
					return false;
				}
				else if (getPacketType(dataMsg) != PacketType.DATA)
				{
					errString = "Expecting DATA, received invalid Opcode";
				}
				else if (blockNumToPacket(dataMsg) != blockNum)
				{
					errString = "Expecting block number " + blockNum + " instead received " + blockNumToPacket(dataMsg);
				}
				else{
					errString = "An error occurred";
				}
				
				sendErrorPacket(receive, sendReceiveSocket, errString, ErrorCode.ILLEGAL, consolePrefix, Verbosity.NONE);
				return false;
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
	 *   Prints basic packet details based on the verbosity of the caller
	 *
	 *   @param  DatagramPacket to print details of
	 *   @param	 Verbosity level of the caller
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
			String sending = new String(packet.getData(), 0, packet.getLength());

			if (verbosity == TFTPCommon.Verbosity.ALL)
			{ 
				System.out.println("Client: request packet contains " + sending);
			}
		}		
	}

	/**
	 *   Convert DatagramPacket data contents to TFTP packet type and number
	 *
	 *   @param  byte[] data from DatagramPacket
	 *   @return String
	 * 
	 */
	public static String packetTypeAndNumber (byte[] data)
	{
		if ( getPacketType(data) == PacketType.REQUEST || getPacketType(data) == PacketType.ERROR )
		{
			return (opcodeToString(data) + " packet");
		}
		return (opcodeToString(data) + " packet " + blockNumToPacket(data));
	}

	/**
	 *   Convert opcode from DatagramPacket data contents to PacketType enum
	 *
	 *   @param  byte[] data from DatagramPacket
	 *   @return PacketType
	 * 
	 */
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
        else if (data[1] == 5)
        {
        	return PacketType.ERROR;
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
		else if (data[1] == 5)
		{
			return "ERROR";
		}

		return "invalid";
	}

	/**
	 *   Convert PacketType to String
	 *
	 *   @param  PacketType to convert to String
	 *   @return String
	 * 
	 */
	public static String packetTypeToString (PacketType type)
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
		else if (type == PacketType.ERROR)
		{
			return "ERROR";
		}

		return "invalid";
	}
	
	/**
	 *   Convert PacketType to String
	 *
	 *   @param  PacketType to convert to String
	 *   @return String
	 * 
	 */
	public static String errorOpcodeToString (byte[] data)
	{
		if (data[2] != 0)
		{
			return "invalid";
		}

		if (data[3] == 0)
		{
			return "not defined";
		}
		else if (data[3] == 1)
		{
			return "file not found";
		}
		else if (data[3] == 2)
		{
			return "access violation";
		}
		else if (data[3] == 3)
		{
			return "disk full";
		}
		else if (data[3] == 4)
		{
			return "illegal TFTP operation";
		}
		else if (data[3] == 5)
		{
			return "unknown transfer ID";
		}
		else if (data[3] == 6)
		{
			return "file already exists";
		}
		return "invalid";
	}

	/**
	 *   Convert ModificationType to String
	 *
	 *   @param  ModificationType to convert to String
	 *   @return String
	 * 
	 */
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
        else if (error == ModificationType.CONTENTS)
        {
        	return "contents";
        }
        else if (error == ModificationType.INVALIDTID)
        {
        	return "invalid TID";
        }

        return "invalid";
    }

    public static int packetTypeToPacketSize (PacketType type)
    {
    	if (type == PacketType.ACK)
    	{
    		return 4;
    	}
    	else if (type == PacketType.DATA)
    	{
    		return 516;
    	}
    	else if (type == PacketType.REQUEST || type == PacketType.ERROR)
    	{
    		return 100;
    	}

  		return 100;
    }

    /**
	 *   Constructs WRQ or RRQ packet
	 *
	 *   @param  byte[] array to store packet data in
	 *   @param  int opcode of packet
	 *   @param  String filename for read or write
	 *   @param  String filemode for read or write
	 *   @return int length of packet
	 */
    public static int constructReqPacket(byte[] msg, int opcode, String fileName, String fileMode)
    {
    	byte[] fn, md;

    	msg[0] = (byte) (opcode / 10);
    	msg[1] = (byte) (opcode % 10);

    	fn = fileName.getBytes();
    	System.arraycopy(fn, 0, msg, 2, fn.length);
    	msg[fn.length+2] = 0;
    	md = fileMode.getBytes();
    	System.arraycopy(md, 0, msg, fn.length+3, md.length);
    	msg[fn.length+md.length+3] = 0;

    	return (fn.length + md.length + 4);
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
	 *   Constructs an ERROR packet
	 *
	 *   @param  byte[] array to store packet data in
	 *   @param  ErrorCode specifying which error
	 *   @param  String error message
	 *   @return none
	 */
	public static int constructErrorPacket (byte[] msg, ErrorCode errCode, String errorMsg)
	{
		byte[] em;
		em = errorMsg.getBytes();
		System.arraycopy(em, 0, msg, 4, em.length);
		msg[em.length + 4] = 0;
		
		msg[0] = 0;
		msg[1] = 5;

		if (errCode == ErrorCode.FILENOTFOUND) 
		{
			msg[2] = 0;
			msg[3] = 1;
		}
		else if (errCode == ErrorCode.ACCESSVIOLATE) 
		{
			msg[2] = 0;
			msg[3] = 2;
		}
		else if (errCode == ErrorCode.DISKFULL) 
		{
			msg[2] = 0;
			msg[3] = 3;
		}
		else if (errCode == ErrorCode.ILLEGAL) 
		{
			msg[2] = 0;
			msg[3] = 4;
		}
		else if (errCode == ErrorCode.UNKNOWNTID) 
		{
			msg[2] = 0;
			msg[3] = 5;
		}
		else if (errCode == ErrorCode.FILEEXISTS) 
		{
			msg[2] = 0;
			msg[3] = 6;
		}
		else
		{
			msg[2] = 0;
			msg[3] = 0;
		}
		return em.length + 5;
	}

	public static void parseErrorPacket(byte[] data, String consolePrefix)
	{
		int i;

		for (i = 4; i < data.length; i++)
		{
			if (data[i] == 0)
			{
				break;
			}
		}

		String errorMessage = new String(data, 4, i - 2);

		System.out.println("Received error " + errorOpcodeToString(data) + " with message \"" + errorMessage.trim() + "\"");
	}
	
	public static ErrorCode getErrorType(byte[] data)
    {
        if (data[3] == 1)
        {
            return ErrorCode.FILENOTFOUND;
        }

        if (data[3] == 2)
        {
            return ErrorCode.ACCESSVIOLATE;
        }
        else if (data[3] == 3)
        {
            return ErrorCode.DISKFULL;
        }
        else if (data[3] == 4)
        {
            return ErrorCode.ILLEGAL;
        }
        else if (data[3] == 5)
        {
        	return ErrorCode.UNKNOWNTID;
        }
        else if (data[3] == 6)
        {
        	return ErrorCode.FILEEXISTS;
        }

        return ErrorCode.INVALID;
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
