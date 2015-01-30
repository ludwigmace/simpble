package simpble;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.util.Log;

public class BlePeer {

	private static final String TAG = "BlePeer";
	
	// the network address of this peer; only good for the current connection
	// you might think this unnecessary, but will come into play if your Central role
	// is connected to multiple peripherals
	private String peerAddress;
	
	// kind of unused, but will need to fill this
	private String peerName;
	
	// this peer's public key, in byte array form
	private byte[] peerPublicKey;
	
	// fingerprint to identify this user, in byte array form
	private byte[] peerPublicKeyFingerprint;

	// map of the BleMessages incoming from this peer
	private Map<Integer, BleMessage> peerMessagesIn;
	
	// map of the BleMessages outgoing to this peer
	private Map<Integer, BleMessage> peerMessagesOut;
	
	// how this peer is currently connected (if at all?)
	public String ConnectedAs;
	
	public int CurrentMessageIndex;
	
	// the last update of activity
	private Date lastActivity;
	
	/**
	 * 
	 * @param PeerAddress Network address of the peer, or really anything that uniquely identifies this peer for the current connection
	 */
	public BlePeer(String PeerAddress) {
		peerAddress = PeerAddress;
		peerName="";
		peerMessagesIn = new HashMap<Integer, BleMessage>();
		peerMessagesOut = new HashMap<Integer, BleMessage>();
		
		ConnectedAs = "";
		CurrentMessageIndex = 0;
		
		// set the last activity date to right now
		MarkActive();
	}
	
	public void MarkActive() {
		lastActivity = new Date();
	}
	
	public boolean CheckStale() {
		boolean isStale = false;
		
		Date rightnow = new Date();
		
		long diff = rightnow.getTime() - lastActivity.getTime();
		
		if (diff > (1000 * 10)) { // if we're over 10 seconds of stale time
			isStale = true;
		}
		
		return isStale;
	}
	
	/**
	 * Verify if the SHA-1 digest matches that calculated from the payload
	 * @param digest digest used to verify, in byte format
	 * @param payload data to check, in byte format
	 * @return true if matches, false if not
	 */
	private boolean checkDigest(byte[] digest, byte[] payload) {
        MessageDigest md = null;
        boolean status = false;
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
        
        if (Arrays.equals(md.digest(payload), digest)) {
        	status = true;	
        }

        return status;
	}
	
	public Map<Integer, BleMessage> GetMessageOut() {
		return peerMessagesOut;
	}
	
	public Map<Integer, BleMessage> GetMessageIn() {
		return peerMessagesIn;
	}
	
	public String RecipientAddress() {
		return peerAddress;
	}
	
	public byte[] GetPublicKey() {
		return peerPublicKey;
	}
	
	public boolean SetPublicKey(byte[] publicKey) {
		boolean status = true;
		
		peerPublicKey = publicKey;
		
		status = checkDigest(peerPublicKeyFingerprint, publicKey);
		
		return status;
	}
	
	
	public String GetFingerprint() {
		if (peerPublicKeyFingerprint != null) {
			return ByteUtilities.bytesToHex(peerPublicKeyFingerprint);
		} else {
			return "";
		}
	}

	public byte[] GetFingerprintBytes() {
		return peerPublicKeyFingerprint;
	}
	
	public void SetFingerprint(byte[] fp) {
		peerPublicKeyFingerprint = fp;
	}
	
	public void SetFingerprint(String fp) {
		peerPublicKeyFingerprint = ByteUtilities.hexToBytes(fp);
	}
	
	public BleMessage getBleMessageIn(int MessageIdentifier) {

		// if there isn't already a message with this identifier, add one
		if (!peerMessagesIn.containsKey(MessageIdentifier)) {
			peerMessagesIn.put(MessageIdentifier, new BleMessage());
		}
		
		return peerMessagesIn.get(MessageIdentifier);
	}
	
	public BleMessage getBleMessageOut(int MessageIdentifier) {
		CurrentMessageIndex = MessageIdentifier;
		return peerMessagesOut.get(MessageIdentifier);
	}
	
	public BleMessage getBleMessageOut() {
		
		// get the highest priority (0=highest) message to send out
		int min = 0;
		for (Integer i : peerMessagesOut.keySet()) {
			if (i <= min ) {
				min = i;
			}
		}
		
		Log.v(TAG, "getBleMessageOut #" + String.valueOf(min));
		return getBleMessageOut(min);
	}
	
	public void RemoveBleMessage(int MessageIndex) {
		// get the message based off the supplied index
		BleMessage m = peerMessagesOut.get(MessageIndex);
		
		Log.v(TAG, "removing message " + String.valueOf(MessageIndex) + " b/c it was sent");
		
		// remove it
		peerMessagesOut.remove(m);
	
	}

	public void RemoveBleMessage(BleMessage m) {
		// remove the sent message
		peerMessagesOut.remove(m);
	}
	
	
	public void addBleMessageOut(BleMessage m) {
		
		// find the highest message number
		int max = 0;
		
		if (peerMessagesOut.size() > 0) {
				for (Integer i : peerMessagesOut.keySet()) {
					if (max <= i ) {
						max = i;
					}
				}
				max++;
		}
		
		Log.v(TAG, "add message to peerMessagesOut #" + String.valueOf(max));
		peerMessagesOut.put(max, m);		
	}
	
	public void SetName(String PeerName) {
		peerName = PeerName;
	}
	
	public String GetName() {
		return peerName;
	}
	
	

    
}
