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

    public Boolean appendMod(SimulatePacketInfo modification)
    {
        return toModify.add(modification);
    }

    public Boolean removeMod(SimulatePacketInfo toRemove)
    {
        return toModify.remove(toRemove);
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
                    
                    //TODO - need to fix below for new menu system

                    //If we lose a request, the client will send another request which will spawn a new error sim thread
                    //The new thread won't have the list of modifications, so pass those now
                    /*if (check.getPacketType() == TFTPCommon.PacketType.REQUEST)
                    {
                        parent.nextSimulateList(simulateList, true);
                    }*/
    			} 
                else if (check.getModType() == TFTPCommon.ModificationType.DUPLICATE || check.getModType() == TFTPCommon.ModificationType.DELAY) 
                {
                    Thread delayDuplicateThread = new Thread(new DelayDuplicatePacket(sendPacket, sendReceiveSocket, check.getModType(), check.getDelayDuplicateGap()));
    				delayDuplicateThread.start();
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
    private String scIn;

    public TFTPIntHostCommandLine(TFTPIntHost parent)
    {
        parentSimulator = parent;
        verbosity = TFTPCommon.Verbosity.NONE;
        cliRunning = true;
        nextList = new ArrayList<SimulatePacketInfo>();
        scIn = new String();
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
                System.out.println("Input out of range, try again.");
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

    private SimulatePacketInfo selectPacket(Scanner sc)
    {
        TFTPCommon.PacketType pType = TFTPCommon.PacketType.INVALID;
        int pNum = -1;

        while (pType == TFTPCommon.PacketType.INVALID)
        {
            System.out.print("What packet type should be modified? (request, data, ack, error, or r to return): ");
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("request") )
            {
                pType = TFTPCommon.PacketType.REQUEST;
                pNum = 1;

                return new SimulatePacketInfo(pNum, pType);
            }
            else if ( scIn.equalsIgnoreCase("data") )
            {
                pType = TFTPCommon.PacketType.DATA;
            }
            else if ( scIn.equalsIgnoreCase("ack") )
            {
                pType = TFTPCommon.PacketType.ACK;
            }
            else if ( scIn.equalsIgnoreCase("error") )
            {
                pType = TFTPCommon.PacketType.ERROR;
            }
            else if ( scIn.equalsIgnoreCase("r") )
            {
                return null;        //can't do this, need another way of cancelling mod creation
            }
            else if ( !scIn.equalsIgnoreCase("") )
            {
                System.out.println("Invalid packet type, try again.");
            }
        }

        pNum = getIntMenu(sc, "Enter packet number: ");

        return new SimulatePacketInfo(pNum, pType);
    }

    private void modifyContents(Scanner sc)
    {
        System.out.println("This isn't implemented yet");
    }

    private void delayPacket(Scanner sc)
    {
        SimulatePacketInfo delayPacket = selectPacket(sc);
        int delayAmount = getIntMenu(sc, "Enter amount to delay packet (ms): ");
        
        delayPacket.setModType(TFTPCommon.ModificationType.DELAY);
        delayPacket.setDelayDuplicateGap(delayAmount);

        if (!parentSimulator.appendMod(delayPacket))
        {
            System.out.println("Failed to add modification!");
        }
    }

    private void duplicatePacket(Scanner sc)
    {
        SimulatePacketInfo duplicatePacket = selectPacket(sc);
        int duplicateGap = getIntMenu(sc, "Enter gap between duplicated packets (ms): ");
        
        duplicatePacket.setModType(TFTPCommon.ModificationType.DUPLICATE);
        duplicatePacket.setDelayDuplicateGap(duplicateGap);

        if (!parentSimulator.appendMod(duplicatePacket))
        {
            System.out.println("Failed to add modification!");
        }
    }

    private void losePacket(Scanner sc)
    {
        SimulatePacketInfo losePacket = selectPacket(sc);
        
        losePacket.setModType(TFTPCommon.ModificationType.LOSE);
        
        if (!parentSimulator.appendMod(losePacket))
        {
            System.out.println("Failed to add modification!");
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
            System.out.println("--------------------");
            System.out.println("contents: Modify packet contents");
            System.out.println("delay: Delay packet");
            System.out.println("dup: Duplicate packet");
            System.out.println("lose: Lose packet");
            System.out.println("c: Cancel pending modification");
            System.out.println("p: Print modifications");
            System.out.println("v: Set verbosity (current: " + TFTPCommon.verbosityToString(verbosity) + ")");
            System.out.println("q: Quit");
            
            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("contents") )
            {
                modifyContents(sc);
            }
            else if ( scIn.equalsIgnoreCase("delay") )
            {
                delayPacket(sc);              
            }
            else if ( scIn.equalsIgnoreCase("dup") )
            {
                duplicatePacket(sc);
            }
            else if ( scIn.equalsIgnoreCase("lose") )
            {
                losePacket(sc);
            }
            else if ( scIn.equalsIgnoreCase("c") )
            {
                deletePendingMod(sc);
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
 * @author        Cyrus Sadeghi
 */
class SimulatePacketInfo 
{
	private TFTPCommon.PacketType pType;
	private int pNum, delayDuplicateGap;
	private TFTPCommon.ModificationType modType;
    private int[] positions;
    private byte[] newData;

    //Constructor used by CLI to initialize basic details (also called by below constructor)
    public SimulatePacketInfo (int pNum, TFTPCommon.PacketType pType)
    {
        this.pNum = pNum;
        this.pType = pType;
    }

    //Constructor for losing packet (also called by below constructors)
	public SimulatePacketInfo (TFTPCommon.ModificationType modType, int pNum, TFTPCommon.PacketType pType) 
    {
		this(pNum, pType);
        this.modType = modType;
	}

    //Constructor for delaying or duplicating packet
    public SimulatePacketInfo (TFTPCommon.ModificationType modType, int pNum, TFTPCommon.PacketType pType, int amount)
    {
        this(modType, pNum, pType);
        delayDuplicateGap = amount;
    }

    //Constructor for modifying packet contents
    public SimulatePacketInfo (TFTPCommon.ModificationType modType, int pNum, TFTPCommon.PacketType pType, byte[] newData, int[] positions)
    {
        this(modType, pNum, pType);
        this.positions = positions;
        this.newData = newData;
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
        return modType;
    }

    public int getDelayDuplicateGap()
    {
        return delayDuplicateGap;
    }

    public void setModType(TFTPCommon.ModificationType modType)
    {
        this.modType = modType;
    }

    public void setDelayDuplicateGap(int delayDuplicateGap)
    {
        this.delayDuplicateGap = delayDuplicateGap;
    }

    //datamod to string method needed?

    public String toString() 
    {
        String returnString = new String ("Packet: " + TFTPCommon.packetTypeToString(pType) + " " + pNum + " Error Type: " + TFTPCommon.errorSimulateToString(modType));
        
        if (getModType() == TFTPCommon.ModificationType.DELAY)
        {
            returnString = returnString + " Delay Amount: " + getDelayDuplicateGap() + " ms";
        }

        if (getModType() == TFTPCommon.ModificationType.DUPLICATE)
        {
            returnString = returnString + " Duplicate Gap: " + getDelayDuplicateGap() + " ms";
        }

        return returnString;
    }
}
