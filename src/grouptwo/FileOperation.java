package grouptwo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.FileSystems;

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
     *   Exception used for indicating file errors, always constructed with a TFTPCommon ErrorCode
     *
     *   @author Cyrus Sadeghi
     */
    public class FileOperationException extends Exception 
    {
        public TFTPCommon.ErrorCode error;
        public String strError;

        public FileOperationException (TFTPCommon.ErrorCode e)
        {
            error = e;
            strError = new String();
        }

        public FileOperationException (TFTPCommon.ErrorCode e, String strError)
        {
            this(e);
            this.strError = strError;
        }

        public String toString()
        {
            return strError;
        }
    }

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
    public int readNextDataPacket(byte[] data, int dataOffset) throws IOException
    {
        int readAmount = numBytes;

        if (inStream.available() < readAmount)
        {
            readAmount = inStream.available();
        }

        if (readAmount > 0)
        {
            //Returns readAmount, add 4 for opcode/bytenumber
            return inStream.read(data, dataOffset, readAmount) + dataOffset;
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
    public void writeNextDataPacket(byte[] data, int dataOffset, int len) throws IOException, FileOperationException 
    {
        if ( file.getUsableSpace() < len )
        {   finalizeFileWrite();
            delete();
            throw new FileOperationException(TFTPCommon.ErrorCode.DISKFULL, "Destination disk is full"); 
        }

        if ( !file.canWrite() )
        {
            
            finalizeFileWrite();
            delete();
            throw new FileOperationException(TFTPCommon.ErrorCode.ACCESSVIOLATE, "File: \"" + file.getName() + "\" is not writeable. Access violation");
        }

        outStream.write(data, dataOffset, len);
    }

    /**
    *   Closes write file once we are finished with it
    *
    *   @param  none
    *   @return none
    */
    public void finalizeFileWrite() throws IOException
    {
        outStream.close();
    }

    /**
    *   Closes read file once we are finished with it
    *
    *   @param  none
    *   @return none
    */
    public void closeFileRead() throws IOException
    {
        inStream.close();
    }

    /**
     *   Deletes file referred to by this FileOperation
     *
     *   @param  none
     *   @return boolean true if delete was successful
     */
    public boolean delete(){
        try {
        	outStream.close();
	    	Files.delete(file.toPath());
	    	return true;
	    } catch (IOException e) {
	    	e.printStackTrace();
	    	return false;
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
    public FileOperation(String absolutePath, Boolean localRead, int bytesRW, Boolean overwrite) throws FileNotFoundException, FileOperationException
    {
        numBytes = bytesRW;
        file = new File(absolutePath);
        
        //Client: Read Request writes to local machine
        //Server: Write Request writes to local machine
        if ( localRead == false ) 
        {
            if (file.exists() && !Files.isWritable(FileSystems.getDefault().getPath(file.getAbsolutePath())))
            {
                throw new FileOperationException(TFTPCommon.ErrorCode.ACCESSVIOLATE, "File: \"" + file.getName() + "\" exists and is not writable");
            }

            if (overwrite)
            {
                file.delete();
            }

            if (file.exists())
            {
                throw new FileOperationException(TFTPCommon.ErrorCode.FILEEXISTS, "File: \"" + file.getName() + "\" already exists");
            }
            //Constructor: Path, Append (allows us to make a file out of packets)
            outStream = new FileOutputStream(absolutePath, true);
        }
       
        //Client: Write Request reads from local machine
        //Server: Read Request reads from local machine
        else
        {
            if (file.exists() && !Files.isReadable(FileSystems.getDefault().getPath(file.getAbsolutePath())))
            {
                throw new FileOperationException(TFTPCommon.ErrorCode.ACCESSVIOLATE, "File: \"" + file.getName() + "\" is not readable. Access violation.");
            }

            inStream = new FileInputStream(absolutePath);
        }
    }
}
