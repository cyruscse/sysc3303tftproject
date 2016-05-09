package grouptwo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class ClientConnectionThread implements Runnable{
	// types of requests we can receive
	public static enum Request { READ, WRITE };
	private Request requestType;

	//"some" verbosity prints packet details omitting data contents,
	//"all" verbosity prints everything (including the 512 data bytes)
	public static enum Verbosity { NONE, SOME, ALL };
	private Verbosity verbosity;

	DatagramPacket receivePacket;
	DatagramPacket sendPacket;
	DatagramSocket sendReceiveSocket;

    private FileOperation  fileOp;
	private TFTPServer parent;
	private String filename;

	public ClientConnectionThread(DatagramPacket receivePckt, TFTPServer parent, TFTPServer.Request requestResponse, String filename) {
		this.receivePacket = receivePckt;
		this.parent = parent;

		if ( requestResponse == TFTPServer.Request.READ ) this.requestType = Request.READ;
		else if ( requestResponse == TFTPServer.Request.WRITE ) this.requestType = Request.WRITE;

		this.filename = filename;
	}

	@Override
	public void run() {

		byte[] data,
		response = new byte[4];
		int len, j=0, k=0;
		
		System.out.println("Server: new thread created");

		// Create a response.
		if (requestType==Request.READ)  // for Read it's 03 then block number
		{
			
			try {
                fileOp = new FileOperation(localName, true, 512);
            } catch (FileNotFoundException e) {
                System.out.println("Local file " + localName + " does not exist!");
                return;
            }
			
			/*TODO	Here is where we set up the file stream OUT of the file on the server to be read
			 * 		read by the client:
			 * 			1) Have to setup a loop through all blocks of the file
			 * 			2) For each block in the file we have to (in order) :	
			 * 				-set up the message (with 512 bytes being a data block from the file)
			 * 				-create the datagram
			 * 				-send the datagram
			 * 				-receive the response
			 * 				-verify the response is an ACK
			 * 				-loop again (now on the next block)
			 * 		
			 * 		Look at Cyrus' Client code for an idea on how this is done the other way.
			 */
			
		}
		else if (requestType==Request.WRITE) // for Write it's 0400
		{
			
			try {
                fileOp = new FileOperation(localName, false, 512);
            } catch (FileNotFoundException e) {
                System.out.println("Couldn't write to " + localName);
                return;
            }
			
			/*TODO	Here is where we set up the file stream INTO the file on the server which is
			 * 		a write by the client. Generally the same as above to-do comment block but 
			 * 		with different roles.
			 */
			
		} else { // it was invalid, just quit
			// throw new Exception("Not yet implemented");
		}

		/*sendPacket = new DatagramPacket(response, response.length,
				receivePacket.getAddress(), receivePacket.getPort());

		System.out.println("Server: Sending packet:");
		System.out.println("To host: " + sendPacket.getAddress());
		System.out.println("Destination host port: " + sendPacket.getPort());
		len = sendPacket.getLength();
		System.out.println("Length: " + len);
		System.out.println("Containing: ");
		for (j=0;j<len;j++) {
			System.out.println("byte " + j + " " + response[j]);
		}

		// Send the datagram packet to the client via a new socket.

		try {
			// Construct a new datagram socket and bind it to any port
			// on the local host machine. This socket will be used to
			// send UDP Datagram packets.
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("Server: packet sent using port " + sendReceiveSocket.getLocalPort());
		System.out.println(); */


		// We're finished with this socket, so close it.
		sendReceiveSocket.close();
		parent.threadDone();
		// done the thread will die automatically
	}

}
