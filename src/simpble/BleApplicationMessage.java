package simpble;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import android.util.Log;

import com.google.common.primitives.Bytes;

public abstract class BleApplicationMessage {

	private static final String TAG = "BLEAPPMSG";
	private static final int MessagePacketSize = 20; 

	// included in packet and serves as an identification so that the receiver can build msg from packets
	private int messageNumber;
	
	public byte MessageType;
	
	// truncated sha1 of message; carried in Packet 0 of every message
	public byte[] MessageHash;
	
	// body of message in bytes
	public byte[] MessagePayload;
	
	private String messageSignature;
	
	public boolean ReceiptAcknowledged;
	
	// the raw bytes of this message
	protected byte[] allBytes;
	
	/**
	 * A way for the calling application to identify a particular message
	 * Doesn't have any setters/getters
	 */
	public String ApplicationIdentifier;
	
	// this is meant to be a helper that the calling application will implement
	public abstract boolean BuildMessageDetails();
	
	
	// initializes our list of BlePackets, current counter, and sent status
	public BleApplicationMessage() {
		ReceiptAcknowledged = false;
	}
	
	
	// allows calling program to set which number identifies this message
	public void SetMessageNumber(int MessageNumber) {
		messageNumber = MessageNumber;
	
	}


	
	// get the message sequence number
	public int GetMessageNumber() {
		return messageNumber;
	}
		
	public byte[] GetAllBytes() {
		return allBytes;
	}
	
	/** 
	 * Sets the bytes that make up this message
	 * @param RawMessageBytes The raw bytes that make up this message
	 * @return If this message could be properly constructed from these bytes, return TRUE
	 */
	public String SetRawBytes(byte[] RawMessageBytes) {
		
		
		allBytes = RawMessageBytes;
        // this builds a message digest of the MessageBytes, and culls the size less 5 bytes
        // (i want my digest to be the packet size less the 5 bytes needed for header info)
		MessageHash  = Arrays.copyOfRange(ByteUtilities.digestAsBytes(allBytes), 0, MessagePacketSize - 5);
		
		return ByteUtilities.bytesToHex(MessageHash);
	}

	
}
