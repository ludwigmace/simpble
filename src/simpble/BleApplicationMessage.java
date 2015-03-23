package simpble;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import android.util.Log;
import android.util.SparseArray;

import com.google.common.primitives.Bytes;

public class BleApplicationMessage {

	private static final String TAG = "BLEG";
	private static final int MessagePacketSize = 20; 
	
	// holds all the packets that make up this message
	private SparseArray<BlePacket> messagePackets;
	
	private SparseArray<BlePacket> pendingPackets;
	
	// number of packets that make up this message
	private int BlePacketCount;
	
	// are there any pending packets left that need to be sent?
	private boolean pendingPacketStatus;
	
	// indicates which packet needs to be sent
	private int currentPacketCounter;
	
	// included in packet and serves as an identification so that the receiver can build msg from packets
	private int messageNumber;

	// Identity or something else
	//public String MessageType;
	
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
	
	// the raw bytes of this message
	private byte[] allBytes;
	
	

	// initializes our list of BlePackets, current counter, and sent status
	public BleApplicationMessage() {
		messagePackets = new SparseArray<BlePacket>();
		currentPacketCounter = 0;
		pendingPacketStatus = false;
		ReceiptAcknowledged = false;
	}
	
	// allows calling program to set which number identifies this message
	public void SetMessageNumber(int MessageNumber) {
		messageNumber = MessageNumber;
		
		// now that we know which message sequence this is for the receiving peer, we can build the packets
		constructPackets();
	}

	
	// get the message sequence number
	public int GetMessageNumber() {
		return messageNumber;
	}
	
	// returns all the BlePackets that make up this message
	public SparseArray<BlePacket> GetAllPackets() {
		return messagePackets;
	}
	
	//  returns all the pending BlePackets that make up this message
	public SparseArray<BlePacket> GetPendingPackets() {
		return pendingPackets;
	}
	
	public void PacketSent(int i) {
		pendingPackets.removeAt(i);
	}
	
	public boolean PacketReQueue(int i) {
		
		boolean success = true;
		
		BlePacket p = null;
		
		try {
			p = messagePackets.get(i);
		} catch (Exception x) {
			success = false;
			Log.v(TAG, "couldn't find an object at index " + String.valueOf(i) + " in messagePackets");
		}
		
		if (success && p != null) {
			try {
				pendingPackets.put(i, p);
			} catch (Exception x) {
				Log.v(TAG, "couldn't add packet for index " + String.valueOf(i) + " into pendingPackets");
				success = false;
			}
		}
		
		
		return success;
	}
	
	// from our array of packets, return a particular packet
	public BlePacket GetPacket(int PacketNumber) {
		return messagePackets.get(PacketNumber);
	}
	
	// call GetPacket(int) by calculating the int based off the currentPacketCounter
	// increment this counter after pulling this packet
	public BlePacket GetPacket() {
			
		// as long as you've got packets to send, send them; if no more packets to send, send 0x00
		if (currentPacketCounter <= pendingPackets.size()-1) {
			return GetPacket(currentPacketCounter++);
		} else {
			pendingPacketStatus = false;
			return null;
		}
		
	}
	
	public void ClearPendingPackets() {
		pendingPackets = new SparseArray<BlePacket>();
	}
	
	// are there still packets left to send?
	public boolean PendingPacketStatus() {
		return pendingPacketStatus;
	}
	
	// create a BlePacket with a given sequence and payload, and add to our packets list
	private void addPacket(int packetSequence, byte[] packetBytes) {
		messagePackets.put(packetSequence, new BlePacket(packetSequence, packetBytes));
		
		// if the size of the sparsearray is >= the # of packets we're expecting then we've got the msg
		// we're going strictly off the number of packets here, and i'm not sure if that's the best way to go about this
		if (messagePackets.size() > BlePacketCount) {
			pendingPacketStatus = false;
		} else {
			pendingPacketStatus = true;
		}
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

	/**
	 * Calculates a hash and checks if it matches what was provided
	 */
	public String GetCalcHash() {
		
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
        byte[] calcDigest = Arrays.copyOfRange(md.digest(ByteUtilities.trimmedBytes(MessageBytes)), 0, MessagePacketSize - 5);
		
		//return ByteUtilities.bytesToHexShort(calcDigest);
        
        // return the source for our hash so we can see if it's what we need
        return ByteUtilities.bytesToHex(calcDigest).substring(0,8);
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
		
        // this builds a message digest of the MessageBytes, and culls the size less 5 bytes
        // (i want my digest to be the packet size less the 5 bytes needed for header info)
		MessageHash  = Arrays.copyOfRange(ByteUtilities.digestAsBytes(allBytes), 0, MessagePacketSize - 5);
     	
	}
	
	/**
	 * Takes the message payload from the calling method and builds the list
	 * of BlePackets
	 * 
	 * @param Payload Body of the message you want to send in bytes
	 * @param MessagePacketSize The size of the message packets
	 */
	private void constructPackets() {

		// for an id message:
		// first byte, 0x01, indicates an identity message
		// next 20 bytes are recipient fingerprint
		// next 20 bytes are sender fingerprint
		// final arbitrary bytes are the payload
		
		
		// Message Type, RFP, SFP, and payload
		byte[] MessageBytes = Bytes.concat(new byte[] {MessageType}, RecipientFingerprint, SenderFingerprint, MessagePayload);
		
		// clear the list of packets; we're building a new message using packets!
        messagePackets.clear();
        
        // how many packets?  divide msg length by packet size, w/ trick to round up
        // so the weird thing is we've got to leave a byte in each msg, so effectively our
        // msg blocks are decreased by an extra byte, hence the -4 and -3 below
        int msgCount  = (MessageBytes.length + MessagePacketSize - 4) / (MessagePacketSize - 3);
        
        // first byte is counter; 0 provides meta info about msg
        // right now it's just how many packets to expect
                
        Log.v(TAG, "first payload is of size: " + String.valueOf(MessageHash.length));
        
        // first byte is which message this is for the receiver to understand
        // second/third bytes are current packet
        // fourth/fifth bytes are message size
        // 6+ is the digest truncated to 15 bytes
        
        // build the message size tag, which is the number of BlePackets represented in two bytes
        byte[] msgSize = new byte[2];
        msgSize[0] = (byte)(msgCount >> 8);
        msgSize[1] = (byte)(msgCount & 0xFF);
        
        /* The first BlePacket!!!  includes:
         * 1 byte - this BleMessage's identifying number,
         * 2 bytes - the current packet counter, 
         * 2 bytes  - the number of BlePackets,
         * n bytes - the message digest
        */ 
        byte[] firstPacket = Bytes.concat(new byte[]{(byte)(messageNumber & 0xFF)}, new byte[]{(byte)0x00, (byte)0x00}, msgSize, MessageHash);

        // create a BlePacket of index 0 using the just created payload
        addPacket(0, firstPacket);
        
        // now start building the rest
        int msgSequence = 1;
					
        // loop over the payload and build packets, starting at index 1
		while (msgSequence <= msgCount) {
			
			/* based on the current sequence number and the message packet size
			 *  get the read index in the MessageBytes array */
			int currentReadIndex = ((msgSequence - 1) * (MessagePacketSize - 3));
		
			// leave room for the message counters (the -3 at the end)
			byte[] val = Arrays.copyOfRange(MessageBytes, currentReadIndex, currentReadIndex + MessagePacketSize - 3);

			// the current packet counter is the message sequence, in two bytes
	        byte[] currentPacketCounter = new byte[2];
	        currentPacketCounter[0] = (byte)(msgSequence >> 8);
	        currentPacketCounter[1] = (byte)(msgSequence & 0xFF);
 
	        // build the payload for the packet using the identifying parent BleMessage number, the current BlePacket counter, and the BlePacket bayload
	        val = Bytes.concat(new byte[]{(byte)(messageNumber & 0xFF)}, currentPacketCounter, val);

	        // add this packet to our list of packets
	        addPacket(msgSequence, val);

	        // increment our counter for the next round
	        msgSequence++;
			
		}
		
		// now that we've built up what all our packets are, clone these into a pending packets array
		pendingPackets = messagePackets.clone();

		// once we've built all the packets up, indicate we have packets pending send
		pendingPacketStatus = true;
		
	}
	
	/**
	 * Given a byte array for a BlePacket and the counter for that packet, add to our
	 * list of BlePackets that make up this message.  Once the provided PacketCounter is
	 * gte our indicated BlePacketCount, call unbundleMessage() to slam all these packets together
	 * into a message
	 * 	
	 * @param packetCounter
	 * @param packetPayload
	 */
	public void BuildMessageFromPackets(int packetCounter, byte[] packetPayload) {
		this.addPacket(packetCounter, packetPayload);
		
		/* if we've got all the packets for this message,
		 * set our pending packet flag to false (flag set in above addPacket method)
		 */
		if (!pendingPacketStatus) {
			// now that we've got all the packets, build the raw bytes for this message
			allBytes = dePacketize();
		}
		
	}
	
	/**
	 * Before calling BuildMessageFromPackets(int packetCounter, byte[] packetPayload), call this
	 * while passing in messageSize.  This initializes our messagePackets SparseArray<BlePacket> (and pendingPackets),
	 * sets BlePacketCount = messageSize, sets our flag for pendingPacketStatus=true, and then 
	 * calls BuildMessageFromPackets(int packetCounter, byte[] packetPayload) to add the first BlePacket
	 * 
	 * @param packetCounter
	 * @param packetPayload
	 * @param messageSize
	 */
	public void BuildMessageFromPackets(int packetCounter, byte[] packetPayload, int messageSize) {
		messagePackets = new SparseArray<BlePacket>();
		BlePacketCount = messageSize;
		pendingPacketStatus = true;
		
		BuildMessageFromPackets(packetCounter, packetPayload);
	}

	
	private boolean BuildMessageDetails(byte[] RawBytes) {
		
		boolean success = false;
		
		/*
		 * - message type
		 * - recipient fingerprint
		 * - sender fingerprint
		 * - hash/mic
		 * - payload
		 */
		
		if (RawBytes != null) {
			// we need this to be 41+ bytes
			if (RawBytes.length > 41) {
			
				MessageType = Arrays.copyOfRange(RawBytes, 0, 1)[0]; // byte 0
				RecipientFingerprint = Arrays.copyOfRange(RawBytes, 1, 21); // bytes 1-20
				SenderFingerprint = Arrays.copyOfRange(RawBytes, 21, 41); // bytes 21-40
				MessagePayload = Arrays.copyOfRange(RawBytes, 41, RawBytes.length+1); //bytes 41 through end
	
				success = true;
			}
		}
		
		return success;
		
	}

	// make sure all the packets are there, and in the right order
	public ArrayList<Integer> GetMissingPackets() {
		
		ArrayList<Integer> l;
		ArrayList<Integer> missing;
		
		l = new ArrayList<Integer>();
		missing = new ArrayList<Integer>();
		
		// add the message sequence for each packet in the msg packets into a list
		// this will write these packets out in order
		for (int i = 0; i < messagePackets.size(); i++) {
			
			// get the packet corresponding to the current index
			BlePacket b = messagePackets.valueAt(i);
			l.add(b.MessageSequence);
		}

		// search the list for every packet
		for (int i = 0; i <= BlePacketCount; i++) {
			if (!l.contains(i)) {
				missing.add(i);
				Log.v(TAG, "missing packet #" + String.valueOf(i));
			}
		}
		

		return missing;
	}
	
	public byte[] GetAllBytes() {
		return allBytes;
	}
	
	/**
	 * 
	 * 
	 * @param RawMessageBytes The raw bytes that make up this message
	 * @return If this message could be properly constructed from these bytes, return TRUE
	 */
	public boolean SetRawBytes(byte[] RawMessageBytes) {
		
		boolean success = false;
		
		allBytes = RawMessageBytes;
        // this builds a message digest of the MessageBytes, and culls the size less 5 bytes
        // (i want my digest to be the packet size less the 5 bytes needed for header info)
		MessageHash  = Arrays.copyOfRange(ByteUtilities.digestAsBytes(allBytes), 0, MessagePacketSize - 5);
		
		success = BuildMessageDetails(RawMessageBytes);
		
		return success;
	}
	
	/** 
	 * Loop over all the BlePackets in the message - packet0 is the hash; write the rest to MessageBytes
	 * 
	 * @return Byte array of the message packets
	 */
	private byte[] dePacketize() {
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] failure = new byte[] {0x00};
			
		// this will write these packets out in order
		for (int i = 0; i < messagePackets.size(); i++) {
			
			// get the packet corresponding to the current index
			BlePacket b = messagePackets.valueAt(i);
        	
			if (b != null ) {
				try {
					// if this is the first packet in the sequence, the first couple of bytes are header and the rest are the Hash
		        	if (b.MessageSequence == 0) {
		        		MessageHash = Arrays.copyOfRange(b.MessageBytes, 2, b.MessageBytes.length);
		        	} else {
		        		try {
							os.write(b.MessageBytes);
						} catch (IOException e) {
							// if we can't write to our output stream, return a NUL byte
							return failure;
						}
		        	}
				} catch (Exception e) {
					Log.e(TAG, "b.MessageSequence didn't return anything, or couldn't build MessageHash: " + e.getMessage());
					return failure;
				}
			} else {
				Log.v(TAG, "no packet returned for messagePackets.valueAt(" + String.valueOf(i) + ")");
			}
        	
        }
		
        return os.toByteArray(); 
		
	}
	
}
