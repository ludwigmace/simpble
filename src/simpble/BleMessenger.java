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
    
    // allows us to look up peers by connected addresses
    private Map<String, BlePeer> peerMap;
    
    // allows us to look up addresses based on fingerprint
    private Map<String, String> fpNetMap;
    
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
	
		fpNetMap = new HashMap<String, String>();
		
		// create your server for listening and your client for looking; Android can be both at the same time
		blePeripheral = new BlePeripheral(uuidServiceBase, ctx, btAdptr, btMgr, peripheralHandler);
		bleCentral = new BleCentral(btAdptr, ctx, centralHandler, uuidServiceBase, 3000);
		
		serviceDef.add(new BleCharacteristic("identifier_read", uuidFromBase("100"), BleGattCharacteristics.GATT_READ));		
		serviceDef.add(new BleCharacteristic("identifier_writes", uuidFromBase("101"), BleGattCharacteristics.GATT_WRITE));
		serviceDef.add(new BleCharacteristic("data_notify", uuidFromBase("102"), BleGattCharacteristics.GATT_NOTIFY));
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
	public void sendMessagesToPeer(BlePeer Peer) {
		bleStatusCallback.headsUp("sending messages meant for peer " + getFirstChars(Peer.GetFingerprint(), 20));
		writeOut(Peer);
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
		//bleStatusCallback.headsUp("check for stale connection!");
		// reset our stale-checker
		setupStaleChecker(INACTIVE_TIMEOUT); // 1 minute
		
		// loop over peers and check for staleness!
		for (Map.Entry<String, BlePeer> entry : peerMap.entrySet()) {
			BlePeer p = entry.getValue();
			bleStatusCallback.headsUp("is this guy current? connected as: " + p.ConnectedAs);
			
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
				
				bleStatusCallback.headsUp("closing stale connection");
				peerMap.remove(entry.getKey()); // because we've disconnected, remove from our peerlist
			}
			
		}

	}
	
	/* this is called when in central mode */
	private void writeOut(BlePeer peer) {
		
		// given a peer, get the first message in the queue to send out
		BleMessage b = peer.getBleMessageOut();
		
		if (b == null) {
			Log.v(TAG, "cannot 'writeOut' - peer.getBleMessageOut returned null");
			bleStatusCallback.headsUp("no message found for peer");
			
			return;
		}
		
		// pull the remote address for this peer
		String remoteAddress = fpNetMap.get(peer.GetFingerprint());
		
		// get an array list of all our packets
		ArrayList<BlePacket> bps = b.GetAllPackets();
		
		// loop over all our packets to send
		for (BlePacket p: bps) {
			
			try {
		
				byte[] nextPacket = p.MessageBytes;
				
				Log.v(TAG, "send write request to " + remoteAddress);
				
	    		if (nextPacket != null) {
	    			bleStatusCallback.headsUp("o:" + ByteUtilities.bytesToHexShort(nextPacket));
		    		bleCentral.submitCharacteristicWriteRequest(remoteAddress, uuidFromBase("101"), nextPacket);
	    		}
	    		
			}  catch (Exception e) {
    			Log.v(TAG, "packet send error: " + e.getMessage());
    			bleStatusCallback.headsUp("packet send error");
    		}
			
		}
		
		Log.v(TAG, "all pending packets sent");
		bleStatusCallback.headsUp("all pending packets sent (hopefully)");
		bleStatusCallback.messageSent(b.MessageHash, peer);
		
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
		
			// have this pull from the service definition
			blePeripheral.addChar(BleGattCharacteristics.GATT_READ, uuidFromBase("100"), peripheralHandler);
			blePeripheral.addChar(BleGattCharacteristics.GATT_WRITE, uuidFromBase("101"), peripheralHandler);
			blePeripheral.addChar(BleGattCharacteristics.GATT_NOTIFY, uuidFromBase("102"), peripheralHandler);
			
			//blePeripheral.updateCharValue(uuidFromBase("100"), new String(myIdentifier + "|" + myFriendlyName).getBytes());
			//blePeripheral.updateCharValue(uuidFromBase("101"), new String("i'm listening").getBytes());
			
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
	
	// this sends out when you're in peripheral mode
    private void sendOutgoing(String remote, UUID uuid) {
    	
    	// if we've got messages to send
    	if (bleMessageMap.size() > 0) {
    	
    		// get the current message to send
	    	BleMessage b = bleMessageMap.get(CurrentParentMessage);
	    	
			if (b == null) {
				Log.v(TAG, "cannot 'sendOutgoing' - bleMessageMap.get(CurrentParentMessage) returned null");
				bleStatusCallback.headsUp("no message found for peer");
				
				return;
			}
	    	
			// get an array list of all our packets
			ArrayList<BlePacket> bps = b.GetAllPackets();
	    	
			// loop over all our packets to send
			for (BlePacket p: bps) {
				
				try {
					
					byte[] nextPacket = p.MessageBytes;
					
					Log.v(TAG, "send write request via " + uuid.toString());
					
		    		if (nextPacket != null) {
		    			bleStatusCallback.headsUp("o:" + ByteUtilities.bytesToHexShort(nextPacket));

		    			// update the value of this characteristic, which will send to subscribers
				    	blePeripheral.updateCharValue(uuid, nextPacket);
		    		}
		    		
				}  catch (Exception e) {
	    			Log.v(TAG, "packet send error: " + e.getMessage());
	    			bleStatusCallback.headsUp("packet send error");
	    		}
				
			}
			
			bleMessageMap.remove(CurrentParentMessage);

			bleStatusCallback.headsUp("message " + ByteUtilities.bytesToHex(b.MessageHash) + " sent, remove from map");

			/*
			byte[] MsgType;
			
			if (b.MessageType == "identity") {
				MsgType = new byte[]{(byte)(0x01)};
			} else {
				MsgType = new byte[]{(byte)(0x02)};
			}
			
			bleStatusCallback.headsUp("message " + ByteUtilities.bytesToHex(MsgType) + ByteUtilities.bytesToHex(b.RecipientFingerprint) + ByteUtilities.bytesToHex(b.SenderFingerprint) + ByteUtilities.bytesToHex(b.MessagePayload) + " sent, remove from map");
			*/
	    	CurrentParentMessage++;
	
	    	
    	}
    	// TODO: consider when to disconnect
    	
    }

    private void incomingMessage(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
		int parentMessagePacketTotal = 0;
		
		//Log.v(TAG, "incoming hex bytes:" + ByteUtilities.bytesToHex(incomingBytes));
		bleStatusCallback.headsUp("i:" + ByteUtilities.bytesToHexShort(incomingBytes));
		
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
    		// otherwise throw this packet payload into the message
    		b.BuildMessageFromPackets(packetCounter, packetPayload);	
    	}
    	
    	// if this particular message is done; ie, is it still pending packets?
    	if (b.PendingPacketStatus() == false) {
    		bleStatusCallback.headsUp("pending packet status now false");
    		
    		// if there's a fingerprint for the sender, handle this message (you should always have a sender fingerprint, else the message is malformed)
    		
    		// add the sender's fingerprint to our map of senderfingerprint and remoteAddress - should this be elsewhere?
    		if (b.SenderFingerprint != null) {
	    		if (b.SenderFingerprint.length > 0) {
	    			bleStatusCallback.headsUp("sender fp, handling msg");
	    			
	    			if (b.checkHash()) {
	    				bleStatusCallback.headsUp("hash good");
	    			} else {
	    				bleStatusCallback.headsUp("hash bad: " + b.GetCalcHash());
	    				bleStatusCallback.headsUp("shouldbe: " + ByteUtilities.bytesToHexShort(b.MessageHash));
	    			}
	    			
	    			fpNetMap.put(ByteUtilities.bytesToHex(b.SenderFingerprint), remoteAddress);
	    			bleStatusCallback.handleReceivedMessage(ByteUtilities.bytesToHex(b.RecipientFingerprint), ByteUtilities.bytesToHex(b.SenderFingerprint), b.MessagePayload, b.MessageType);
	    			
	    		} else {
	    			bleStatusCallback.headsUp("msg error: SenderFingerprint.length=0");	
	    		}
    		} else {
    			bleStatusCallback.headsUp("msg error: SenderFingerprint NULL");
    		}
    		
    		// check message integrity here?
    		// what about encryption?
    		
    		// how do i parse the payload if the message contains handshake/identity?
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
	    		
	    		bleStatusCallback.headsUp("accepted connection from a central");
	    		
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
    		bleStatusCallback.headsUp("notify request from " + device + "; reset timeout");
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
    	
    };


    BleCentralHandler centralHandler = new BleCentralHandler() {
    	
    	@Override
    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);
    		
    	}
    	
		@Override
		public void intakeFoundDevices(ArrayList<BluetoothDevice> devices) {
			bleStatusCallback.headsUp("stopped scanning");
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
				bleStatusCallback.headsUp("connecting to " + peerAddress);
				
				// initiate a connection to the remote address, which checks to see if it's a valid peer
				// if it's not, then you won't be connected
				bleCentral.connectAddress(peerAddress);
				
			}
			
		}
		
		@Override
		public void parlayWithRemote(String remoteAddress) {
			// so now we're connected  			// you don't want to subscribe YET
			bleStatusCallback.headsUp("connected and ready to exchange w/ " + remoteAddress, "subscribe=" + remoteAddress);
			BlePeer p = peerMap.get(remoteAddress);
			
			p.MarkActive();
			
			// get the PuF?
			// You can't do that unless you've already done the ID dance
			
		    // look up peer by connected address
			//BlePeer remoteGuy = peerMap.get(remoteAddress);
		    

			
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

		// so now let's ask for identification information - SUBSEQUENTLY we may transfer other data
		BleMessage b = new BleMessage();
		
		// add this new message to our message map
		// ONLY WORKS FOR ONE CONNECTED PERIPHERAL; need to make per remoteAddress
		bleMessageMap.put(0, b);

		// pass our remote address and desired uuid to our gattclient
		// who will look up the gatt object and uuid and issue the read request
		bleStatusCallback.headsUp("subscribing to 102 on " + remoteAddress);
		bleCentral.submitSubscription(remoteAddress, uuidFromBase("102"));

		// we should be expecting data on 102 now
		
	}
    
}
