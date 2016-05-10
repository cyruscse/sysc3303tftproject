package grouptwo;

import java.io.*;

/**
* FileOperation is the class that is used by both the TFTP server and client
* to read and write files from/to disk. This class includes methods that
* return the number of TFTP data packets required to completely transfer the file,
* the splitting of a file into 512 (or fewer) byte blocks to construct a data packet from,
* and recreates a file from the 512 (or fewer) byte blocks.
*
* @author        Cyrus Sadeghi
*/
public class FileOperation
{
    private File file;
    private FileInputStream inStream;
    private FileOutputStream outStream;
    private int numBytes;

    /**
    *   Calculate the number of TFTP data packets required to transfer file
    *
    *   @param none
    *   @return int number of data packets required
    */
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

    /**
    *   Reads next data packet from file, skipping over opcode and block number. 
    *   This method continues reading from where it left off on its last invocation.
    *
    *   @param  byte[] array to read next data block to
    *   @param  int number of bytes preceding data block (i.e. opcode and block number), read starts after this many bytes
    *   @return int number of bytes read
    */
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
                return inStream.read(data, dataOffset, readAmount) + dataOffset;
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        return 4;
    }

    /**
    *   Writes next provided data block to file, skipping over opcode and block number.
    *   This method continues writing to where it left off on its last invocation
    *
    *   @param  byte[] array to write to file (next data block)
    *   @param  int number of bytes preceding data block (i.e. opcode and block number), write skips this many bytes
    *   @param  int length of data to write, in bytes
    *   @return none
    */
    public void writeNextDataPacket(byte[] data, int dataOffset, int len) throws FileNotFoundException 
    {
        try {
            outStream.write(data, dataOffset, len);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
    *   Closes write file once we are finished with it
    *
    *   @param  none
    *   @return none
    */
    public void finalizeFileWrite()
    {
        try {
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
    *   Closes read file once we are finished with it
    *
    *   @param  none
    *   @return none
    */
    public void closeFileRead()
    {
        try {
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
    *   Constructor for FileOperation, creates FileInputStream or FileOutputStream, depending on
    *   client/server and request type.
    *
    *   @param  String path to file on local machine
    *   @param  Boolean true when reading from local machine
    *   @param  int number of bytes to read write (for TFTP, 512)
    *   @return FileOperation
    */
    public FileOperation(String absolutePath, Boolean localRead, int bytesRW) throws FileNotFoundException, Exception
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

            if (getNumTFTPBlocks() > 65536)
            {
                throw new Exception("File is too big!");
            }
        }
    }
}
