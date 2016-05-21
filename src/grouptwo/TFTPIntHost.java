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
 * @author        Cyrus Sadeghi
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
    
	private void errorSimulateSend() 
    {   
        for (SimulatePacketInfo check : simulateList)
        {   
            if ( TFTPCommon.getPacketType(receivePacket.getData()) == check.getPacketType() && ( (check.getPacketType() == TFTPCommon.PacketType.REQUEST) || (check.getPacketNum() == TFTPCommon.blockNumToPacket(receivePacket.getData()) ) ) )
            {
    			if (check.getModType() == TFTPCommon.ModificationType.LOSE) 
                {
    				losePacket(check);
                    simulateList.remove(check);
                    
                    //TODO - need to fix below for new menu system - need to test multiple clients

                    //If we lose a request, the client will send another request which will spawn a new error sim thread
                    //The new thread won't have the list of modifications, so pass those now
                    /* if (check.getPacketType() == TFTPCommon.PacketType.REQUEST)
                    {
                        for (SimulatePacketInfo prop : simulateList)
                        {
                            parent.appendMod(prop);
                        }
                    } */
    			} 
                else if (check.getModType() == TFTPCommon.ModificationType.DUPLICATE || check.getModType() == TFTPCommon.ModificationType.DELAY) 
                {
                    Thread delayDuplicateThread = new Thread(new DelayDuplicatePacket(sendPacket, sendReceiveSocket, check.getModType(), check.getDelayDuplicateGap()));
    				delayDuplicateThread.start();
                    simulateList.remove(check);
    			}

                else if (check.getModType() == TFTPCommon.ModificationType.CONTENTS)
                {
                    modifyContents(check);
                    simulateList.remove(check);
                }

                else if (check.getModType() == TFTPCommon.ModificationType.INVALIDTID)
                {
                    invalidForward(check);
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
    private void losePacket (SimulatePacketInfo check)
    {
        System.out.println("Lose " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()));
    }

    private void modifyContents (SimulatePacketInfo check)
    {
        data = sendPacket.getData();

        for (ModifyByte mod : check.getModByteList())
        {
            System.out.println("Changing byte " + mod.getPosition() + " of " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " from " + data[mod.getPosition()] + " to " + mod.getValue());
            data[mod.getPosition()] = mod.getValue();
        }

        sendPacket.setData(data);
    }

    private void invalidForward (SimulatePacketInfo check)
    {
        //Temporarily set to sendReceiveSocket in order to avoid
        //initialization compiler warnings
        DatagramSocket invalidSocket = sendReceiveSocket;

        try {
            invalidSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Sending " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " on new socket (port " + invalidSocket.getPort() + ") to simulate invalid TID");

        try {
            invalidSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void passOnTFTP()
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
    private String scIn;

    public TFTPIntHostCommandLine(TFTPIntHost parent)
    {
        parentSimulator = parent;
        verbosity = TFTPCommon.Verbosity.NONE;
        cliRunning = true;
        scIn = new String();
    }

    private int getIntMenu (Scanner sc, int lowLimit, int highLimit, String promptMessage)
    {
        int parsedString = -1;

        while ( true )
        {
            System.out.print(promptMessage);

            scIn = sc.nextLine();

            try {
                parsedString = Integer.parseInt(scIn);
            } catch (NumberFormatException e) {
                System.out.println("Input was not a number, try again.");
            }

            if (parsedString < lowLimit || parsedString > highLimit)
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
        System.out.println("This is not yet implemented");
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

        pNum = getIntMenu(sc, 0, 65535, "Enter packet number: ");

        return new SimulatePacketInfo(pNum, pType);
    }

    private void modifyContents(Scanner sc)
    {
        SimulatePacketInfo dataModPacket = selectPacket(sc);
        int opcode = -1;
        int blockNum = -1;
        List<Integer> positions = new ArrayList<Integer>();

        dataModPacket.setModType(TFTPCommon.ModificationType.CONTENTS);

        System.out.println("Packet Content Modifications");
        System.out.println("----------------------------");
        System.out.println("opcode: Modify opcode");
        
        if (dataModPacket.getPacketType() == TFTPCommon.PacketType.REQUEST)
        {
            System.out.println("fname: Modify file name");
            System.out.println("mode: Modify file mode");
        }
        else
        {
            System.out.println("block: Modify block number");
        }

        System.out.println("manual: Manually modify packet contents");

        scIn = sc.nextLine();

        if ( scIn.equalsIgnoreCase("opcode") )
        {
            opcode = getIntMenu(sc, 0, 512, "Enter new opcode (as bytes, i.e. 02, 03, etc.): ");
            dataModPacket.setOpcode(opcode);
        }
        
        if (dataModPacket.getPacketType() == TFTPCommon.PacketType.REQUEST)
        {
            if ( scIn.equalsIgnoreCase("fname") )
            {
                System.out.print("Enter new file name: ");
                dataModPacket.setFileName(sc.nextLine());
            }
            else if ( scIn.equalsIgnoreCase("mode") )
            {
                System.out.print("Enter new file mode: ");
                dataModPacket.setFileMode(sc.nextLine());
            }
        }

        else if ( scIn.equalsIgnoreCase("block") )
        {
            blockNum = getIntMenu(sc, 0, 512, "Enter new block number (as integer): ");
            dataModPacket.setBlockNum(blockNum);
        }

        if ( scIn.equalsIgnoreCase("manual") )
        {
            while (true)
            {
                Integer position = getIntMenu(sc, 0, 516, "Enter byte position to modify: ");
                Integer value = getIntMenu(sc, 0, 255, "Enter new value for byte: ");

                ModifyByte modByte = new ModifyByte(position, value.byteValue());
                dataModPacket.addModByte(modByte);
            }
        }

        if (!parentSimulator.appendMod(dataModPacket))
        {
            System.out.println("Failed to add modification!");
        }
    }

    private void delayPacket(Scanner sc)
    {
        SimulatePacketInfo delayPacket = selectPacket(sc);
        int delayAmount = getIntMenu(sc, 0, 100000, "Enter amount to delay packet (ms): ");
        
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
        int duplicateGap = getIntMenu(sc, 0, 100000, "Enter gap between duplicated packets (ms): ");
        
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

    private void invalidTID(Scanner sc)
    {
        SimulatePacketInfo invalidTID = selectPacket(sc);

        invalidTID.setModType(TFTPCommon.ModificationType.INVALIDTID);

        if (!parentSimulator.appendMod(invalidTID))
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

        while (cliRunning)
        {
            System.out.println("TFTP Error Simulator");
            System.out.println("--------------------");
            System.out.println("contents: Modify packet contents");
            System.out.println("delay: Delay packet");
            System.out.println("dup: Duplicate packet");
            System.out.println("lose: Lose packet");
            System.out.println("tid: Send packet with invalid TID");
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
            else if ( scIn.equalsIgnoreCase("tid") )
            {
                invalidTID(sc);
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
	private int pNum, delayDuplicateGap, opcode, blockNum;
    private String fileName, fileMode;
	private TFTPCommon.ModificationType modType;
    private List<ModifyByte> modByte;

    public SimulatePacketInfo (int pNum, TFTPCommon.PacketType pType)
    {
        this.pNum = pNum;
        this.pType = pType;
        modByte = new ArrayList<ModifyByte>();
    }

    public TFTPCommon.PacketType getPacketType ()
    {
        return pType;
    }

    public int getPacketNum ()
    {
        return pNum;
    }

    public TFTPCommon.ModificationType getModType ()
    {
        return modType;
    }

    public int getDelayDuplicateGap ()
    {
        return delayDuplicateGap;
    }

    public List<ModifyByte> getModByteList ()
    {
        return modByte;
    }

    public void setModType (TFTPCommon.ModificationType modType)
    {
        this.modType = modType;
    }

    public void setDelayDuplicateGap (int delayDuplicateGap)
    {
        this.delayDuplicateGap = delayDuplicateGap;
    }

    public void setOpcode (int opcode)
    {
        this.opcode = opcode;
        modByte.add(new ModifyByte(0, (byte) (opcode / 10)));
        modByte.add(new ModifyByte(1, (byte) (opcode % 10)));
    }

    public void setFileName (String fileName)
    {
        this.fileName = fileName;
    }

    public void setFileMode (String fileMode)
    {
        this.fileMode = fileMode;
    }

    public void setBlockNum (int blockNum)
    {
        this.blockNum = blockNum;
        modByte.add(new ModifyByte(2, (byte) (blockNum / 256)));
        modByte.add(new ModifyByte(3, (byte) (blockNum % 256)));
    }

    public void addModByte (ModifyByte modByte)
    {
        this.modByte.add(modByte);
    }

    public String toString () 
    {
        String returnString = new String ("Packet: " + TFTPCommon.packetTypeToString(pType) + " " + pNum + " Error Type: " + TFTPCommon.errorSimulateToString(modType));
        
        if (getModType() == TFTPCommon.ModificationType.DELAY)
        {
            returnString = returnString + " Delay Amount: " + getDelayDuplicateGap() + " ms";
        }

        else if (getModType() == TFTPCommon.ModificationType.DUPLICATE)
        {
            returnString = returnString + " Duplicate Gap: " + getDelayDuplicateGap() + " ms";
        }

        else if (getModType() == TFTPCommon.ModificationType.CONTENTS)
        {

            for (ModifyByte m : modByte)
            {
                returnString = returnString + System.lineSeparator() + "Packet Contents: " + m.toString();
            }
        }

        return returnString;
    }
}

class ModifyByte
{
    private Integer position;
    private byte value;

    public ModifyByte (Integer position, byte value)
    {
        this.position = position;
        this.value = value;
    }

    public Integer getPosition ()
    {
        return position;
    }

    public byte getValue ()
    {
        return value;
    }

    public String toString ()
    {
       return (new String ("Change value of byte " + position + " to " + Byte.toUnsignedInt(value)) + " (unsigned int value)");
    }
}
