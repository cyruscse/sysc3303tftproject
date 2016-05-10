// FileOperation.java
// This class provides file IO for the client and server classes
// It can split a file into chunks to be used for data packets
// It also stitches the chunks back into a file to write to disk
package grouptwo;

import java.io.*;

public class FileOperation
{
    private File file;
    private FileInputStream inStream;
    private FileOutputStream outStream;
    private int numBytes;

    public String getFilePath() 
    {
        return file.getAbsolutePath();
    }

    //Gets number of TFTP data packets needed to transfer file
    public int getNumTFTPBlocks() 
    {   
        if (file.length() == 0)
        {
            return 1;
        }
        
        double blocks = Math.ceil((double) file.length() / numBytes);
        
        if (file.length() % 512 == 0)
        {
            blocks += 1;
        }

        return (int) blocks;
    }

    public int size()
    {
        return (int) file.length();
    }

    //Read FileInputStream in chunks then write each chunk into the provided byte array
    //Returns length of final packet
    public int readNextDataPacket(byte[] data, int dataOffset) throws FileNotFoundException 
    {
        int readAmount = numBytes;

        try {
            if (inStream.available() < readAmount)
            {
                readAmount = inStream.available();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        if (readAmount > 0)
        {
            try {
                //Returns readAmount, add 4 for opcode/bytenumber
                return inStream.read(data, dataOffset, readAmount) + 4;
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        return 4;
    }

    //Write each chunk of data into the FileOutputStream in order to recreate file
    public void writeNextDataPacket(byte[] data, int dataOffset, int len) throws FileNotFoundException 
    {
        try {
            outStream.write(data, dataOffset, len);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    //Close file once we are finished writing
    public void finalizeFileWrite()
    {
        try {
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public FileOperation(String absolutePath, Boolean localRead, int bytesRW) throws FileNotFoundException
    {
        numBytes = bytesRW;
        file = new File(absolutePath);

        //Client: Read Request writes to local machine
        //Server: Write Request writes to local machine
        if ( localRead == false ) 
        {
            file.delete();
            //Constructor: Path, Append (allows us to make a file out of packets)
            outStream = new FileOutputStream(absolutePath, true);
        }
        //Client: Write Request reads from local machine
        //Server: Read Request reads from local machine
        else
        {
            inStream = new FileInputStream(absolutePath);
        }
    }
}
