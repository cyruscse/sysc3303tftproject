package grouptwo;

import java.io.*;
import java.net.*;

public class TFTPCommon {

	// we can run in normal (send directly to server) or test
	// (send to simulator) mode
	public static enum Mode { NORMAL, TEST };

	// types of request beteen server and client
	public static enum Request { READ, WRITE, ERROR };

	// verbosity possible settings
	//"some" verbosity prints packet details omitting data contents,
	//"all" verbosity prints everything (including the 512 data bytes)
	public static enum Verbosity { NONE, SOME, ALL };

	/**
	 *   Create a DatagramSocket instance. If port parameter is 0, then the
	 *   socket will be created on the first available port.
	 *   
	 *   IMPORTANT:	Must make sure to call .close() on the socket when 
	 *   			finished with it.
	 *   @param  DatagramSocket to create
	 *   @return none
	 */
	public static void createSocket(DatagramSocket socket, int port)
	{
		try {
			socket = new DatagramSocket(port);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 *   Create a DatagramPacket instance.
	 *   @param  DatagramPacket to create
	 *   @return none
	 */
	public static void createPacket(DatagramPacket packet, byte[] data, int dataLength, InetAddress address, int port)
	{
		packet = new DatagramPacket(data, dataLength, address, port);
	}

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
		} 
		catch (SocketException e) {
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
	public static void receivePacketWTimeout(DatagramPacket packet, DatagramSocket socket,
			int timeout) throws SocketTimeoutException { 
		try {
			socket.setSoTimeout(timeout);
			socket.receive(packet);
		} catch(SocketTimeoutException se){
			throw se;
		}catch (SocketException e) {
			System.out.println("Socket closed, no longer accepting new packets.");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
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
					System.out.println("byte " + j + " " + (packet.getData()[j] & 0xFF));
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
			return "normal";
		}
		else if ( ver == Verbosity.SOME )
		{
			return "basic packet details";
		}
		else if ( ver == Verbosity.ALL )
		{
			return "full packet details (including data contents)";
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
	public static int constructDataPacket(byte[] msg, int blockNumber, FileOperation file) throws FileNotFoundException 
	{
		msg[0] = 0;
		msg[1] = 3;
		msg[2] = (byte) (blockNumber / 256);
		msg[3] = (byte) (blockNumber % 256);   

		return file.readNextDataPacket(msg, 4);
	}

	/**
	 *   Processes DATA packet
	 *
	 *   @param  byte[] data sent from server to client
	 *   @param  int length of data
	 *   @param  FileOperation current file we are writing to
	 *   @return Boolean indicating if this was the final DATA packet
	 */
	public static Boolean validDataPacket(byte[] msg, int len, FileOperation file, Verbosity verbose) throws Exception
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
		else
		{
			return false;
		}
	}

	
	/**
	 *   Processes ACK packet
	 *
	 *   @param  byte[] packet data
	 *   @param  int length of data
	 *   @return Boolean indicating if ACK is valid or not
	 */
	public static Boolean validACKPacket(DatagramPacket packet, int blockNum)
	{
		if ( packet.getData()[0] == 0 && packet.getData()[1] == 4 && (packet.getData()[2] & 0xFF) == ((byte)(blockNum / 256) & 0xFF)
						&& (packet.getData()[3] & 0xFF) ==  ((byte)(blockNum % 256) & 0xFF) )
		{
			return true;
		}
		else return false;
	}

}
