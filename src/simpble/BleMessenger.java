package simpble;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

public class BleMessenger {
	private static String TAG = "blemessenger";
	private static int INACTIVE_TIMEOUT = 120000; // 2 minute timeout
	
	private Timer longTimer;
	
	// handles to the device and system's bluetooth management 
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	
	// the context needs to be passed to the advertiser
	private Context ctx;
	
	// service base constant defined by the framework, changeable by the developer (or user)
    private static String uuidServiceBase = "73A20000-2C47-11E4-8C21-0800200C9A66";
    
    // global variables to handle Central operations and Peripheral operations
    private static BleCentral bleCentral = null; 
    private static BlePeripheral blePeripheral = null;
    
    // callback for handling events from BleCentral and BlePeripheral
    private BleStatusCallback bleStatusCallback;
    
    // index of the current BleMessage that we're sending from peripheral mode
    // this may end up being unnecessary
    private int CurrentParentMessage;

    // keep a map of our messages for a connection session
    // also might end up being unnecessary
    private Map<Integer, BleMessage> bleMessageMap;

    // peers to keep an eye out for
    private Map<String, BlePeer> friendsFpMap;
    
    // allows us to look up peers by connected addresses
    public Map<String, BlePeer> peerMap;
    
    // our idmessage should stay the same, so save it in a global variable
    // allow to be set from calling functions
	public BleMessage idMessage;
    
    private List<BleCharacteristic> serviceDef;
	
    /**
     * Instantiates serviceDef arraylist, peerMap, fpNetMap, creates handles for peripheral/central,
     * populates serviceDef, and instantiates bleMessageMap
     * 
     * @param bluetoothManager Instantiated BluetoothManager object you create
     * @param bluetoothAdapter Instantiated BluetoothAdapter object you create
     * @param context The current application's context (used by the central/peripheral functions)
     * @param BleStatusCallback Callback of type BleStatusCallback you've created
     */
	public BleMessenger(BluetoothManager bluetoothManager, BluetoothAdapter bluetoothAdapter, Context context, BleStatusCallback eventCallback) {
		
		bleStatusCallback = eventCallback;
		btMgr = bluetoothManager;
		btAdptr = bluetoothAdapter;
		ctx = context;
		
		serviceDef = new ArrayList<BleCharacteristic>();
		
		// i need a place to put my found peers
		peerMap = new HashMap<String, BlePeer>();
		friendsFpMap = new HashMap<String, BlePeer>();
		
		// create your server for listening and your client for looking; Android can be both at the same time
		blePeripheral = new BlePeripheral(uuidServiceBase, ctx, btAdptr, btMgr, peripheralHandler);
		bleCentral = new BleCentral(btAdptr, ctx, centralHandler, uuidServiceBase, 3000);
		
		serviceDef.add(new BleCharacteristic("identifier_read", uuidFromBase("100"), BleGattCharacteristics.GATT_READ));		
		serviceDef.add(new BleCharacteristic("identifier_writes", uuidFromBase("101"), BleGattCharacteristics.GATT_WRITE));
		serviceDef.add(new BleCharacteristic("data_notify", uuidFromBase("102"), BleGattCharacteristics.GATT_NOTIFY));
		serviceDef.add(new BleCharacteristic("flow_control", uuidFromBase("105"), BleGattCharacteristics.GATT_READ));
		//serviceDef.add(new BleCharacteristic("data_indicate", uuidFromBase("103"), MyAdvertiser.GATT_INDICATE));
		//serviceDef.add(new BleCharacteristic("data_write", uuidFromBase("104"), MyAdvertiser.GATT_WRITE));

		bleCentral.setRequiredServiceDef(serviceDef);
		
		bleMessageMap = new HashMap<Integer, BleMessage>();
		
		setupStaleChecker(INACTIVE_TIMEOUT);  // setup timeout
		
	
		// when we connect, send the id message to the connecting party
	}
	
	/**
	 * Send all the messages to the passed in Peer
	 * @param Peer
	 */
	public void sendMessagesToPeer(String PeerAddress) {
		//BlePeer p = GetBlePeerByFingerprint(PeerFingerprint);
		BlePeer p = peerMap.get(PeerAddress);
		
		if (p != null) {
			if (p.PendingMessageCount() > 0) {
				bleStatusCallback.headsUp("m: found " + String.valueOf(p.PendingMessageCount()) + " msgs for " + PeerAddress);
				
				// is your peer connected as a peripheral or a central?
				if (p.ConnectedAs.equalsIgnoreCase("peripheral")) {
					// if they're a peripheral, you're a central, so send out as such
					writeOut(PeerAddress);
				} else if (p.ConnectedAs.equalsIgnoreCase("central")) {
					// if they're a central, you're a peripheral, so send out as such
					/* you'll need to know which attribute to write on
					 * if you're hoping to use a notify characteristic, they'll need to be subscribed to it
					*/ 
				}
			} else {
				bleStatusCallback.headsUp("m: no more messages for peer: " + PeerAddress);
			}
		} else {
			bleStatusCallback.headsUp("m: can't locate a peer w/ fp: " + PeerAddress);
		}
	}
	
	public BlePeer GetBlePeerByAddress(String remoteAddress) {
		
		bleStatusCallback.headsUp("m: check peerMap for remote: " + remoteAddress);
		
		BlePeer p = peerMap.get(remoteAddress);
		
		return p;
	}

	public BlePeer GetBlePeerByFingerprint(String fingerprint) {
		String remoteAddress = "";
		
		for (String peerAddress : peerMap.keySet()) {
			
			BlePeer p = peerMap.get(peerAddress);
			
			if (p.GetFingerprint().equalsIgnoreCase(fingerprint)) {
				remoteAddress = peerAddress;
				break;
			}
		}
		
		bleStatusCallback.headsUp("m: found address: " + remoteAddress + " for fingerprint: " + fingerprint);
		
		// now call your normal function
		return GetBlePeerByAddress(remoteAddress);
	}
	
	private String getFirstChars(String str, int index) {
		if (str.length() >= index) {
			return str.substring(0, index);
		} else {
			return str;
		}
	}

	
	private synchronized void setupStaleChecker(long timeout) {
		if (longTimer != null) {
			longTimer.cancel();
			longTimer = null;
		}
		
		if (longTimer == null) {
			longTimer = new Timer();
			
			longTimer.schedule(new TimerTask() {
				public void run() {
					longTimer.cancel();
					longTimer = null;
					
					// check timing on connections; drop those that are stale
					checkForStaleConnections();
				}
				
			}, timeout); // 10 sec, in ms
		}
	}
	
	private void checkForStaleConnections() {
		//bleStatusCallback.headsUp("m: check for stale connection!");
		// reset our stale-checker
		setupStaleChecker(INACTIVE_TIMEOUT); // 1 minute
		
		// loop over peers and check for staleness!
		for (Map.Entry<String, BlePeer> entry : peerMap.entrySet()) {
			BlePeer p = entry.getValue();
			bleStatusCallback.headsUp("m: is this guy current? connected as: " + p.ConnectedAs);
			
			// right here it's easy to disconnect a particular peripheral if you're the central
			// how to identify if the device you want to disconnect 
			if (p.CheckStale()) {
				// connected to your peer as a peripheral
				if (p.ConnectedAs.equalsIgnoreCase("peripheral")) {
					blePeripheral.closeConnection();
				} else {
					// connected to your peer as a central
					bleCentral.disconnectAddress(entry.getKey());
				}
				
				bleStatusCallback.headsUp("m: closing stale connection");
				peerMap.remove(entry.getKey()); // because we've disconnected, remove from our peerlist
			}
			
		}

	}

	
	// this is called when in CENTRAL mode
	private void writeOut(String peerAddress) {
		
		// look up the peer by their address (aka index)
		BlePeer peer = peerMap.get(peerAddress);
		
		// given a peer, get the first message in the queue to send out
		BleMessage m = peer.getBleMessageOut();
	
		bleStatusCallback.headsUp("m: (writeOut) - pending msgs: " + String.valueOf(peer.PendingMessageCount()));		
		
		// if no message found, there's a problem
		if (m == null) {
			Log.v(TAG, "cannot 'writeOut' - peer.getBleMessageOut returned null");
			bleStatusCallback.headsUp("m: (writeOut) no message found for peer");
			
			return;
		} else {
			// the previous call allows us to get the current message
			bleStatusCallback.headsUp("m: sending message: " + String.valueOf(peer.CurrentMessageIndex));
		}
		
		// get a sparsearray of the packets pending send for the message m
		SparseArray<BlePacket> bps = m.GetPendingPackets();
		
		bleStatusCallback.headsUp("m: # of pending packets: " + String.valueOf(bps.size()));
		int sent = 0;
		// loop over all our packets to send
		for (int i = 0; i < bps.size(); i++) {
			
			BlePacket p = bps.valueAt(i);
			
			try {
		
				byte[] nextPacket = p.MessageBytes;
				
				Log.v(TAG, "send write request to " + peerAddress);
				
	    		if (nextPacket != null) {
	    			//bleStatusCallback.headsUp("m: o:" + ByteUtilities.bytesToHexShort(nextPacket));
	    			Thread.sleep(100);
		    		bleCentral.submitCharacteristicWriteRequest(peerAddress, uuidFromBase("101"), nextPacket);
	    		}
	    		
			}  catch (Exception e) {
    			Log.v(TAG, "packet send error: " + e.getMessage());
    			bleStatusCallback.headsUp("m: packet send error");
    		}
			
			sent = i;
		}
		
		bleStatusCallback.headsUp("m: " + String.valueOf(sent+1) + " packets sent");
		
		m.ClearPendingPackets();
		
		
		// need to make sure I requeue all the packets if the other message doesn't know what happened
		bleStatusCallback.headsUp("m: finished send; checking if rcvd");
		
		bleCentral.submitCharacteristicReadRequest(peerAddress, uuidFromBase("105"));
		
		// should only call this after we're sure the message was sent
		//bleStatusCallback.messageSent(b.MessageHash, peer);
		
	}

	
	private UUID uuidFromBase(String smallUUID) {
		String strUUID =  uuidServiceBase.substring(0, 4) + new String(new char[4-smallUUID.length()]).replace("\0", "0") + smallUUID + uuidServiceBase.substring(8, uuidServiceBase.length());
		UUID idUUID = UUID.fromString(strUUID);
		
		return idUUID;
	}
	
	public void ShowFound() {
		// call our Central object to scan for devices
		bleCentral.scanLeDevice(true);
		
		// centralHandler's intakeFoundDevices method is called after scanning is complete
		// -- this then calls BleCentral's connectAddress for each found device
		// -- which then discovers services by calling gattServer.discoverServices()
		// -- if the service definition is met, then centralHandler's parlayWithRemote is called
	}
		
	public boolean BeFound() {
		
		try {
		
			/*
			blePeripheral.addChar(BleGattCharacteristics.GATT_READ, uuidFromBase("100"), peripheralHandler);
			blePeripheral.addChar(BleGattCharacteristics.GATT_WRITE, uuidFromBase("101"), peripheralHandler);
			blePeripheral.addChar(BleGattCharacteristics.GATT_NOTIFY, uuidFromBase("102"), peripheralHandler);
			*/
			
			// pull from the service definition
			for (BleCharacteristic c: serviceDef) {
				blePeripheral.addChar(c.type, c.uuid, peripheralHandler);
			}
			
			// advertising doesn't take much energy, so go ahead and do it
			blePeripheral.advertiseNow();
			return true;
		} catch (Exception e) {
			return false;
		}
		
	}
	
	public void HideYourself() {
		blePeripheral.advertiseOff();
	}
	
    /**
     * Adds a friend from the calling application to the list of peers that BleMessenger keeps an eye out for.  When you
     * reference this particular peer in the future, use the method GetBlePeerByFingerprint() or GetBlePeerByRemoteAddress()
     * 
     * @param BlePeer instance of a BlePeer you want BleMessenger to keep an eye out for
     * 
     */
	public void PutMessageForFriend(String FriendFingerprint, BleMessage MessageToSend) {
		
		// try to get this peer by their fingerprint
		BlePeer p = friendsFpMap.get(FriendFingerprint);
		
		// if this peer isn't found, then create him
		if (p == null) {
			p = new BlePeer("");
			p.SetFingerprint(FriendFingerprint);
			friendsFpMap.put(FriendFingerprint, p);
		}
		
		// add the message
		p.addBleMessageOut(MessageToSend);
		
	}
	
	// this sends out when you're in PERIPHERAL mode
    private void sendOutgoing(String remote, UUID uuid) {
    	
    	// if we've got messages to send
    	if (bleMessageMap.size() > 0) {
    	
    		// get the current message to send
	    	BleMessage b = bleMessageMap.get(CurrentParentMessage);
	    	
			if (b == null) {
				Log.v(TAG, "cannot 'sendOutgoing' - bleMessageMap.get(CurrentParentMessage) returned null");
				bleStatusCallback.headsUp("m: (sendOutgoing) no message found for peer");
				
				return;
			}
	    	
			// get an array of all our packets
			SparseArray<BlePacket> bps = b.GetAllPackets();
			
			bleStatusCallback.headsUp("m: start sending packets for msg " + String.valueOf(CurrentParentMessage));
			
			// loop over all our packets to send
			for (int i = 0; i < bps.size(); i++) {
				
				BlePacket p  = bps.valueAt(i);

				
				try {
					
					byte[] nextPacket = p.MessageBytes;
					
					Log.v(TAG, "send write request via " + uuid.toString());
					
		    		if (nextPacket != null) {
		    			//bleStatusCallback.headsUp("m: o:" + ByteUtilities.bytesToHexShort(nextPacket));

		    			// update the value of this characteristic, which will send to subscribers
				    	blePeripheral.updateCharValue(uuid, nextPacket);
		    		}
		    		
				}  catch (Exception e) {
	    			Log.v(TAG, "packet send error: " + e.getMessage());
	    			bleStatusCallback.headsUp("m: packet send error");
	    		}
				
			}
			
			bleMessageMap.remove(CurrentParentMessage);

			bleStatusCallback.headsUp("m: message " + ByteUtilities.bytesToHex(b.MessageHash) + " sent, remove from map");

			/*
			byte[] MsgType;
			
			if (b.MessageType == "identity") {
				MsgType = new byte[]{(byte)(0x01)};
			} else {
				MsgType = new byte[]{(byte)(0x02)};
			}
			
			bleStatusCallback.headsUp("m: message " + ByteUtilities.bytesToHex(MsgType) + ByteUtilities.bytesToHex(b.RecipientFingerprint) + ByteUtilities.bytesToHex(b.SenderFingerprint) + ByteUtilities.bytesToHex(b.MessagePayload) + " sent, remove from map");
			*/
	    	CurrentParentMessage++;
	
	    	
    	}
    	// TODO: consider when to disconnect
    	
    }

    private void incomingMessage(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
		int parentMessagePacketTotal = 0;
		
		//Log.v(TAG, "incoming hex bytes:" + ByteUtilities.bytesToHex(incomingBytes));
		//bleStatusCallback.headsUp("m: i:" + ByteUtilities.bytesToHexShort(incomingBytes));
		
		// if our msg is under a few bytes it can't be valid; return
    	if (incomingBytes.length < 5) {
    		Log.v(TAG, "message bytes less than 5");
    		return;
    	}

    	// get the Message to which these packets belong as well as the current counter
    	int parentMessage = incomingBytes[0] & 0xFF; //00
    	int packetCounter = (incomingBytes[1] << 8) | incomingBytes[2] & 0xFF; //0001

    	// get the peer which matches the connected remote address 
    	BlePeer p = peerMap.get(remoteAddress);
    	
    	// update "last heard from" time
    	p.MarkActive();
    	
    	// find the message we're building, identified by the first byte (cast to an integer 0-255)
    	// if this message wasn't already created, then the getBleMessageIn method will create it
    	bleStatusCallback.headsUp("m: msg " + String.valueOf(parentMessage) + ", pckt " + String.valueOf(packetCounter));
    	BleMessage b = p.getBleMessageIn(parentMessage);
    	
    	// your packet payload will be the size of the incoming bytes less our 3 needed for the header (ref'd above)
    	byte[] packetPayload = Arrays.copyOfRange(incomingBytes, 3, incomingBytes.length);
    	
    	// if our current packet counter is ZERO, then we can expect our payload to be:
    	// the number of packets we're expecting
    	if (packetCounter == 0) {
    		// right now this is only going to be a couple of bytes
    		parentMessagePacketTotal = (incomingBytes[3] << 8) | incomingBytes[4] & 0xFF; //0240
    		
    		Log.v(TAG, "parent message packet total is:" + String.valueOf(parentMessagePacketTotal));
    		b.BuildMessageFromPackets(packetCounter, packetPayload, parentMessagePacketTotal);
    	} else {
    		//bleStatusCallback.headsUp("m: building msg with packet #" + String.valueOf(packetCounter));
    		
    		// otherwise throw this packet payload into the message
    		b.BuildMessageFromPackets(packetCounter, packetPayload);	
    	}
    	
    	// if this particular message is done; ie, is it still pending packets?
    	if (b.PendingPacketStatus() == false) {
    		bleStatusCallback.headsUp("m: pending packet status now false, all packets rcvd");
    		
    		// if there's a fingerprint for the sender, handle this message (you should always have a sender fingerprint, else the message is malformed)
    		
    		// Add the sender's fingerprint to our map of senderfingerprint and remoteAddress
    		// Should this be elsewhere?
    		if (b.SenderFingerprint != null) {
	    		if (b.SenderFingerprint.length > 0) {
	    			bleStatusCallback.headsUp("m: sender has fp, handling msg");
	    			
	    			if (b.checkHash()) {
	    				bleStatusCallback.headsUp("m: hash good");
	    			} else {
	    				bleStatusCallback.headsUp("m: hash bad: " + b.GetCalcHash());
	    				bleStatusCallback.headsUp("m: shouldbe: " + ByteUtilities.bytesToHexShort(b.MessageHash));
	    			}
	    			
	    			// we don't want to overwrite the friend we put in earlier, but if it's not
	    			// already in friendsFpMap then add
	    			if (friendsFpMap.get(ByteUtilities.bytesToHex(b.SenderFingerprint)) == null) {
	    				friendsFpMap.put(ByteUtilities.bytesToHex(b.SenderFingerprint), p);
	    			}
	    			
	    			bleStatusCallback.handleReceivedMessage(remoteAddress, ByteUtilities.bytesToHex(b.RecipientFingerprint), ByteUtilities.bytesToHex(b.SenderFingerprint), b.MessagePayload, b.MessageType);
	    			
	    		} else {
	    			bleStatusCallback.headsUp("m: msg error: SenderFingerprint.length=0");	
	    		}
    		} else {
    			bleStatusCallback.headsUp("m: msg error: SenderFingerprint NULL");
    		}
    		
    		// check message integrity here?
    		// what about encryption?
    		
    		// how do i parse the payload if the message contains handshake/identity?
    	}
    	
		
    }
    
    public void checkSendStatus(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    	// get the remote peer based on the address
    	//bleStatusCallback.headsUp("m: hearing back from " + remoteAddress + " about a message's status");
    	
    	BlePeer p = peerMap.get(remoteAddress);
    	
    	if (p==null) {
    		bleStatusCallback.headsUp("m: no peer found for address " + remoteAddress);
    		return;
    	}
    	
    	/*
    	for (String s: peerMap.keySet()) {
    		bleStatusCallback.headsUp("m: peerMap key found: " + s);	
    	}*/
    	
    	// get the message ID to check on
    	int msg_id = incomingBytes[0] & 0xFF;
    	
    	bleStatusCallback.headsUp("m: hearing from " + remoteAddress + " re msg#" + String.valueOf(msg_id));
    	
    	// instantiate a message object
    	BleMessage m = null;
    	
    	// get all the outbound messages in an array
    	SparseArray<BleMessage> blms = p.GetMessagesOut();
    	
    	// get the message corresponding to this msgid
    	m = blms.get(msg_id);
    	
    	bleStatusCallback.headsUp("m: msg currently has " + String.valueOf(m.GetPendingPackets().size()) + " packets to send");

		if (m != null) {
			bleStatusCallback.headsUp("m: bleMessage found at index " + String.valueOf(msg_id));

			// how many missing packets?  if 0, we're all set; call it done
	    	int missing_packet_count = incomingBytes[1] & 0xFF;
	    	
	    	// if we're all done, mark this message sent
	    	if (missing_packet_count == 0) {
	    		bleStatusCallback.headsUp("m: all sent, removing msg " + String.valueOf(msg_id) + " from queue") ;
	    		p.RemoveBleMessage(msg_id);
	    		//bleStatusCallback.headsUp("m: GetMessageOut.size():" + String.valueOf(p.GetMessagesOut().size()));
	    	} else {
	    		// read the missing packet numbers into an array
	    		byte[] missingPackets = Arrays.copyOfRange(incomingBytes, 2, incomingBytes.length);
	    		bleStatusCallback.headsUp("m: " + String.valueOf(missingPackets.length) + " packet(s) didn't make it");
	    		
	    		for (byte b: missingPackets) {
	    			int missing_packet = b & 0xFF;
	    			bleStatusCallback.headsUp("m: re-queuing packet #" + String.valueOf(missing_packet));
	    			try {
	    				if (m.PacketReQueue(missing_packet)) {
	    					bleStatusCallback.headsUp("m: packet re-queued");
	    				} else {
	    					bleStatusCallback.headsUp("m: packet not re-queued");
	    				}
	    			} catch (Exception x) {
	    				bleStatusCallback.headsUp("m: error calling BleMessage.PacketRequeue(" + String.valueOf(missing_packet) + ")");
	    				Log.v(TAG, x.getMessage());
	    			}
	    			
	    		}
	    		
	    		bleStatusCallback.headsUp("m: message now has " + String.valueOf(m.GetPendingPackets().size()) + " packets to send");
	    		
	    		// flag these packets for re-send
	    		
	    	}
    	
    	} else {
    		bleStatusCallback.headsUp("m: p.getBleMessageOut(" + String.valueOf(msg_id) + ") doesn't pull up a blemessage");
    	}
    }
    
    
    BlePeripheralHandler peripheralHandler = new BlePeripheralHandler() {
    	
    	
    	public void ConnectionState(String device, int status, int newStatus) {
    		
    		// if connected
    		if (newStatus == 2) {
    		
	    		// create/reset our message map for our connection
	    		bleMessageMap =  new HashMap<Integer, BleMessage>();
	    		
	    		// this global "CurrentParentMessage" thing only works when sending in peripheral mode
	    		CurrentParentMessage = 0;
	    		
	    		// the message itself needs to know what its sequence is when sent to recipient
	    		idMessage.SetMessageNumber(CurrentParentMessage);
	    		
	    		// add our id message to this message map
	    		bleMessageMap.put(0, idMessage);
	    		
	    		Log.v(TAG, "id message added to connection's message map");
	    		
	    		bleStatusCallback.headsUp("m: accepted connection from a central");
	    		
	    		 // create a new peer to hold messages and such for this network device
	    		BlePeer p = new BlePeer(device);
	    		p.ConnectedAs = "peripheral";
	    		peerMap.put(device, p);

    		}
    		
    	}

    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		// based on remoteAddress, UUID of remote characteristic, put the incomingBytes into a Message
    		// probably need to have a switchboard function
    		incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);
    			
    	}

		@Override
		public void handleNotifyRequest(String device, UUID uuid) {
    		// we're connected, so initiate send to "device", to whom we're already connected
    		Log.v(TAG, "from handleNotifyRequest, initiate sending messages");
    		
    		// we've got a notify request, so let's reset this peer's inactivity timeout
    		bleStatusCallback.headsUp("m: notify request from " + device + "; reset timeout");
    		BlePeer p = peerMap.get(device);
    		p.MarkActive();
    		
    		sendOutgoing(device, uuid);
		}

		@Override
		public void handleAdvertiseChange(boolean advertising) {
			if (advertising) {
				bleStatusCallback.advertisingStarted();
			} else {
				bleStatusCallback.advertisingStopped();
			}
		}

		@Override
		public void prepReadCharacteristic(String remoteAddress, UUID remoteCharUUID) {
			// figure out whom i'm talking to based on remoteAddress
			// BT 4.1 allows multiple centrals, so you need to keep remoteAddress to tell which one
			
			// if somebody's hitting 105 they're gonna wanna know if their msg is sent or not
			if (remoteCharUUID.toString().equalsIgnoreCase(uuidFromBase("105").toString())) {
				
				// get the peer who just asked us if we have any incomplete messages
				BlePeer p = peerMap.get(remoteAddress);
				
				// iterate over all the messages we have
				bleStatusCallback.headsUp("m: checking " + String.valueOf(p.GetMessageIn().size()) + " message(s) from this peer");
				
				// loop over the inbound message numbers (even though we're only doing the first)
				for (int k: p.GetMessageIn().keySet()) {
					
					// get the first message
					BleMessage m = p.getBleMessageIn(k);

					if (!m.ReceiptAcknowledged) {
						// see if we've got any missing packets
						ArrayList<Integer> missing = m.GetMissingPackets();
						
						// create an array
						byte[] missingPackets = new byte[missing.size()+2];
						
						// first byte will be message identifier
						missingPackets[0] = Integer.valueOf(k).byteValue();
						
						// second byte will be number of missing packets
						missingPackets[1] = Integer.valueOf(missing.size()).byteValue();
						
						bleStatusCallback.headsUp("m: msg #" + String.valueOf(k) + " lacks " + String.valueOf(missing.size()) + " packets");
						
						// subsequent bytes are those that are missing!
						int counter = 2;
						for (Integer i: missing) {
							missingPackets[counter] = i.byteValue();
							counter++;
						}
						
						// if we still need packets
						blePeripheral.updateCharValue(remoteCharUUID, missingPackets);
						
						// should we do this here - to support one message going to multiple folks,
						// you may want to have a different array/map to check
						// however we could presribe this to be for only single-destination messages
						
						if (missing.size() == 0) { 
							m.ReceiptAcknowledged = true;
						}
						
						break;
					} else {
						bleStatusCallback.headsUp("m: msg " + String.valueOf(k) + " has already been ack'd");
					}

				}
			}
		}
    	
    };


    BleCentralHandler centralHandler = new BleCentralHandler() {
    	
    	@Override
    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		
    		// if the UUID is 102, it's the notify
    		if (remoteCharUUID.compareTo(uuidFromBase("102")) == 0) {
    			incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);    			
    		} else if (remoteCharUUID.compareTo(uuidFromBase("105")) == 0) {
    			checkSendStatus(remoteAddress, remoteCharUUID, incomingBytes);
    		}
    		
    	}
    	
		@Override
		public void intakeFoundDevices(ArrayList<BluetoothDevice> devices) {
			bleStatusCallback.headsUp("m: stopped scanning");
			// loop over all the found devices
			// add them 
			for (BluetoothDevice b: devices) {
				Log.v(TAG, "(BleMessenger)MyGattClientHandler found device:" + b.getAddress());
				
				
				// if the peer isn't already in our list, put them in!
				String peerAddress = b.getAddress();
				
				if (!peerMap.containsKey(peerAddress)) {
					BlePeer blePeer = new BlePeer(peerAddress);
					blePeer.ConnectedAs = "central";
					peerMap.put(peerAddress, blePeer);
				}
				
				// this could possibly be moved into an exterior loop
				bleStatusCallback.headsUp("m: connecting to " + peerAddress);
				
				// initiate a connection to the remote address, which checks to see if it's a valid peer
				// if it's not, then you won't be connected
				bleCentral.connectAddress(peerAddress);
				
			}
			
		}
		
		@Override
		public void connectedServiceGood(String remoteAddress) {
			// so now we're connected; not going to do anything else yet
			// so we literally have no info about this particular person
			bleStatusCallback.headsUp("m: connected and ready to exchange w/ " + remoteAddress);
			BlePeer p = peerMap.get(remoteAddress);
			
			// since we're parlaying, reset our timeout timer
			p.MarkActive();
			
			bleStatusCallback.peerNotification(remoteAddress, "new_contract");
			
			// can't get the PuF because we haven't done the ID dance
		}
		
		
		@Override
		public void reportDisconnect() {
			// what to do when disconnected?
			
		}
    	
    };
    
    
    // if you're a central, identify a particular peripheral
    // this function probably won't be called directly
	public void getPeripheralIdentifyingInfo(String remoteAddress) {
		
		// so at this point we should still be connected with our remote device
		// and we wouldn't have gotten here if the remote device didn't meet our service spec

		
		// why am i adding this blank message to my messagemap?
		// so now let's ask for identification information - SUBSEQUENTLY we may transfer other data
		BleMessage b = new BleMessage();
		
		// add this new message to our message map
		// ONLY WORKS FOR ONE CONNECTED PERIPHERAL; need to make per remoteAddress
		bleMessageMap.put(0, b);

		// pass our remote address and desired uuid to our gattclient
		// who will look up the gatt object and uuid and issue the read request
		bleStatusCallback.headsUp("m: subscribing to 102 on " + remoteAddress);
		bleCentral.submitSubscription(remoteAddress, uuidFromBase("102"));

		// we should be expecting data on 102 now
		
	}
    
}
