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

    public void removeMod(int toRemove) throws IndexOutOfBoundsException
    {
        toModify.remove(toRemove);
    }

    public int simulateListLength()
    {
        return toModify.size();
    }

    public String simulateListToString()
    {
        String returnString = new String();

        for (SimulatePacketInfo s : toModify)
        {
            returnString = returnString + s + System.lineSeparator();
        }

        return returnString;
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
                receiveSocket.receive(receivePacket);
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
    private DatagramSocket sendReceiveSocket, invalidTIDSocket;
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
            invalidTIDSocket = new DatagramSocket();
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
                    
                    //TODO - need to test losing request packets (as well as multiple clients)

                    //If we lose a request, the client will send another request which will spawn a new error sim thread
                    //The new thread won't have the list of modifications, so pass those now
                    if (check.getPacketType() == TFTPCommon.PacketType.REQUEST)
                    {
                        for (SimulatePacketInfo prop : simulateList)
                        {
                            parent.appendMod(prop);
                        }
                    }
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
                    Thread delayDuplicateThread = new Thread(new DelayDuplicatePacket(sendPacket, invalidTIDSocket, check.getModType(), 0));
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
        TFTPCommon.printPacketDetails(sendPacket, verbosity, false);

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
        TFTPCommon.printPacketDetails(sendPacket, verbosity, false);
    }

    private void modifyContents (SimulatePacketInfo check)
    {
        data = sendPacket.getData();
        int len = sendPacket.getLength();
        String packetType = TFTPCommon.packetTypeAndNumber(sendPacket.getData());

        if (check.getContentModType() == TFTPCommon.ContentSubmod.FILENAME || check.getContentModType() == TFTPCommon.ContentSubmod.FILEMODE)
        {
            String fileName, fileMode;
            int opcode, j, k;

            for (j = 2; j < sendPacket.getLength(); j++) 
            {
                if (data[j] == 0) 
                {
                    break;
                }
            }

            fileName = new String(data, 2, j - 2);
            
            for (k = j + 1; k < sendPacket.getLength(); k++)
            {
                if (data[k] == 0)
                {
                    break;
                }
            }

            fileMode = new String(data, j + 1, k - j - 1);
           
            opcode = Byte.toUnsignedInt(data[0]) * 256 + Byte.toUnsignedInt(data[1]);

            if (check.getFileName().length() > 0)
            {
                System.out.println("Changing file name in request from " + fileName + " to " + check.getFileName());
                fileName = check.getFileName();
            }

            if (check.getFileMode().length() > 0)
            {
                System.out.println("Changing file mode in request from " + fileMode + " to " + check.getFileMode());
                fileMode = check.getFileMode();
            }

            data = new byte[100];
            len = TFTPCommon.constructReqPacket(data, opcode, fileName, fileMode);
            
            sendPacket.setData(data);
            sendPacket.setLength(len);
        }
        
        if (check.getContentModType() == TFTPCommon.ContentSubmod.LENGTH)
        {
            data = sendPacket.getData();

            System.out.println("Changing " + TFTPCommon.packetTypeAndNumber(sendPacket.getData()) + " length from " + sendPacket.getLength() + " to " + check.getLength());

            //Packet's length is greater than new length, truncate packet
            if (sendPacket.getLength() > check.getLength())
            {
                System.arraycopy(data, 0, data, 0, check.getLength());
                sendPacket.setLength(check.getLength());
                sendPacket.setData(data);
            }

            //Packet's length is less than new length, pad with 0s
            else
            {
                byte[] paddedData = new byte[check.getLength()];
                System.arraycopy(data, 0, paddedData, 0, sendPacket.getLength());
                sendPacket.setLength(check.getLength());
                sendPacket.setData(paddedData);
            }
        }

        for (ModifyByte mod : check.getModByteList())
        {
            if (mod.getPosition() < data.length)
            {
                System.out.println("Changing byte " + mod.getPosition() + " of " + packetType + " from " + data[mod.getPosition()] + " to " + mod.getValue());
                data[mod.getPosition()] = mod.getValue();
            }
            else
            {
                System.out.println("Ignoring content modification: byte # " + mod.getPosition() + " value " + mod.getValue() + " as modification is out of bounds for this packet");
            }
        }

        sendPacket.setData(data, 0, len);

        TFTPCommon.printPacketDetails(sendPacket, verbosity, false);

        try {
            sendReceiveSocket.send(sendPacket);
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
    private DatagramPacket send, receive;
    private DatagramSocket socket;
    private TFTPCommon.ModificationType modType;
    private Integer delayAmount;
    private byte[] data;

    public DelayDuplicatePacket(DatagramPacket sendPacket, DatagramSocket sendReceiveSocket, TFTPCommon.ModificationType mod, Integer delayDuplicateAmount)
    {
        if (mod != TFTPCommon.ModificationType.DUPLICATE && mod != TFTPCommon.ModificationType.DELAY && mod != TFTPCommon.ModificationType.INVALIDTID)
        {
            System.out.println("Invalid modification!");
            return;
        }

        send = sendPacket;
        data = new byte[516];
        receive = new DatagramPacket(data, data.length);
        socket =  sendReceiveSocket;
        delayAmount = delayDuplicateAmount;
        modType = mod;
    }

    //Send the packet normally, wait a certain amount of time, then send again
    public void duplicatePacket ()
    {
        System.out.println("Duplicate " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending first instance");
        
        TFTPCommon.printPacketDetails(send, verbosity, false);
        
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
        
        TFTPCommon.printPacketDetails(send, verbosity, false);

        System.out.println("Waiting " + delayAmount + " ms");

        try {
            Thread.sleep(delayAmount);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Delayed " + TFTPCommon.packetTypeAndNumber(send.getData()) + ", sending now");
         
        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } 
    }

    //Send an error 5 packet, wait for response
    public void sendReceiveInvalidTID ()
    {
        System.out.println("Sending " + TFTPCommon.packetTypeAndNumber(send.getData()) + " with invalid TID");

        TFTPCommon.printPacketDetails(send, verbosity, false);

        try {
            socket.send(send);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Waiting for error packet from server");

        try {
            socket.receive(receive);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Received " + TFTPCommon.packetTypeAndNumber(receive.getData()));

        TFTPCommon.printPacketDetails(receive, verbosity, false);
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
        else if (modType == TFTPCommon.ModificationType.INVALIDTID)
        {
            sendReceiveInvalidTID();
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
        int modToDelete, simulateListSize;
        Scanner simulateListSc;

        while ( true )
        {
            simulateListSize = parentSimulator.simulateListLength();
            simulateListSc = new Scanner(parentSimulator.simulateListToString());

            if (simulateListSize == 0)
            {
                System.out.println("There are no pending modifications");
                simulateListSc.close();
                return;
            }

            scIn = new String();
            modToDelete = 0;

            System.out.println("List of Pending Modifications");

            for (int i = 0; i < simulateListSize; i++)
            {
                if (simulateListSc.hasNextLine())
                {
                    System.out.println((i + 1) + ". " + simulateListSc.nextLine());
                }
            }

            while (modToDelete <= 0)
            {
                System.out.print("Please enter a number (or r to return): ");

                scIn = sc.nextLine();

                if (scIn.equalsIgnoreCase("r"))
                {
                    simulateListSc.close();
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

            if (modToDelete <= simulateListSize && simulateListSize == parentSimulator.simulateListLength())
            {
                try {
                    parentSimulator.removeMod(modToDelete);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Failed to remove mod!");
                }
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
                return new SimulatePacketInfo(0, pType);
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
        int newLen = -1;

        if (dataModPacket.getPacketType() == TFTPCommon.PacketType.INVALID)
        {
            return;
        }

        while (true)
        {
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
            System.out.println("length: Modify packet length");
            System.out.println("manual: Manually modify packet contents");
            System.out.println("r: return without saving");
            System.out.println("s: save changes and return");

            scIn = sc.nextLine();

            if ( scIn.equalsIgnoreCase("opcode") )
            {
                opcode = getIntMenu(sc, 0, 512, "Enter new opcode (as bytes, i.e. 02, 03, etc.): ");
                dataModPacket.setOpcode(opcode);
                dataModPacket.setContentModType(TFTPCommon.ContentSubmod.OPCODE);
            }
            
            if (dataModPacket.getPacketType() == TFTPCommon.PacketType.REQUEST)
            {
                if ( scIn.equalsIgnoreCase("fname") )
                {
                    System.out.print("Enter new file name: ");
                    dataModPacket.setFileName(sc.nextLine());
                    dataModPacket.setContentModType(TFTPCommon.ContentSubmod.FILENAME);
                }
                else if ( scIn.equalsIgnoreCase("mode") )
                {
                    System.out.print("Enter new file mode: ");
                    dataModPacket.setFileMode(sc.nextLine());
                    dataModPacket.setContentModType(TFTPCommon.ContentSubmod.FILEMODE);
                }
            }

            else if ( scIn.equalsIgnoreCase("block") )
            {
                blockNum = getIntMenu(sc, 0, 512, "Enter new block number (as integer, 0-512): ");
                dataModPacket.setBlockNum(blockNum);
                dataModPacket.setContentModType(TFTPCommon.ContentSubmod.BLOCKNUM);
            }

            if ( scIn.equalsIgnoreCase("length") )
            {
                System.out.println("Note: If the new length is greater than the old length, the packet gets padded with 0s. If the new length is smaller than the old length, the packet gets truncated");
                newLen = getIntMenu(sc, 0, 516, "Enter new length (as integer, 0-516): ");
                dataModPacket.setLength(newLen);
                dataModPacket.setContentModType(TFTPCommon.ContentSubmod.LENGTH);
            }

            else if ( scIn.equalsIgnoreCase("manual") )
            {
                Integer position = getIntMenu(sc, 0, 516, "Enter byte position to modify (as integer, 0-516): ");
                Integer value = getIntMenu(sc, 0, 255, "Enter new value for byte: ");

                ModifyByte modByte = new ModifyByte(position, value.byteValue());
                dataModPacket.addModByte(modByte);
                dataModPacket.setContentModType(TFTPCommon.ContentSubmod.MANUAL);
            }

            if ( scIn.equalsIgnoreCase("r") )
            {
                return;
            }

            if ( scIn.equalsIgnoreCase("s") )
            {
                if (dataModPacket.getContentModType() != TFTPCommon.ContentSubmod.INVALID)
                {
                    if (!parentSimulator.appendMod(dataModPacket))
                    {
                        System.out.println("Failed to add modification!");
                    }
                }

                return;
            }
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
                System.out.println(parentSimulator.simulateListToString());
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
	private int pNum, delayDuplicateGap, opcode, blockNum, length;
    private String fileName, fileMode;
	private TFTPCommon.ModificationType modType;
    private List<ModifyByte> modByte;
    private TFTPCommon.ContentSubmod subMod;

    public SimulatePacketInfo (int pNum, TFTPCommon.PacketType pType)
    {
        this.pNum = pNum;
        this.pType = pType;
        this.subMod = TFTPCommon.ContentSubmod.INVALID;
        this.length = -1;
        fileName = new String();
        fileMode = new String();
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

    public TFTPCommon.ContentSubmod getContentModType ()
    {
        return subMod;
    }

    public int getDelayDuplicateGap ()
    {
        return delayDuplicateGap;
    }

    public List<ModifyByte> getModByteList ()
    {
        return modByte;
    }

    public String getFileName ()
    {
        return fileName;
    }

    public String getFileMode ()
    {
        return fileMode;
    }

    public int getLength ()
    {
        return length;
    }

    public void setModType (TFTPCommon.ModificationType modType)
    {
        this.modType = modType;
    }

    public void setContentModType (TFTPCommon.ContentSubmod subMod)
    {
        setModType(TFTPCommon.ModificationType.CONTENTS);
        this.subMod = subMod;
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

    public void setLength (int length)
    {
        this.length = length;
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
            if (fileName.length() > 0)
            {
                returnString = returnString + System.lineSeparator() + "Change filename to " + fileName;
            }

            if (fileMode.length() > 0)
            {
                returnString = returnString + System.lineSeparator() + "Change filemode to " + fileMode;
            }

            if (length > -1)
            {
                returnString = returnString + System.lineSeparator() + "Change length to " + length;
            }

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