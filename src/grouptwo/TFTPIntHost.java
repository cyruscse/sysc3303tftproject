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

/**
 * TFTPIntHost spawns the CLI thread and a new ErrorSimulator thread for each
 * new client. Its behaviour is similar to TFTPServer in that the CLI thread
 * uses set methods in this class to create a list of modifications for the next
 * client thread. 
 *
 * @author        Cyrus Sadeghi
 */
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

/**
 * ErrorSimulator handles modifiying packets, each client connection gets an ErrorSimulator thread spawned
 * by TFTPIntHost. ErrorSimulator can delay, duplicate, lose or not modify packets that received from the server
 * or client.
 *
 * @author        Majeed Mirza
 */
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
                    Thread duplicateThread = new Thread(new DelayDuplicatePacket(sendPacket, sendReceiveSocket, check.getModType(), check.getDuplicateGap()));
    				duplicateThread.start();
                    //duplicatePacket(check);
                    simulateList.remove(check);
    			} 
                else if (check.getModType() == TFTPCommon.ModificationType.DELAY) 
                {
                    Thread delayThread = new Thread(new DelayDuplicatePacket(sendPacket, sendReceiveSocket, check.getModType(), check.getDelayAmount()));
    		  		delayThread.start();
                    //delayPacket(check);
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

/**
 * DelayDuplicatePacket threads are spawned everytime a delay or duplicate needs to occur.
 * A new thread is required as delaying or duplicating packets causes the thread to go to sleep,
 * which would block any incoming packets from arriving.
 *
 * @author        Cyrus Sadeghi
 */
class DelayDuplicatePacket extends Thread
{
    private TFTPCommon.Verbosity verbosity;
    private DatagramPacket send;
    private DatagramSocket socket;
    private TFTPCommon.ModificationType modType;
    private Integer delayAmount;

    public DelayDuplicatePacket(DatagramPacket sendPacket, DatagramSocket sendReceiveSocket, TFTPCommon.ModificationType mod, Integer delayDuplicateAmount)
    {
        if (mod != TFTPCommon.ModificationType.DUPLICATE && mod != TFTPCommon.ModificationType.DELAY)
        {
            System.out.println("Invalid modification!");
            return;
        }

        send = sendPacket;
        socket =  sendReceiveSocket;
        delayAmount = delayDuplicateAmount;
        modType = mod;
    }

    //Send the packet normally, wait a certain amount of time, then send again
    public void duplicatePacket ()
    {
        System.out.println("Duplicate " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending first instance");
        
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Waiting " + delayAmount + " ms");
        
        try {
            Thread.sleep(delayAmount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Duplicate " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending second instance");
        
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }   
    }
    
    
    //Wait a certain amount of time then send packet
    public void delayPacket ()
    {
        System.out.println("Delay " + TFTPCommon.packetTypeAndNumber(send.getData()) + " by " + delayAmount + "ms");
        
        try {
            Thread.sleep(delayAmount);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }
         
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } 
    }

    public void run()
    {
        if (modType == TFTPCommon.ModificationType.DUPLICATE)
        {
            duplicatePacket();
        }
        else if (modType == TFTPCommon.ModificationType.DELAY)
        {
            delayPacket();
        }
    }
}

/**
 * TFTPIntHostCommandLine is the command line for error simulator. It allows you to select packets to modify
 * and the type of modification (delaying, duplicating or losing) to apply to the packet. Multiple packets
 * can be modified for each client connection and modifications can be cancelled before they occur
 *
 * @author        Cyrus Sadeghi
 */
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

    private void deletePendingMod (Scanner sc)
    {

        String scIn;
        int modToDelete;

        while ( true )
        {
            if (nextList.size() == 0)
            {
                return;
            }

            scIn = new String();
            modToDelete = 0;

            System.out.println("List of Pending Modifications");

            for (int i = 0; i < nextList.size(); i++)
            {
                System.out.println((i + 1) + ". " + nextList.get(i));
            }

            while (modToDelete <= 0)
            {
                System.out.print("Please enter a number (or r to return): ");

                scIn = sc.nextLine();

                if (scIn.equalsIgnoreCase("r"))
                {
                    return;
                }
                else
                {
                    try {
                        modToDelete = Integer.parseInt(scIn);
                    } catch (NumberFormatException e) {
                        System.out.println("Input wasn't a number, try again.");
                    }
                }
            }

            modToDelete--;

            if (modToDelete <= nextList.size())
            {
                nextList.remove(modToDelete);
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
            System.out.println("d: Delete pending modification");
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
                else if ( scIn.equalsIgnoreCase("d") )
                {
                    deletePendingMod(sc);
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

/**
 * SimulatePacketInfo contains information of a packet modification, including packet number,
 * type, modification type and modification details
 *
 * @author        Majeed Mirza
 */
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
