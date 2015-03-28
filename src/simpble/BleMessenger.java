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
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

public class BleMessenger {
	private static String TAG = "BLEM";
	private static int INACTIVE_TIMEOUT = 600000; // 5 minute timeout
	private static int BUSINESS_TIMEOUT = 5000; // 5 second timeout
	
	public static final int MSGTYPE_ID = 1;
	public static final int MSGTYPE_PLAIN = 2;
	public static final int MSGTYPE_ENCRYPTED_PAYLOAD = 20;
	public static final int MSGTYPE_ENCRYPTED_KEY = 21;
	public static final int MSGTYPE_DROP = 90;
	
	private boolean StayingBusy;
	
	private Timer longTimer;
	private Timer businessTimer;
	
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

    // allows us to look up peers by connected addresses
    public Map<String, BlePeer> peerMap;
    
    private Map<String, String> messageMap;
    
    // our idmessage should stay the same, so save it in a global variable
    // allow to be set from calling functions
	public BleMessage idMessage;
	
	public boolean SupportsAdvertising;
    
    private List<BleCharacteristic> serviceDef;
    
    private boolean areWeSendingMessages;
	
    /**
     * Instantiates serviceDef arraylist, peerMap, fpNetMap, creates handles for peripheral/central,
     * populates serviceDef
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
		StayingBusy = true;
		

		blePeripheral = null;
		
		if (Build.VERSION.SDK_INT < 21) {
			SupportsAdvertising = false;
		} else {			
			// check if we support advertising or not
			if (!btAdptr.isMultipleAdvertisementSupported()) {
				SupportsAdvertising = false;
			} else {
				SupportsAdvertising = true;			
			}
		}
		
		//if (btAdptr.getName().equalsIgnoreCase("Nexus 5")) {
		//			SupportsAdvertising = false;
		//}
		
		serviceDef = new ArrayList<BleCharacteristic>();
		
		// i need a place to put my found peers
		peerMap = new HashMap<String, BlePeer>();
		
		// when you start delivering a message, get an identifier so you can tell the calling application when you've delivered it
		messageMap = new HashMap<String, String>();
		
		// create your server for listening and your client for looking; Android can be both at the same time
		
		if (SupportsAdvertising) {
			blePeripheral = new BlePeripheral(uuidServiceBase, ctx, btAdptr, btMgr, peripheralHandler);
		}
		bleCentral = new BleCentral(btAdptr, ctx, centralHandler, uuidServiceBase, 3000);
		
		serviceDef.add(new BleCharacteristic("identifier_read", uuidFromBase("100"), BleGattCharacteristics.GATT_READ));		
		serviceDef.add(new BleCharacteristic("identifier_writes", uuidFromBase("101"), BleGattCharacteristics.GATT_WRITE));
		serviceDef.add(new BleCharacteristic("data_notify", uuidFromBase("102"), BleGattCharacteristics.GATT_INDICATE));
		serviceDef.add(new BleCharacteristic("flow_control", uuidFromBase("105"), BleGattCharacteristics.GATT_READWRITE));
		//serviceDef.add(new BleCharacteristic("data_indicate", uuidFromBase("103"), MyAdvertiser.GATT_INDICATE));
		//serviceDef.add(new BleCharacteristic("data_write", uuidFromBase("104"), MyAdvertiser.GATT_WRITE));

		bleCentral.setRequiredServiceDef(serviceDef);

		areWeSendingMessages = false;
		
		setupStaleChecker(INACTIVE_TIMEOUT);  // setup timeout
		setupBusinessTimer(BUSINESS_TIMEOUT); // every second make sure that we're all stayin' busy
		
	
		// when we connect, send the id message to the connecting party
	}
	
	/**
	 * Send all the messages to the passed in Peer
	 * @param Peer
	 */
	private void sendMessagesToPeer(BlePeer p) {
		// right here i need to catch whether or not my peer has subscribed if i am connected as a peripheral
		
		// if you're connected to this peer as a peripheral and this peer isn't subscribed to anything, then exit 
		if (p.ConnectedAs.equalsIgnoreCase("peripheral") && p.subscribedChars.length() < 1) {
			bleStatusCallback.headsUp("m: connected as perp, nothing is subscribed");
			return;
		}
		
		if (p.PendingMessageCount() > 0) {
			bleStatusCallback.headsUp("m: found " + String.valueOf(p.PendingMessageCount()) + " msgs for peer " + p.RecipientAddress());
			
			// is your peer connected as a peripheral or a central?
			if (p.ConnectedAs.equalsIgnoreCase("central")) {
				// if you're a central, send as such
				bleStatusCallback.headsUp("m: you're a central and can initiate a send, congrats");
				writeOut(p.RecipientAddress());
			} else if (p.ConnectedAs.equalsIgnoreCase("peripheral")) {
				// if you're a peripheral send as such
				/* you'll need to know which attribute to write on
				 * if you're hoping to use a notify characteristic, they'll need to be subscribed to it
				*/
				// how to tell if this peer is subscribed?
				bleStatusCallback.headsUp("m: begin peripheral send to " + p.RecipientAddress());

				writeOut(p.RecipientAddress());
				
			}
		} else {
			bleStatusCallback.headsUp("m: no more messages for peer: " + p.RecipientAddress());
		}

	}
	
	public BlePeer GetBlePeerByAddress(String remoteAddress) {
		
		bleStatusCallback.headsUp("m: check peerMap for remote: " + remoteAddress);
		
		BlePeer p = peerMap.get(remoteAddress);
		
		return p;
	}

	
	public void SendMessagesToConnectedPeers() {
		//setupBusinessTimer(BUSINESS_TIMEOUT);

		//Log.v(TAG, "LoopSendMessages on thread " + Thread.currentThread().getName());	
			
		// loop over all the peers i have that i'm connected to	
		for (BlePeer p: peerMap.values()) {
			
			// get the first message i see
			BleMessage m = p.getBleMessageOut();
			
			// funny, we're not actually sending a particular message per se, even though we asked for a particular message
			// we're calling a method to send any pending messages for a particular peer
			// mainly because we don't store the identifier for the peer in a particular BleMessage (although we could?)
			if (m != null) {
				Log.v("DOIT", "pulled msg #" + String.valueOf(m.GetMessageNumber()) + ", " + ByteUtilities.bytesToHex(m.PayloadDigest).substring(0,8));
				areWeSendingMessages = true;
				sendMessagesToPeer(p);
			}

		}
	}
	
	private synchronized void setupBusinessTimer(long youStillBusy) {
		if (businessTimer != null) {
			businessTimer.cancel();
			businessTimer = null;
		}
		
		if (businessTimer == null && StayingBusy) {
			
			bleStatusCallback.headsUp("m: business timer reset!");
			businessTimer = new Timer();
			
			businessTimer.schedule(new TimerTask() {
				public void run() {
					businessTimer.cancel();
					businessTimer = null;
					
					SendMessagesToConnectedPeers();
				}
				
			}, youStillBusy);
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
				
			}, timeout);
		}
	}
	
	public void StartBusy() {
		StayingBusy = true;
		setupBusinessTimer(BUSINESS_TIMEOUT);
	}
	
	public void StopBusy() {
		StayingBusy = false;
	}
	
	public boolean BusyStatus() {
		return StayingBusy;
	}
	
	private void checkForStaleConnections() {
		//bleStatusCallback.headsUp("m: check for stale connection!");
		// reset our stale-checker
		setupStaleChecker(INACTIVE_TIMEOUT);
		
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

	private void writeOut(String peerAddress) {
		UUID uuid = uuidFromBase("102");
		writeOut(peerAddress, uuid);
	}
	
	//TODO: change writeOut to indicate which particular message you're sending out
	private void writeOut(String peerAddress, UUID uuid) {

		// if i'm connected to you as a peripheral, and you are not yet subscribed, i should not be sending you
		// any damn messages
		
		// look up the peer by their address (aka index)
		BlePeer peer = peerMap.get(peerAddress);
		
		// given a peer, get the first message in the queue to send out
		BleMessage m = peer.getBleMessageOut();
		
		// add (or update) a mapping of the digest of this message to a way to get it
		messageMap.put(peerAddress + "_" + m.GetMessageNumber(), ByteUtilities.bytesToHex(m.PayloadDigest));
			
		// the previous call allows us to get the current message
		bleStatusCallback.headsUp("m: sending message: " + String.valueOf(m.GetMessageNumber()));

		// get a sparsearray of the packets pending send for the message m
		SparseArray<BlePacket> bps = m.GetPendingPackets();
		
		bleStatusCallback.headsUp("m: # of pending packets: " + String.valueOf(bps.size()));
		int sent = 0;
		int i = 0;
		
		// loop over all our packets to send
		for (i = 0; i < bps.size(); i++) {
			
			BlePacket p = bps.valueAt(i);
			
			try {
		
				byte[] nextPacket = p.MessageBytes;
				boolean flag_sent = false;
				
	    		if (nextPacket != null) {
	    			if (peer.ConnectedAs.equalsIgnoreCase("central")) {
	    				Thread.sleep(100);
	    				flag_sent = bleCentral.submitCharacteristicWriteRequest(peerAddress, uuidFromBase("101"), nextPacket);
	    				Log.v(TAG, "writing packet #" + i);
	    			} else {
	    				flag_sent = blePeripheral.updateCharValue(uuid, nextPacket);
	    			}
	    		}
	    		
	    		// if the particular packet was sent, increment our "sent" counter
	    		if (flag_sent) {
	    			sent++;
	    		}
	    		
			}  catch (Exception e) {
    			Log.e(TAG, "packet send error: " + e.getMessage());
    			bleStatusCallback.headsUp("m: packet send error");
    		}
			
		}
		
		// well now we've sent some messages
		areWeSendingMessages = false;
		
		bleStatusCallback.headsUp("m: " + String.valueOf(sent) + " packets sent");
		
		// we sent packets out, so clear our pending list (we'll requeue missing later)
		m.ClearPendingPackets();

		RequestAcknowledgment(peer);
		
		
	}
	
	private String GetAddressForPeer(BlePeer p) {
		String peerAddress = "";
		
		for (Entry<String, BlePeer> entry : peerMap.entrySet()) {
			if (p.equals(entry.getValue())) {
				peerAddress = entry.getKey();
			}
		}
		
		return peerAddress;
	}
	
	private boolean RequestAcknowledgment(BlePeer p) {
		// TODO: this part about checking needs to be set from the calling application
		boolean request_sent = false;
		
		String peerAddress = GetAddressForPeer(p);
		
		if (p.ConnectedAs.equalsIgnoreCase("central")) { 
			if (p.TransportTo) {
				bleCentral.submitCharacteristicReadRequest(peerAddress, uuidFromBase("105"));
				request_sent = true;
			}

		} else {
			if (p.TransportTo) {
				//TODO: peripheral needs to send message to central requesting acknowledgment which central will write to 105
				request_sent = true;
			}
		}
		
		// next action happens when processMessageSendAcknowledgment is called back
		
		return request_sent;
		
	}

	
	private UUID uuidFromBase(String smallUUID) {
		String strUUID =  uuidServiceBase.substring(0, 4) + new String(new char[4-smallUUID.length()]).replace("\0", "0") + smallUUID + uuidServiceBase.substring(8, uuidServiceBase.length());
		UUID idUUID = UUID.fromString(strUUID);
		
		return idUUID;
	}
	
	public void ScanForPeers(int duration) {
		
		bleCentral.setScanDuration(duration);
		// call our Central object to scan for devices
		bleCentral.scanForPeripherals(true);
		
		// centralHandler's intakeFoundDevices method is called after scanning is complete
		// -- this then calls BleCentral's connectAddress for each found device
		// -- which then discovers services by calling gattServer.discoverServices()
		// -- if the service definition is met, then centralHandler's parlayWithRemote is called
	}
		
	public boolean BeFound() {
		
		
		try {
			
			// pull from the service definition
			for (BleCharacteristic c: serviceDef) {
				blePeripheral.addChar(c.type, c.uuid, peripheralHandler);
			}
			
			// advertising doesn't take much energy, so go ahead and do it
			return blePeripheral.advertiseNow();
		} catch (Exception e) {
			return false;
		}
		
	}
	
	public void HideYourself() {
		blePeripheral.advertiseOff();
	}
	
	/**
	 * Takes the bytes of each incoming packet and assigns them to a message
	 * 
	 * @param remoteAddress The Bluetooth address of the device sending the packets to this one
	 * @param remoteCharUUID The UUID of the GATT characteristic being used for transport
	 * @param incomingBytes The raw bytes of the incoming packet
	 */
    private void incomingMessage(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
		int parentMessagePacketTotal = 0;
		
		Log.v(TAG, ByteUtilities.bytesToHex(incomingBytes));
		
		// if our msg is under a few bytes it can't be valid; return
    	if (incomingBytes.length < 5) {
    		Log.v(TAG, "message bytes less than 5");
    		return;
    	}

    	// Process the header of this packet, whicg entails parsing out the parentMessage and which packet this is in the message
    	
    	// get the Message to which these packets belong as well as the current counter
    	int parentMessage = incomingBytes[0] & 0xFF; //00
    	int packetCounter = (incomingBytes[1] << 8) | incomingBytes[2] & 0xFF; //0001

    	// get the peer which matches the connected remote address 
    	BlePeer p = peerMap.get(remoteAddress);
    	
    	// update "last heard from" time
    	p.MarkActive();
    	
    	// find the message we're building, identified by the first byte (cast to an integer 0-255)
    	// if this message wasn't already created, then the getBleMessageIn method will create it
    	BleMessage msgBeingBuilt = p.getBleMessageIn(parentMessage);
    	
    	// your packet payload will be the size of the incoming bytes less our 3 needed for the header
    	byte[] packetPayload = Arrays.copyOfRange(incomingBytes, 3, incomingBytes.length);
    	
    	// if our current packet counter is ZERO, then we can expect our payload to be:
    	// the number of packets we're expecting
    	if (packetCounter == 0) {

    		// get the number of packets we're expecting - 2 bytes can indicate 0 through 65,535 packets 
    		parentMessagePacketTotal = (packetPayload[0] << 8) | packetPayload[1] & 0xFF;
    		
    		// since this is the first packet in the message, pass in the number of packets we're expecting
    		msgBeingBuilt.BuildMessageFromPackets(packetCounter, packetPayload, parentMessagePacketTotal);
    	} else {
    		
    		// otherwise throw this packet payload into the message
    		msgBeingBuilt.BuildMessageFromPackets(packetCounter, packetPayload);	
    	}
    	
    	// If all the expected packets have been received, process this message
    	if (msgBeingBuilt.PendingPacketStatus() == false) {
    		
    		
    		// we can do this before we're even asked about it
    		if (p.ConnectedAs.equalsIgnoreCase("central")) {
    			
    			Log.v(TAG, "parentMessage # is:" + parentMessage);
    			
    			// what message are we talking about?
    			//byte[] ACKet = ByteUtilities.intToByte(parentMessage);
    			
    			byte[] ACKet = new byte[] {(byte)parentMessage};
    			
    			// create an acknowledgment packet with only 0's, indicating we got it all
    			byte[] ack = new byte[20];
    			ack = Arrays.copyOf(ACKet, ack.length);
    			
    			//bleCentral.submitCharacteristicWriteRequest(remoteAddress, uuidFromBase("105"));
    			bleCentral.submitCharacteristicWriteRequest(remoteAddress, uuidFromBase("105"), ack);
    		} else {
    			// when peripheral receives a message, we wait until asked
    		}
    		
    		
    		// TODO: do we need to have a hash check?
    		bleStatusCallback.handleReceivedMessage(remoteAddress, msgBeingBuilt.GetAllBytes());
    		
    		

    	} else {
    		// TODO: we need to have some kind of deal where we set a timer, and if no more packets are received for this message then we . . . do something?
    		// or we just clear out this half-built message and give up on ever getting it?
    		// or clear it out completely - if the sender wants to make sure we've got it, they'all ask
    	}
    	
		
    }
    
    public void processMessageSendAcknowledgment(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    	// get the remote peer based on the address
    	
    	BlePeer p = peerMap.get(remoteAddress);
    	
    	if (p==null) {
    		bleStatusCallback.headsUp("m: no peer found for address " + remoteAddress);
    		return;
    	}
    	
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
			
			String payloadDigest = messageMap.get(remoteAddress + "_" + msg_id);
			
			bleStatusCallback.headsUp("m: bleMessage found at index " + String.valueOf(msg_id));

			// how many missing packets?  if 0, we're all set; call it done
	    	int missing_packet_count = incomingBytes[1] & 0xFF;
	    	
	    	// if we're all done, mark this message sent
	    	if (missing_packet_count == 0) {
	    		bleStatusCallback.headsUp("m: all sent, removing msg " + String.valueOf(msg_id) + " from queue") ;

	    		bleStatusCallback.messageDelivered(remoteAddress, payloadDigest);
	    		p.RemoveBleMessage(msg_id);
	    		
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
    	
    	
    	/**
    	 * The Gatt Peripheral handler provides an update for the connection state; here we handle that
    	 * If we're connected
    	 */
    	public void ConnectionState(String device, int status, int newStatus) {

    		BlePeer p = new BlePeer(device);
    		
    		// if connected
    		if (newStatus == BluetoothProfile.STATE_CONNECTED) {
    			
	    		Log.v(TAG, "add id message to connection's message map");
	    		
	    		bleStatusCallback.headsUp("m: accepted connection from a central");
	    		
	    		 // create a new peer to hold messages and such for this network device
	    		p.ConnectedAs = "peripheral";
	    		
	    		peerMap.put(device, p);
	    		
	    		// if we've been connected to, we can assume the central can write out to us
	    		p.TransportFrom = true;
	    		// we can't set TransportTo, because we haven't been subscribed to yet
	    		
	    		// we also need to indicate to the calling application that this peer has just connected,
	    		// so that we can do some "this dude has just connected!" types of things
	    		
    		} else {
	    		// let the calling activity know that as a peripheral, we've lost or connection
    			bleStatusCallback.peerDisconnect(device);
    			p.TransportFrom = false;
    			p.TransportTo = false;
    		}
    		
    	}

    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		// based on remoteAddress, UUID of remote characteristic, put the incomingBytes into a Message
    		
    		if (remoteCharUUID.compareTo(uuidFromBase("105")) == 0) {
    			processMessageSendAcknowledgment(remoteAddress, remoteCharUUID, incomingBytes);    			
    		} else  {
    			incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);	
    		}
    			
    	}

		@Override
		public void handleNotifyRequest(String device, UUID uuid) {
    		// we're connected, so initiate send to "device", to whom we're already connected
    		Log.v(TAG, "from handleNotifyRequest, initiate sending messages");
    		
    		// we've got a notify request, so let's reset this peer's inactivity timeout
    		bleStatusCallback.headsUp("m: notify request from " + device + "; reset timeout");
    		BlePeer p = peerMap.get(device);
    		p.MarkActive();
    		p.subscribedChars = uuid.toString() + ";" + p.subscribedChars;
    		p.TransportTo = true;
    		
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

					// TODO: this can be its own function to be re-used for Central
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
    	public void subscribeSuccess(String remoteAddress, UUID remoteCharUUID) {
    		
    		bleStatusCallback.headsUp("TransportFrom:true");
    		BlePeer p  = peerMap.get(remoteAddress);
    		p.TransportFrom = true;
    		
    	}
    	
    	@Override
    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		
    		// if the UUID is 102, it's the notify
    		if (remoteCharUUID.compareTo(uuidFromBase("102")) == 0) {
    			incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);    			
    		} else if (remoteCharUUID.compareTo(uuidFromBase("105")) == 0) {
    			processMessageSendAcknowledgment(remoteAddress, remoteCharUUID, incomingBytes);
    		}
    		
    	}
    	
		@Override
		public void intakeFoundDevices(ArrayList<BluetoothDevice> devices) {
			bleStatusCallback.headsUp("m: stopped scanning");
			Log.v(TAG, "intake devices, thread "+ Thread.currentThread().getName());
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

			bleStatusCallback.headsUp("connectedServiceGood: submitSubscription");
			// get a handle to this peer
			BlePeer p = peerMap.get(remoteAddress);
			
			// reset our timeout timer
			p.MarkActive();
			
			// as a Central, when we know the service is good, we know we can send msgs to this peer
			p.TransportTo = true;
			
			// now let's subscribe so we can get inbound stuff
			
			// onDescriptorWrite callback will be called, which will call blecentralhandler's subscribeSuccess
			bleCentral.submitSubscription(remoteAddress, uuidFromBase("102"));
			
		}
		
		@Override
		public void reportDisconnect(String remoteAddress) {
			// report disconnection to MainActivity
			bleStatusCallback.peerDisconnect(remoteAddress);
		}
    	
    };
    
    /**
     * Add a message to the outgoing queue for a connected peer
     * 
     * @param remoteAddress The identifier for the target connectee; this is how SimpBle identifies the recipient
     * @param msg A BleApplicationMessage object with all the right stuff
     * @return A digest of the message's payload
     */
    public String AddMessage(String remoteAddress, BleApplicationMessage msg) {
    	String result = "";
		BlePeer p = peerMap.get(remoteAddress);
		
		// pass your message's bytes to the peer object to build a BleMessage to send, and get a digest of that message
		result = p.BuildBleMessageOut(msg.GetAllBytes());
    	
    	return result;
    	
    }
    
}
