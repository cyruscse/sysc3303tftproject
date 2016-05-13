// TFTPSim.java
// This class is the beginnings of an error simulator for a simple TFTP server 
// based on UDP/IP. The simulator receives a read or write packet from a client and
// passes it on to the server.  Upon receiving a response, it passes it on to the 
// client.
// One socket (23) is used to receive from the client, and another to send/receive
// from the server.  A new socket is used for each communication back to the client.   
package grouptwo;

import java.io.*;
import java.net.*;
import java.util.*;

public class TFTPIntHost 
{ 
    private DatagramSocket receiveSocket;
    private DatagramPacket receivePacket;
    private TFTPCommon.Verbosity verbosity;
    private TFTPIntHostCommandLine cliThread;
    private byte [] data;
    private Set<SimulatePacketInfo> toModify;

    public TFTPIntHost()
    {
        try {
            receiveSocket = new DatagramSocket(23);
        } catch (SocketException se) {
            se.printStackTrace();
            System.exit(1);
        }

        verbosity = TFTPCommon.Verbosity.NONE;
        cliThread = new TFTPIntHostCommandLine(this);
    }

    /**
    *   Called by CLI thread, sets the verbosity for new ClientConnectionThreads
    *   Note: This doesn't change verbosity for ongoing transfers
    *
    *   @param  Verbosity verbosity to set to
    *   @return none
    */
    public void setVerbosity(TFTPCommon.Verbosity v) 
    {
        this.verbosity = v;
    }

    public void nextSimulateSet(Set<SimulatePacketInfo> nextSet)
    {
        toModify = nextSet;
    }

    private void processClients()
    {
        cliThread.start();

        System.out.println("Error Simulator: Waiting for clients.");
        while (true)
        {
            data = new byte[100];
            receivePacket = new DatagramPacket(data, data.length);

            try {
                receiveSocket.receive(receivePacket); //TODO: need to print packet details
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            Thread client = new Thread(new ErrorSimulator(receivePacket, verbosity, toModify));
            client.start();
        }
    }

    public static void main( String args[] )
    {
        TFTPIntHost s = new TFTPIntHost();
        s.processClients();
    }
}

class ErrorSimulator extends Thread
{
    private DatagramSocket sendReceiveSocket;
    private DatagramPacket sendPacket, receivePacket;
    private TFTPCommon.Verbosity verbosity;
    private InetAddress clientAddress;
    private int clientPort, serverPort, len;
    private byte [] data;
    private Set<SimulatePacketInfo> simulateSet;

    public ErrorSimulator(DatagramPacket firstPacket, TFTPCommon.Verbosity verbose, Set<SimulatePacketInfo> simulateSet)
    {
        clientAddress = firstPacket.getAddress();
        clientPort = firstPacket.getPort();
        len = firstPacket.getLength();
        data = firstPacket.getData();
        verbosity = verbose;
        this.simulateSet = simulateSet;
        
        try {
            sendReceiveSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    
	//Decides whether to simulate error or send normally
    
    //this needs logging with different verbosities
	public void errorSimulateSend() 
    {
		for (SimulatePacketInfo check : simulateSet)
        {
			if (check.getPacketType() == TFTPCommon.getPacketType(sendPacket.getData()) && check.getPacketNum() == blockNumToPacket(sendPacket.getData()))
            {
				if (check.getModType() == TFTPCommon.ModificationType.LOSE) 
                {
					losePacket(check);
				} 
                else if (check.getModType() == TFTPCommon.ModificationType.DUPLICATE) 
                {
					duplicatePacket(check);
				} 
                else if (check.getModType() == TFTPCommon.ModificationType.DELAY) 
                {
					delayPacket(check);
				} 
                else 
                {
					System.out.println("Error on " + check.getPacketNum());
				}
				return;
			} 
		}

		System.out.println("Sending packet " + blockNumToPacket(sendPacket.getData()) + " with no error simulation.");

		try {
	        sendReceiveSocket.send(sendPacket);
	    } catch (IOException e) {
	        e.printStackTrace();
	        System.exit(1);
	    }
	}
	 
	//returns packet number
	public static int blockNumToPacket(byte[] data)
    {
	 	Byte a = data[2];
	 	Byte b = data[3];
	 	
        return Byte.toUnsignedInt(a) * 256 + Byte.toUnsignedInt(b);
    }
    
	//Do nothing with packet
    public void losePacket(SimulatePacketInfo check)
    {
    	System.out.println("Lose packet " + check.getPacketNum());
    }
    
    //Send the packet normally, wait a certain amount of time, then send again
    public void duplicatePacket (SimulatePacketInfo check)
    {
        System.out.println("Duplicate packet " + check.getPacketNum());
        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Wait " + check.getDuplicateGap() + " ms");
        
        try {
            Thread.sleep(check.getDuplicateGap());
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }   
    }
    
    
    //Wait a certain amount of time then send packet
    public void delayPacket (SimulatePacketInfo check)
    {
    	System.out.println("Delay packet " + check.getPacketNum() + "by " + check.getDelayAmount() + "ms ");
    	
    	try {
			Thread.sleep(check.getDelayAmount());
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	 
        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }      
    }
    

    public void passOnTFTP()
    {
        int j = 0;

        //TODO might have to move request code to loop (?)
        sendPacket = new DatagramPacket(data, len, clientAddress, 69); //TODO define 69 somewhere

        len = sendPacket.getLength();
        
        if (verbosity != TFTPCommon.Verbosity.NONE)
        {
            System.out.println("Simulator: sending request packet.");
            System.out.println("To host: " + sendPacket.getAddress());
            System.out.println("Destination host port: " + sendPacket.getPort());
            System.out.println("Length: " + len);
            if (verbosity == TFTPCommon.Verbosity.ALL)
            {
                System.out.println("Containing: ");
                for (j = 0; j < len; j++) 
                {
                    System.out.println("byte " + j + " " + data[j]);
                }
            }
        }

        errorSimulateSend(); 
        
        while (true) 
        {
            // receive from server
            data = new byte[516];
            receivePacket = new DatagramPacket(data, data.length);

            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("Simulator: Waiting for packet from server.");
            }

            try {
                sendReceiveSocket.receive(receivePacket);
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            serverPort = receivePacket.getPort();
            len = receivePacket.getLength();

            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("Simulator: Packet received from server:");
                System.out.println("From host: " + receivePacket.getAddress());
                System.out.println("Host port: " + receivePacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPCommon.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) {
                        System.out.println("byte " + j + " " + data[j]);
                    }
                }
            }
            
            // send to client
            sendPacket = new DatagramPacket(data, receivePacket.getLength(),
                               receivePacket.getAddress(), clientPort);

            len = sendPacket.getLength();
            
            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("Simulator: Sending packet to client:");
                System.out.println("To host: " + sendPacket.getAddress());
                System.out.println("Destination host port: " + sendPacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPCommon.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) 
                    {
                       System.out.println("byte " + j + " " + data[j]);
                    }   
                }
            }   

            errorSimulateSend();

            // receive from client
            data = new byte[516];
            receivePacket = new DatagramPacket(data, data.length);

            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("Simulator: Waiting for packet from client.");
            }

            try {
                // Block until a datagram is received via sendReceiveSocket.
                sendReceiveSocket.receive(receivePacket);
            } catch(IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            len = receivePacket.getLength();
            // Process the received datagram.
            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("Simulator: Packet received from client:");
                System.out.println("From host: " + receivePacket.getAddress());
                System.out.println("Host port: " + receivePacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPCommon.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) 
                    {
                        System.out.println("byte " + j + " " + data[j]);
                    }
                }
            }
         
            sendPacket = new DatagramPacket(data, len, receivePacket.getAddress(), serverPort);
        
            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("Simulator: sending packet to server.");
            }

            len = sendPacket.getLength();
            if (verbosity != TFTPCommon.Verbosity.NONE)
            {
                System.out.println("To host: " + sendPacket.getAddress());
                System.out.println("Destination host port: " + sendPacket.getPort());
                System.out.println("Length: " + len);
                if (verbosity == TFTPCommon.Verbosity.ALL)
                {
                    System.out.println("Containing: ");
                    for (j=0;j<len;j++) 
                    {
                        System.out.println("byte " + j + " " + data[j]);
                    }
                }
            }

            // Send to server.
            errorSimulateSend();
         
        } // end of loop
    }

    public void run()
    {
        this.passOnTFTP();
    }
}

class TFTPIntHostCommandLine extends Thread
{
    private TFTPCommon.Verbosity verbosity;
    private TFTPIntHost parentSimulator;
    private Boolean cliRunning;
    private Set<SimulatePacketInfo> nextSet;

    public TFTPIntHostCommandLine(TFTPIntHost parent)
    {
        parentSimulator = parent;
        verbosity = TFTPCommon.Verbosity.NONE;
        cliRunning = true;
        nextSet = new HashSet<SimulatePacketInfo>();
    }

    private void commitNextSet()
    {
        parentSimulator.nextSimulateSet(nextSet);
        nextSet = new HashSet<SimulatePacketInfo>();
    }

    /*** make a common menu method ***/
    private void modifyPacket(Scanner sc)
    {
        //loop "forever", this returns with user input
        while (true)
        {
            String scIn = new String();
            int packetToModify = 0;
            int parsedString = 0;
            int delayAmount = 0;
            TFTPCommon.PacketType packetType = TFTPCommon.PacketType.INVALID;
            TFTPCommon.ModificationType modType = TFTPCommon.ModificationType.NONE;

            System.out.println("Modification Menu");
            System.out.println("delay: Delay packet");
            System.out.println("dup: Duplicate packet");
            System.out.println("lose: Lose packet");
            System.out.println("r: Return to Main Menu");

            while ( modType == TFTPCommon.ModificationType.NONE )
            {
                System.out.print("Enter modification type: ");

                scIn = sc.nextLine();

                if ( scIn.equalsIgnoreCase("delay") )
                {
                    while ( delayAmount == 0 )
                    {
                        System.out.print("Enter delay amount (ms): ");

                        scIn = sc.nextLine();

                        if ( scIn.equalsIgnoreCase("r") )
                        {
                            return;
                        }

                        try {
                            parsedString = Integer.parseInt(scIn);
                        } catch (NumberFormatException e) {
                            System.out.println("Input was not a number, try again.");
                        }

                        if (parsedString < 1 || parsedString > 65535)
                        {
                            System.out.println("Invalid delay, try again.");
                        }
                        else
                        {
                            delayAmount = parsedString;
                        }
                    }
                    modType = TFTPCommon.ModificationType.DELAY;                
                }
                else if ( scIn.equalsIgnoreCase("dup") )
                {
                    modType = TFTPCommon.ModificationType.DUPLICATE;
                }
                else if ( scIn.equalsIgnoreCase("lose") )
                {
                    modType = TFTPCommon.ModificationType.LOSE;
                }
                else if ( scIn.equalsIgnoreCase("r") )
                {
                    return;
                }
                else if ( !scIn.equalsIgnoreCase("") )
                {
                    System.out.println("Invalid option");
                }
            }

            while ( packetType == TFTPCommon.PacketType.INVALID ) 
            {
                System.out.print("What packet type should be modified? (request, data or ack): ");
                scIn = sc.nextLine();

                if ( scIn.equalsIgnoreCase("request") )
                {
                    packetType = TFTPCommon.PacketType.REQUEST;
                    packetToModify = 1;
                }
                else if ( scIn.equalsIgnoreCase("data") )
                {
                    packetType = TFTPCommon.PacketType.DATA;
                }
                else if ( scIn.equalsIgnoreCase("ack") )
                {
                    packetType = TFTPCommon.PacketType.ACK;
                }
                else if ( !scIn.equalsIgnoreCase("") )
                {
                    System.out.println("Invalid packet type, try again.");
                }
            }

            while ( packetToModify == 0 )
            {
                System.out.print("Enter packet # to modify (or r to return): ");

                scIn = sc.nextLine();

                if ( scIn.equalsIgnoreCase("r") )
                {
                    return;
                }

                try {
                    parsedString = Integer.parseInt(scIn);
                } catch (NumberFormatException e) {
                    System.out.println("Input was not a number, try again.");
                }

                if (parsedString < 1 || parsedString > 65535)
                {
                    System.out.println("Invalid packet number, try again.");
                }
                else
                {
                    packetToModify = parsedString;
                }
            }

            if ( modType == TFTPCommon.ModificationType.DELAY )
            {
                nextSet.add(new SimulatePacketInfo(modType, packetToModify, packetType, delayAmount));
            }
            else
            {
                nextSet.add(new SimulatePacketInfo(modType, packetToModify, packetType));
            }
        }
    }

    private void printModifyDetails()
    {
        for (SimulatePacketInfo s : nextSet)
        {
            System.out.println(s.toString());
        }
    }

    /**
    *   CLI for TFTPIntHost, allows user to set verbosity and exit (non-graceful)
    *
    *   @param  none
    *   @return none
    */
    public void commandLine() 
    {
        Scanner sc = new Scanner(System.in);
        String scIn = new String();

        while (cliRunning)
        {
            System.out.println("TFTP Error Simulator");
            System.out.println("c: Commit errors for next client"); //dont call this commit
            System.out.println("m: Modify packet(s)");
            System.out.println("p: List packets that will be modified");
            System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
            System.out.println("q: Quit");
            
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("c") )
            {
                commitNextSet();
            }
            else if ( scIn.equalsIgnoreCase("m") )
            {
                modifyPacket(sc);
            }
            else if ( scIn.equalsIgnoreCase("p") )
            {
                printModifyDetails();
            }
            else if ( scIn.equalsIgnoreCase("v") ) 
            {
                System.out.println("Enter verbosity (none, some, all): ");
                String strVerbosity = sc.nextLine();

                if ( strVerbosity.equalsIgnoreCase("none") ) 
                {   verbosity = TFTPCommon.Verbosity.NONE;
                    parentSimulator.setVerbosity(verbosity);
                }
                else if ( strVerbosity.equalsIgnoreCase("some") ) 
                {   verbosity = TFTPCommon.Verbosity.SOME;
                    parentSimulator.setVerbosity(verbosity);
                }
                else if ( strVerbosity.equalsIgnoreCase("all") )
                {   verbosity = TFTPCommon.Verbosity.ALL;
                    parentSimulator.setVerbosity(verbosity);
                }
                else 
                {
                    System.out.println("Invalid verbosity");
                }
            }
            else if ( scIn.equalsIgnoreCase("q") ) 
            {
                sc.close();
                System.exit(1);
            }
            else if ( scIn.equalsIgnoreCase("") == false ) 
            {
                System.out.println("Invalid option");
            }
        }
    }

    public void run() 
    {
        this.commandLine();
    }
}

//Packet type, packet number, and error to simulate stored
class SimulatePacketInfo 
{
	private TFTPCommon.PacketType pType;
	private int pNum, delayAmount, duplicateGap;
	private TFTPCommon.ModificationType loseDuplicateDelay;

	public SimulatePacketInfo (TFTPCommon.ModificationType loseDuplicateDelay, int pNum, TFTPCommon.PacketType pType) 
    {
		this.pType = pType;
		this.pNum = pNum;
        this.loseDuplicateDelay = loseDuplicateDelay;
	}

    public SimulatePacketInfo (TFTPCommon.ModificationType loseDuplicateDelay, int pNum, TFTPCommon.PacketType pType, int amount)
    {
        this(loseDuplicateDelay, pNum, pType);

        if ( loseDuplicateDelay == TFTPCommon.ModificationType.DELAY )
        {
            delayAmount = amount;
        }
        else if ( loseDuplicateDelay == TFTPCommon.ModificationType.DUPLICATE )
        {
            duplicateGap = amount;
        }
    }

    public TFTPCommon.PacketType getPacketType()
    {
        return pType;
    }

    public int getPacketNum()
    {
        return pNum;
    }

    public TFTPCommon.ModificationType getModType()
    {
        return loseDuplicateDelay;
    }

    public int getDelayAmount()
    {
        return delayAmount;
    }

    public int getDuplicateGap()
    {
        return duplicateGap;
    }

    public String toString() 
    {
        return ("Packet: " + TFTPCommon.packetTypeToString(pType) + " " + pNum + " Error Type: " + TFTPCommon.errorSimulateToString(loseDuplicateDelay));
    }
}
