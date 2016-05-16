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
    private List<SimulatePacketInfo> toModify;

    public TFTPIntHost()
    {
        try {
            receiveSocket = new DatagramSocket(TFTPCommon.TFTPErrorSimPort);
        } catch (SocketException se) {
            se.printStackTrace();
            System.exit(1);
        }

        verbosity = TFTPCommon.Verbosity.NONE;
        toModify = new ArrayList<SimulatePacketInfo>();
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

    public void nextSimulateList(List<SimulatePacketInfo> nextList, Boolean quiet)
    {
        toModify.addAll(nextList);

        if (!quiet)
        {
            System.out.println("Packet modifications commited");
        }
    }

    public void printSimulateList()
    {
        for (SimulatePacketInfo s : toModify)
        {
            System.out.println(s);
        }
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

            Thread client = new Thread(new ErrorSimulator(receivePacket, verbosity, toModify, this));
            client.start();
            toModify = new ArrayList<SimulatePacketInfo>();
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
    private SimulatePacketInfo simCompare;
    private List<SimulatePacketInfo> simulateList;
    private TFTPIntHost parent;

    public ErrorSimulator(DatagramPacket firstPacket, TFTPCommon.Verbosity verbose, List<SimulatePacketInfo> simulateList, TFTPIntHost intHost)
    {
        clientAddress = firstPacket.getAddress();
        clientPort = firstPacket.getPort();
        serverPort = 0;
        len = firstPacket.getLength();
        data = firstPacket.getData();
        receivePacket = firstPacket;
        verbosity = verbose;
        this.simulateList = simulateList;
        parent = intHost;
        
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
        for (SimulatePacketInfo check : simulateList)
        {   
            if ( TFTPCommon.getPacketType(receivePacket.getData()) == check.getPacketType() && ( (check.getPacketType() == TFTPCommon.PacketType.REQUEST) || (check.getPacketNum() == TFTPCommon.blockNumToPacket(receivePacket.getData()) ) ) )
            {
    			if (check.getModType() == TFTPCommon.ModificationType.LOSE) 
                {
    				losePacket(check);
                    simulateList.remove(check);
                    //If we lose a request, the client will send another request which will spawn a new error sim thread
                    //The new thread won't have the list of modifications, so pass those now
                    if (check.getPacketType() == TFTPCommon.PacketType.REQUEST)
                    {
                        parent.nextSimulateList(simulateList, true);
                    }
    			} 
                else if (check.getModType() == TFTPCommon.ModificationType.DUPLICATE) 
                {
    				duplicatePacket(check);
                    simulateList.remove(check);
    			} 
                else if (check.getModType() == TFTPCommon.ModificationType.DELAY) 
                {
    				delayPacket(check);
                    simulateList.remove(check);
    			} 
                else 
                {
    				System.out.println("Error on " + check.getPacketNum());
    			}
    			return;
            }
        }

		System.out.println("Sending " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " with no error simulation.");

		try {
	        sendReceiveSocket.send(sendPacket);
	    } catch (IOException e) {
	        e.printStackTrace();
	        System.exit(1);
	    }
	}

	//Do nothing with packet
    public void losePacket (SimulatePacketInfo check)
    {
        System.out.println("Lose " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()));
    }
    
    //Send the packet normally, wait a certain amount of time, then send again
    public void duplicatePacket (SimulatePacketInfo check)
    {
        System.out.println("Duplicate " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()));
        
        try {
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Wait " + check.getDuplicateGap() + " ms");
        
        try {
            Thread.sleep(check.getDuplicateGap());  //we can't use sleep (probably), error sim will be asleep when first packet gets a response
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
    	System.out.println("Delay " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " by " + check.getDelayAmount() + "ms ");
    	
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
        int port = TFTPCommon.TFTPListenPort;

        while (true) 
        {
            //Assume that server and client are on same machine by using clientAddress for both
            //Will have to be changed for Iteration 5
            sendPacket = new DatagramPacket(data, len, clientAddress, port);
            len = sendPacket.getLength();
            TFTPCommon.printPacketDetails(sendPacket, verbosity, false);

            errorSimulateSend();

            data = new byte[516];
                
            receivePacket = new DatagramPacket(data, data.length);

            try {
                sendReceiveSocket.receive(receivePacket);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            //Set server port if receivePacket is the first packet from the server
            if (serverPort == 0 && receivePacket.getPort() != clientPort)
            {
                serverPort = receivePacket.getPort();
            }

            //Setup variables to forward packet
            if (receivePacket.getPort() == serverPort)
            {
                port = clientPort;
            }
            else if (receivePacket.getPort() == clientPort)
            {
                port = serverPort;
            }
            else
            {
                System.out.println("Received packet from unknown port! This can happen with a delayed or duplicated request");
            }

            data = receivePacket.getData();
            len = receivePacket.getLength();
        }
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
    private List<SimulatePacketInfo> nextList;

    public TFTPIntHostCommandLine(TFTPIntHost parent)
    {
        parentSimulator = parent;
        verbosity = TFTPCommon.Verbosity.NONE;
        cliRunning = true;
        nextList = new ArrayList<SimulatePacketInfo>();
    }

    private void commitNextList()
    {
        parentSimulator.nextSimulateList(nextList, false);
        nextList = new ArrayList<SimulatePacketInfo>();
    }

    private int getIntMenu (Scanner sc, String promptMessage)
    {
        int parsedString = -1;
        String scIn = new String();

        while ( true )
        {
            System.out.print(promptMessage);

            scIn = sc.nextLine();

            try {
                parsedString = Integer.parseInt(scIn);
            } catch (NumberFormatException e) {
                System.out.println("Input was not a number, try again.");
            }

            if (parsedString < 0 || parsedString > 65535)
            {
                System.out.println("Invalid delay, try again.");
            }
            else
            {
                return parsedString;
            }
        }
    }

    /*** make a common menu method ***/
    private void modifyPacket (Scanner sc)
    {
        //loop "forever", this returns with user input
        while (true)
        {
            String scIn = new String();
            int packetToModify = 0;
            int delayAmount = 0;
            TFTPCommon.PacketType packetType = TFTPCommon.PacketType.INVALID;
            TFTPCommon.ModificationType modType = TFTPCommon.ModificationType.NONE;

            System.out.println("Modification Menu");
            System.out.println("delay: Delay packet");
            System.out.println("dup: Duplicate packet");
            System.out.println("lose: Lose packet");
            System.out.println("p: Print pending modifications");
            System.out.println("r: Return to Main Menu");

            while ( modType == TFTPCommon.ModificationType.NONE )
            {
                System.out.print("Enter modification type: ");

                scIn = sc.nextLine();

                if ( scIn.equalsIgnoreCase("delay") )
                {
                    delayAmount = getIntMenu(sc, "Enter amount to delay packet (ms): ");
                    modType = TFTPCommon.ModificationType.DELAY;                
                }
                else if ( scIn.equalsIgnoreCase("dup") )
                {
                    delayAmount = getIntMenu(sc, "Enter amount of time to wait between duplicated packets (ms): ");
                    modType = TFTPCommon.ModificationType.DUPLICATE;
                }
                else if ( scIn.equalsIgnoreCase("lose") )
                {
                    modType = TFTPCommon.ModificationType.LOSE;
                }
                else if ( scIn.equalsIgnoreCase("p") )
                {
                    printModifyDetails();
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
                System.out.print("What packet type should be modified? (request, data, ack, or r to return): ");
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
                else if ( scIn.equalsIgnoreCase("r") )
                {
                    return;
                }
                else if ( !scIn.equalsIgnoreCase("") )
                {
                    System.out.println("Invalid packet type, try again.");
                }
            }

            if (packetType != TFTPCommon.PacketType.REQUEST)
            {
                packetToModify = getIntMenu(sc, "Enter packet number: ");
            }

            if ( modType == TFTPCommon.ModificationType.DELAY || modType == TFTPCommon.ModificationType.DUPLICATE )
            {
                nextList.add(new SimulatePacketInfo(modType, packetToModify, packetType, delayAmount));
            }
            else
            {
                nextList.add(new SimulatePacketInfo(modType, packetToModify, packetType));
            }
        }
    }

    private void printModifyDetails()
    {
        for (SimulatePacketInfo s : nextList)
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
            //need remove method to cancel commited mod
            System.out.println("TFTP Error Simulator");
            System.out.println("--------------------");
            System.out.println("c: Commit modifications for next client");
            System.out.println("m: Modify packet(s)");
            System.out.println("p: Print commited modifications");
            System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
            System.out.println("q: Quit");
            
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("c") )
            {
                commitNextList();
            }
            else if ( scIn.equalsIgnoreCase("m") )
            {
                modifyPacket(sc);
            }
            else if ( scIn.equalsIgnoreCase("p") )
            {
                parentSimulator.printSimulateList();
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
        String returnString = new String ("Packet: " + TFTPCommon.packetTypeToString(pType) + " " + pNum + " Error Type: " + TFTPCommon.errorSimulateToString(loseDuplicateDelay));
        
        if (getModType() == TFTPCommon.ModificationType.DELAY)
        {
            returnString = returnString + " Delay Amount: " + getDelayAmount() + " ms";
        }

        if (getModType() == TFTPCommon.ModificationType.DUPLICATE)
        {
            returnString = returnString + " Duplicate Gap: " + getDuplicateGap() + " ms";
        }

        return returnString;
    }
}
