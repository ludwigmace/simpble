package example.handshake;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import android.util.Log;

import com.google.common.primitives.Bytes;

import simpble.BleApplicationMessage;
import simpble.ByteUtilities;

public class ApplicationMessage extends BleApplicationMessage {
	private static final String TAG = "BLEAPPMSG";
	private static final int MessagePacketSize = 20; 

	// included in packet and serves as an identification so that the receiver can build msg from packets
	private int messageNumber;
	
	public byte MessageType;
	
	// sha1 of public key for recipient
	public byte[] RecipientFingerprint;
	
	// sha1 of public key for sender
	public byte[] SenderFingerprint;
	
	// truncated sha1 of message; carried in Packet 0 of every message
	public byte[] MessageHash;
	
	// body of message in bytes
	public byte[] MessagePayload;
	
	private String messageSignature;
	
	public boolean ReceiptAcknowledged;
	
	
	// initializes our list of BlePackets, current counter, and sent status
	public ApplicationMessage() {
		ReceiptAcknowledged = false;
	}
	
	
	/**
	 * A message signature uniquely identifies a line-item message in the database
	 * 
	 * @return A most-likely unique signature for this message that can be reconstructed
	 */
	public String GetSignature() {
		return messageSignature;
	}
	
	/**
	 * A message signature uniquely identifies a line-item message in the database
	 * 
	 * @param Pass in a signature
	 * @return returns the signature that you probably just passed in
	 */
	public String SetSignature(String signature) {
		messageSignature = signature;
		
		return messageSignature;
	}
	
	public String GetPayload() {
		return ByteUtilities.bytesToHex(MessagePayload).substring(0,8);
	}
	

	public byte[] BuildMessageMIC() {
		
        // get a digest for the message, to define it
        MessageDigest md = null;
        
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
        
        
        byte[] MessageBytes = Bytes.concat(new byte[]{MessageType}, RecipientFingerprint, SenderFingerprint, MessagePayload);
        
        // this builds a message digest of the MessageBytes, and culls the size less 5 bytes
        // (i want my digest to be the packet size less the 5 bytes needed for header info)
        return Arrays.copyOfRange(md.digest(ByteUtilities.trimmedBytes(MessageBytes)), 0, MessagePacketSize - 5);
		
	}
	
	
	/**
	 * Sets the MessagePayload class variable to the bytes you pass in.  Constructs a SHA1 hash based on
	 * (MessageType, RecipientFingerprint, SenderFingerprint, MessagePayload), strips off the last 5 bytes,
	 * and stores this value in the class variable MessagePayload.
	 * 
	 * @param Payload
	 */
	public void setPayload(byte[] Payload) {
		MessagePayload = Payload;
		
		allBytes = Bytes.concat(new byte[]{MessageType}, RecipientFingerprint, SenderFingerprint, MessagePayload);
		
		Log.v(TAG, "allBytes:" + ByteUtilities.bytesToHex(allBytes));
		
        // this builds a message digest of the MessageBytes, and culls the size less 5 bytes
        // (i want my digest to be the packet size less the 5 bytes needed for header info)
		MessageHash  = Arrays.copyOfRange(ByteUtilities.digestAsBytes(allBytes), 0, MessagePacketSize - 5);
		
		Log.v(TAG, "Hash:" + ByteUtilities.bytesToHex(MessageHash));
     	
	}
	

	
	public boolean BuildMessageDetails() {
		
		boolean success = false;
		
		/*
		 * - message type
		 * - recipient fingerprint
		 * - sender fingerprint
		 * - hash/mic
		 * - payload
		 */
		
		if (allBytes != null) {
			// we need this to be 41+ bytes
			if (allBytes.length > 41) {
			
				MessageType = Arrays.copyOfRange(allBytes, 0, 1)[0]; // byte 0
				RecipientFingerprint = Arrays.copyOfRange(allBytes, 1, 21); // bytes 1-20
				SenderFingerprint = Arrays.copyOfRange(allBytes, 21, 41); // bytes 21-40
				MessagePayload = Arrays.copyOfRange(allBytes, 41, allBytes.length+1); //bytes 41 through end
	
				Log.v(TAG, "MessageType: " + ByteUtilities.bytesToHex(new byte[]{MessageType}));
				Log.v(TAG, "RecipientFingerprint: " + ByteUtilities.bytesToHex(RecipientFingerprint));
				Log.v(TAG, "SenderFingerprint: " + ByteUtilities.bytesToHex(SenderFingerprint));
				Log.v(TAG, "MessagePayload: " + ByteUtilities.bytesToHex(MessagePayload));
				
				messageSignature = ByteUtilities.digestAsHex(allBytes);
				
				success = true;
			}
		}
		
		return success;
		
	}

	

}
