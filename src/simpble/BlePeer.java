package simpble;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.util.Log;
import android.util.SparseArray;

public class BlePeer {

	private static final String TAG = "BlePeer";
	
	// the network address of this peer; only good for the current connection
	// you might think this unnecessary, but will come into play if your Central role
	// is connected to multiple peripherals
	private String peerAddress;
	
	// map of the BleMessages incoming from this peer
	private Map<Integer, BleMessage> peerMessagesIn;
	
	// map of the BleMessages outgoing to this peer
	private SparseArray<BleMessage> peerMessagesOut;
	
	// how this peer is currently connected (if at all?)
	
	/**
	 * How your are currently connected to your peer 
	 */
	public String ConnectedAs;
	
	public int CurrentMessageIndex;
	
	// the last update of activity
	private Date lastActivity;
	
	// this needs to be a private collection referenced w/ getters and setters
	public String subscribedChars;
	
	/**
	 * 
	 * @param PeerAddress Network address of the peer, or really anything that uniquely identifies this peer for the current connection
	 */
	public BlePeer(String PeerAddress) {
		peerAddress = PeerAddress;

		peerMessagesOut = new SparseArray<BleMessage>();
		peerMessagesIn = new HashMap<Integer, BleMessage>();
		
		ConnectedAs = "";
		CurrentMessageIndex = 0;
		subscribedChars ="";
		
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
	
	
	public SparseArray<BleMessage> GetMessagesOut() {
		return peerMessagesOut;
	}
	
	public int PendingMessageCount() {
		int c = 0;
		
		for (int i = 0; i < peerMessagesOut.size(); i++) {
			if (peerMessagesOut.valueAt(i) != null) {
				c++;
			}
		}
		return c;
	}
	
	public Map<Integer, BleMessage> GetMessageIn() {
		return peerMessagesIn;
	}
	
	public String RecipientAddress() {
		return peerAddress;
	}
	
	public void SetRecipientAddress(String newAddress) {
		peerAddress = newAddress;
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
		
		Log.v(TAG, "find message in peerMessagesOut at index " + String.valueOf(MessageIdentifier));
		
		//for (int i = 0; i < peerMessagesOut.size(); i++) {
			//BleMessage m = peerMessagesOut.get(i);
		BleMessage m = peerMessagesOut.get(MessageIdentifier);
			
			if (m != null) {
				Log.v(TAG, "found message at index " + String.valueOf(MessageIdentifier) + " with hash " + ByteUtilities.bytesToHex(m.MessageHash));
			} else {
				Log.v(TAG, "no message found at index " + String.valueOf(MessageIdentifier));
			}
		//}
		
		return peerMessagesOut.get(MessageIdentifier);
	}
	
	public BleMessage getBleMessageOut() {
		
		Log.v(TAG, "peerMessagesOut.size is " + String.valueOf(peerMessagesOut.size()));
		
		// get the highest priority (lowest index) message to send out
		int keyat = 0;
		
		// get the min item in this SparseArray
		for (int i = 0; i < peerMessagesOut.size(); i++) {
			keyat = peerMessagesOut.keyAt(i);
			if (peerMessagesOut.get(keyat) != null) {
				break;
			}
		}
		
		Log.v(TAG, "getBleMessageOut #" + String.valueOf(keyat));
		
		return getBleMessageOut(keyat);

	}
	
	public void RemoveBleMessage(int MessageIndex) {

		// remove it
		peerMessagesOut.delete(MessageIndex);
		
		Log.v(TAG, "removing message " + String.valueOf(MessageIndex) + " b/c it was sent");

	
	}

	public void RemoveBleMessage(BleMessage m) {
		
		// loop over all our packets to send
		for (int i = 0; i < peerMessagesOut.size(); i++) {
			BleMessage bm  = peerMessagesOut.valueAt(i);
			
			if (bm.equals(m)) {
				RemoveBleMessage(i);
			}
		}

	}
	
	
	public String BuildBleMessageOut(byte[] MsgBytes) {
		
		int messageidx = peerMessagesOut.size();
		
		BleMessage m = new BleMessage();
		m.SetRawBytes(MsgBytes);
		
		Log.v(TAG, "added message #" + String.valueOf(messageidx) + " (" + ByteUtilities.bytesToHex(m.MessageHash) + "), peer " + this.toString());
		m.SetMessageNumber(messageidx);
		peerMessagesOut.append(messageidx, m);
		
		return ByteUtilities.bytesToHex(m.MessageHash);
			
	}
	

    
}
