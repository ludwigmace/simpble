package example.handshake;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import simpble.BleApplicationPeer;
import simpble.BleMessenger;
import simpble.BlePeer;
import simpble.BleStatusCallback;
import simpble.ByteUtilities;

import com.google.common.primitives.Bytes;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "MAIN";
	private static final int DEBUGLEVEL = 1;

    private static final int ACTIVITY_CREATE=0;

	
	public static final int MSGTYPE_ID = 1;
	public static final int MSGTYPE_PLAIN = 2;
	public static final int MSGTYPE_ENCRYPTED_PAYLOAD = 20;
	public static final int MSGTYPE_ENCRYPTED_KEY = 21;
	public static final int MSGTYPE_DROP = 90;
    
	
	BleMessenger bleMessenger;
 
	
	Map <String, BleApplicationPeer> bleFriends;  // folks whom i have previously connected to, or i have their id info	
	
	String myFingerprint;
	String myIdentifier;
	
	KeyStuff rsaKey;
	TextView statusText;
	
	private Button btnAdvertise;
	
	private boolean visible;
	
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	
	String statusLogText;
	
    private FriendsDb mDbHelper;
    
    private Context ctx;
    
    private boolean sendToAnybody = true;
    
    private boolean EnableSendID;
    private boolean EnableReceiveID;
    
    private Map<String, byte[]> hashToKey;
    private Map<String, byte[]> hashToPayload;
    
    private Map<String, String> addressesToFriends;
    
    private byte[] anonFP;
    
    private ArrayList<String> connectedAddresses;
    
    private Map<String, String> queuedMessageMap;
    
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		statusLogText = "";
		ctx = this;
		
		mDbHelper = new FriendsDb(this);
		
		if (mDbHelper.getConstant(FriendsDb.CONSTANT_SEND_ID).equalsIgnoreCase("true")) {
			EnableSendID = true;
		} else {
			EnableSendID = false;
		}
		
		if (mDbHelper.getConstant(FriendsDb.CONSTANT_RECV_ID).equalsIgnoreCase("true")) {
			EnableReceiveID = true;
		} else {
			EnableReceiveID = false;
		}
		
		
		// initialize an anonymous fingerprint of 20 bytes of zeroes!
		// use this when you don't want to use your fingerprint
		anonFP = new byte[20];
		Arrays.fill(anonFP, (byte) 0);
		
		queuedMessageMap = new HashMap<String, String>();
		
        // get a pointer to the status text
        statusText = (TextView) findViewById(R.id.status_log);
        
		if (hashToKey == null) {
			hashToKey = new HashMap<String,byte[]>();
		}

		if (hashToPayload == null) {
			hashToPayload = new HashMap<String, byte[]>();
		}
		
		addressesToFriends = new HashMap<String, String>();
		
		connectedAddresses = new ArrayList<String>();
		
		// we're not showing ourselves when the program starts
		visible = false;
		
		// get a pointer to our Be A Friend button, and our transfer packet button
		btnAdvertise = (Button)findViewById(R.id.be_a_friend);
		
		// because this is using BLE, we'll need to get the adapter and manager from the main context and thread 
		btMgr = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
		btAdptr = btMgr.getAdapter();
        
        // check to see if the bluetooth adapter is enabled
        if (!btAdptr.isEnabled()) {
        	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        	startActivityForResult(enableBtIntent, RESULT_OK);
        }

        // get an identifier for this installation
        myIdentifier = Installation.id(this, false);
        //myIdentifier = Installation.id(this);
        
        // get your name (that name part isn't working on Android 5)
        //String userName = getUserName(this.getContentResolver());
        String userName = btAdptr.getName();
        
        EditText yourNameControl = (EditText) findViewById(R.id.your_name);
        yourNameControl.setText(userName);
        
        
        // init the rsaKey object
        rsaKey = null;
        
		try {
			rsaKey = new KeyStuff(this, myIdentifier);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//logMessage(ByteUtilities.bytesToHex(rsaKey.PublicKey()));
		
		myFingerprint = ByteUtilities.bytesToHex(rsaKey.PuFingerprint());
		
		btnAdvertise.setEnabled(false);
		
		// get our BLE operations going
		if (btAdptr.isEnabled()) {
			bleMessenger = new BleMessenger(btMgr, btAdptr, this, bleMessageStatus);
			
			if (bleMessenger.SupportsAdvertising) {
				btnAdvertise.setEnabled(true);
			} else {
				btnAdvertise.setEnabled(false);
			}
			
		} // if not enabled, the onResume will catch this
		
		
		if (myFingerprint != null) {
			logMessage("a: our fp is:" + myFingerprint.substring(0, 20) + " . . .");
			Log.v(TAG, "our fp:" + myFingerprint);
		} else {
			logMessage("a: global myFingerprint is null");
		}
		
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		// Rebuild our bleFriends map, as we may have added or removed some
		// Consider not pre-populating a map and do a database lookup as we connect to each peer
		PopulateFriends();
	}
	
	/**
	 * Update the messages table with the fingerprint of the person to whom you sent this message
	 * 
	 * @param msg_content Content of the message
	 * @param msgtype Type of message, as a string
	 * @param peerIndex remote address for this peer
	 */
	public void MarkMsgSent(String messageSignature, String peerIndex) {
		
		// look up your friend's fingerprint based on the index provided earlier by BleMessenger
		String fp = addressesToFriends.get(peerIndex);
		
		// update as sent using the messageSignature to identify
		boolean updated = mDbHelper.updateMsgSent(messageSignature, fp);
		
		if (updated) {
			if (messageSignature.length() > 8) {
				logMessage("dbupdate as sent: " + messageSignature.substring(0,8));
			} else {
				logMessage("dbupdate as sent: " + messageSignature);
			}
		}
		
	}
	
	/**
	 * Loop over all the friends we have in our database and populate our global bleFriends map with
	 * BleApplication peer objects for each of them
	 */
	private void PopulateFriends() {
		
		// let's build our friends that we've got stored up in the database
		bleFriends = new HashMap<String, BleApplicationPeer>();
		
		Cursor c = mDbHelper.fetchAllFriends();
		
		while (c.moveToNext()) {
			
			BleApplicationPeer new_peer = new BleApplicationPeer("");
			
			String peer_name = c.getString(c.getColumnIndex(FriendsDb.KEY_F_NAME));
			String peer_fp = c.getString(c.getColumnIndex(FriendsDb.KEY_F_FP));
			byte[] peer_puk = c.getBlob(c.getColumnIndex(FriendsDb.KEY_F_PUK));
			
			new_peer.SetFingerprint(peer_fp);
			new_peer.SetName(peer_name);
			new_peer.SetPublicKey(peer_puk);

			bleFriends.put(peer_fp, new_peer);
		}
	}

	/**
	 * Makes sure that your Bluetooth adapter is up and going
	 */
	private void SetUpBle() {
		if (btAdptr != null) {
			if (btAdptr.isEnabled()) {
				if (bleMessenger == null) {
					bleMessenger = new BleMessenger(btMgr, btAdptr, this, bleMessageStatus);
				} else {
					Log.v(TAG, "bleMessenger is already instantiated; we're good to go.");
				}
			} else {
				popUpMessage("Your Bluetooth Adapter isn't enabled; please close the app and enable it.");
			}
		} else {
			popUpMessage("Your Bluetooth Adapter isn't instantiated; something is wrong.");
		}
	}
	
    @Override
    protected void onResume() {
    	// in case the program was run without the adapter on
    	super.onResume();
    	SetUpBle();

    }
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_friends) {
	        Intent i = new Intent(this, FriendsActivity.class);
	        startActivityForResult(i, ACTIVITY_CREATE);
		}
		
		if (id == R.id.action_add_friends) {
	        Intent i = new Intent(this, AddFriendsActivity.class);
	        startActivityForResult(i, ACTIVITY_CREATE);
		}		
		
		if (id == R.id.action_add_message) {
	        Intent i = new Intent(this, AddMessageActivity.class);
	        startActivityForResult(i, ACTIVITY_CREATE);
		}

		if (id == R.id.action_add_deaddrops) {
	        Intent i = new Intent(this, AddShamirActivity.class);
	        startActivityForResult(i, ACTIVITY_CREATE);
		}

		if (id == R.id.action_show_msgs) {
	        Intent i = new Intent(this, ShowMessagesActivity.class);
	        startActivityForResult(i, ACTIVITY_CREATE);
		}
		
		if (id == R.id.action_constants) {
	        Intent i = new Intent(this, ConstantsActivity.class);
	        startActivityForResult(i, ACTIVITY_CREATE);
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	// if i want to be able to receive a message meant for me, then i obviously must volunteer my identifying info
	// however if i just want to ferry messages or don't care to receive one (maybe i just want to send)
	// then i don't need to volunteer my info
	BleStatusCallback bleMessageStatus = new BleStatusCallback() {
		
		public void peerConnectionStatus(String remoteAddress, int ConnectionStatus) {
			
			if (ConnectionStatus == BleMessenger.CONNECTION_NEGOTIATING) {
				logMessage("negotiating connection with " + remoteAddress);	
			} else if (ConnectionStatus == BleMessenger.CONNECTION_CONNECTED) {
				logMessage("connected to " + remoteAddress);
			} else if (ConnectionStatus == BleMessenger.CONNECTION_DISCONNECTED) {
				logMessage("disconnected from " + remoteAddress);
			}
			
			
		}
		
		public void messageDelivered(String remoteAddress, String payloadDigest) {

			// pull the message's signature to mark for closing
			final String peerSentTo = remoteAddress;
			
			final String msgSignature = queuedMessageMap.get(payloadDigest);
			
			runOnUiThread(new Runnable() { public void run() { MarkMsgSent(msgSignature, peerSentTo); } });			
			
		}
				
		public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes) {

			ApplicationMessage incomingMsg = new ApplicationMessage();
			
			String incomingDigest = incomingMsg.SetRawBytes(MessageBytes);
			// we added this 
			incomingMsg.BuildMessageDetails();
			
			Log.v(TAG, "incoming msg digest: " + incomingDigest);
			
			
			// get the message type in integer form; should be between 0 and 255 so &0xFF should work
			int mt = incomingMsg.MessageType & 0xFF;
			String recipientFingerprint = ByteUtilities.bytesToHex(incomingMsg.RecipientFingerprint);
			String senderFingerprint = ByteUtilities.bytesToHex(incomingMsg.SenderFingerprint);
			byte[] payload = incomingMsg.MessagePayload;
			byte[] messageHash = incomingMsg.BuildMessageMIC();
						
			switch (mt) {
				case MSGTYPE_ID:
					Log.v(TAG, "received identity msg");
					
	
					// if we'll send to any old person, shouldn't this be on connect?
					// or how do we know when the other person wants messages?
					if (sendToAnybody) {
						
						ApplicationMessage mTopic = null;
						
						try {
						// TODO: consider location as well
							mTopic = GetUnsentEligibleTopicMessage(senderFingerprint);
						} catch (Exception x) {
							Log.v(TAG, "error trying to pull an eligible topic message");
						}
	
						if (mTopic != null) {
							
							String queuedMsg = "";
							queuedMsg = bleMessenger.AddMessage(remoteAddress, mTopic);
							
							logMessage("a5: queued " + queuedMsg + " for " + remoteAddress);

							
						} else {
							logMessage("no topic messages to send");
						}
					}
				
					// if the sender is in our friends list
					if (bleFriends.containsKey(senderFingerprint)) {
						
						// we need to be able to look this person up by incoming address
						// TODO: remove this association upon disconnect
						addressesToFriends.put(remoteAddress, senderFingerprint);
	
					} else if (EnableReceiveID) {  // if we actually care who this person is, then store their FP
						logMessage("a: this guy's FP isn't known to me: " + senderFingerprint.substring(0,20));
						
				        Intent i = new Intent(ctx, AddFriendsActivity.class);
				        i.putExtra("fp", senderFingerprint);
				        i.putExtra("puk", payload);
				        startActivityForResult(i, ACTIVITY_CREATE);
											
				        // TODO: add back to addressesToFriends
					}
					
					break;
					
				case MSGTYPE_PLAIN:
				
					// TODO: store in database
					logMessage("message recvd of size " + String.valueOf(payload.length));
					logMessage("message: " + new String(payload));
					
					// also, maybe, store the message?!?
					break;
					
				case MSGTYPE_ENCRYPTED_PAYLOAD:
					logMessage("received encrypted msg of size:" + String.valueOf(payload.length));
					
					// payload might be padded with zeroes, strip out trailing null bytes
					payload = ByteUtilities.trimmedBytes(payload);
					
					// load this payload into our hash to payload lookup
					hashToPayload.put(ByteUtilities.bytesToHex(messageHash), payload);
	
					Log.v("DOIT", "encrypted payload: " + ByteUtilities.bytesToHex(payload));
					
					byte[] aesKeyBytes = hashToKey.get(ByteUtilities.bytesToHex(messageHash));
					
					if (aesKeyBytes != null) {
					
						// if we have a key for this thing already, decrypt and display messages
						byte[] decryptedPayload = null;
					
						AESCrypt aes = null;
						try {
							byte[] iv = Arrays.copyOf(payload, 16);
							aes = new AESCrypt(aesKeyBytes, iv);
							
						} catch (Exception e) {
							Log.v(TAG, "couldn't create AESCrypt class with String(aesKeyBytes):" + e.getMessage());
						}
					
						try {
							payload = Arrays.copyOfRange(payload, 16, payload.length);
							decryptedPayload = aes.decrypt(payload);
						} catch (Exception e) {
							Log.v(TAG, "couldn't decrypt payload:" + e.getMessage());
						}
						
						if (decryptedPayload != null) {
							String msgtext = new String(decryptedPayload);
							logMessage("decrypted: " + msgtext);
						} else {
							logMessage("empty decrypted payload!");
						}
						
						
					} else {
						logMessage("a: no key for this message!");
						Log.v(TAG, "no key for this message!");
					}
					
					break;
					
				case MSGTYPE_ENCRYPTED_KEY:
					logMessage("received encrypted key of size:" + String.valueOf(payload.length));
					
					Log.v(TAG, "aes key payload in: " + ByteUtilities.bytesToHex(payload));
					
					byte[] incomingMessageHash = processIncomingKeyMsg(payload);
	
					byte[] encryptedPayload = hashToPayload.get(ByteUtilities.bytesToHex(incomingMessageHash));
					
					if (encryptedPayload != null ) {
						logMessage("found encrypted payload for the key we just got");
						// now decrypt!
					} else {
						logMessage("NO encrypted payload found for this new key");
					}
					
					break;
				
				case MSGTYPE_DROP:
					
					// this can be for topics; but need to differentiate if bytes or not
					String topic_name = "";
					
					try {
						// since the fingerprint was passed in as a hex string, convert it to bytes, and then build a string
						topic_name = new String(ByteUtilities.trimmedBytes(ByteUtilities.hexToBytes(recipientFingerprint)));
						logMessage("a: received topic msg " + topic_name + " of size:" + String.valueOf(payload.length));
					} catch (Exception x) {
						logMessage("a: couldn't parse topic msg bytes into string");
					}
					// payload is missing the first 6 bytes
					String msgSignature = ByteUtilities.digestAsHex(new String (payload) + "topic" + topic_name);
					
					long storedMsgId = -1;
					
					storedMsgId = mDbHelper.queueMsg(topic_name, new String (payload), "topic", msgSignature);
					
					if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
						logMessage("a: message is for us (as follows, next line):");
						logMessage(new String(payload));
					} else {
						logMessage("a: message isn't for us");
					}
					
					logMessage("stored msg: " + String.valueOf(storedMsgId));
					
					break;
					
				default:
					logMessage("received msg of unknown type " + mt);
					
			}
			
		}

		public void advertisingStatusUpdate(boolean isAdvertising) {
			if (isAdvertising) {
				logMessage("a: advertising started");
				
				runOnUiThread(new Runnable() {
					  public void run() {
							visible = true;
							btnAdvertise.setText("!Adv");
					  }
					});
			} else {
				logMessage("a: advertising stopped");
				
				runOnUiThread(new Runnable() {
					  public void run() {
							visible = false;
							btnAdvertise.setText("Advt");
					  }
					});
			}
			
		}
		
		public void headsUp(String msg) {
			logMessage(msg, 1);
		}

		
	};

	/**
	 * Given a digest of an encrypted message, return the corresponding AES key
	 * @param incomingHash Byte array of message digest, 16 bytes 
	 * @return
	 */
	public SecretKey getKeyForMessageHash(byte[] incomingHash) {
				
		byte[] aesKey = hashToKey.get(ByteUtilities.bytesToHex(incomingHash));
		
		SecretKey key = null;
		
		if (aesKey != null) {
			key = new SecretKeySpec(aesKey, 0, aesKey.length, "AES");
		}
		
		return key;
				
	}
	
	/** Takes an incoming byte array of an RSA wrapped AES key and unwraps it, storing the AES key 
	 * and hash of the corresponding encrypted message in the hashToKey Map and returning
	 * the aforementioned hash.
	 * 
	 * @param keyPayload Byte array where the first 16 bytes are a hash of the plaintext of an encrypted message
	 * and the remaining bytes are the wrapped key.
	 * @return
	 */
	public byte[] processIncomingKeyMsg(byte[] keyPayload) {
		
		//keyPayload = Bytes.concat(m.MessageHash, aesKeyEncrypted);
		// first 15 bytes are the hash that corresponds to the encrypted msg this key is for
		// aesKey
		
		Log.v(TAG, "incoming key payload bytes:" + ByteUtilities.bytesToHex(keyPayload));
		
		// read in the hash of the originating message
		byte[] hash = Arrays.copyOfRange(keyPayload, 0, 15);
		byte[] encrypted_key = Arrays.copyOfRange(keyPayload, 15, 256+15);
		
		Log.v(TAG, "encrypted_key length is: " + String.valueOf(encrypted_key.length));
		Log.v(TAG, "key bytes are: " + ByteUtilities.bytesToHex(encrypted_key));
		
		// let's decrypt the key so we can unlock the other message
		SecretKey symmetric_key = null;
		
		try {
			symmetric_key = rsaKey.unwrap(encrypted_key);
		} catch (GeneralSecurityException e) {
			Log.v(TAG, e.getMessage());
			logMessage("can't unwrap the key");
		}
		
		if (symmetric_key != null) {
			// map our messages hashes to our encryption keys
			hashToKey.put(ByteUtilities.bytesToHex(hash), symmetric_key.getEncoded());
		
			Log.v("DOIT", "key is: " + ByteUtilities.bytesToHex(symmetric_key.getEncoded()));
		} else {
			Log.v("DOIT", "unable to store key");
		}
		
		return hash;
		
		
	}
	
	/**
	 * Call this method to loop over every currently connected peer and make sure they're in our local list of connected peers.  If we weren't already connected
	 * to this peer, queue up an Identity message to send to that peer.  If we've already been connected, look up this peer to see if we should
	 * send them any messages.
	 * 
	 * @param view
	 */
	public void handleButtonToggleBusy(View view) {
			
		// i really want BlePeer to not be used outside of BleMessenger!
		for (Map.Entry<String, BlePeer> entry : bleMessenger.peerMap.entrySet()) {
		
			BlePeer p  = entry.getValue();
			String address = entry.getKey();
			String queuedMessageDigest = "";

			
			// if we don't have that this person is already connected
			if (!connectedAddresses.contains(address)) {
				logMessage("address not previously connected to; adding to arraylist");
				
				// add them to our list
				connectedAddresses.add(address);
				
				// this should only be run immediately after connecting, and only if you want to send your id
				// if we can send messages to this peer, add our ID message
				if (p.TransportTo) {
					logMessage("transport open, we want to send it, queue it up");					

					queuedMessageDigest = bleMessenger.AddMessage(address, identityMessage());
				}
				
			} else {
				// since we're already connected, now check friend stuff
				logMessage("address previously connected to; meaning this is a session");
				
				if (addressesToFriends.containsKey(address)) {
					logMessage("address maps to friend");	
			
					ArrayList<ApplicationMessage> friendMessages = GetMessagesForFriend(addressesToFriends.get(address));
					
					logMessage("found " + friendMessages.size() + " messages to send for friend");
					// queue up all the messages we've got for this dude
					for (ApplicationMessage m : friendMessages) {
						
						queuedMessageDigest = bleMessenger.AddMessage(address, m);
						queuedMessageMap.put(queuedMessageDigest, m.ApplicationIdentifier);
					}
					
				} else {
					logMessage("address doesn't map to friend, won't queue message");
				}
			}
						
		}
		
		bleMessenger.SendMessagesToConnectedPeers();
		
		
	}
    
	/**
	 * Enter BLE Peripheral mode to be found by a scanning Central
	 * @param view
	 */
	public void handleButtonBeAFriend(View view) {
		// now we need to create the payload with our friendly name and public key
		Log.v(TAG, "Advertising Toggle Pressed");
		
		if (!visible) {
			Log.v(TAG, "Not currently visible, begin stuff");

			if (bleMessenger.StartAdvertising()) {
				Log.v(TAG, "advertising supported");
			}
		} else {
			bleMessenger.StopAdvertising();
		}
		
		
	}
	    
	/**
	 * Scan for peers in BLE peripheral mode
	 * 
	 * @param view
	 */
	public void handleButtonFindAFriend(View view) {
		logMessage("a: look around");
		
		// calls back bleMessageStatus when peers are found
		bleMessenger.ScanForPeers(2500);
	}
		
	/**
	 * Shortcut to show a Toast
	 * @param msg Message to pop-up
	 */
	private void popUpMessage(String msg) {

		final String message = msg;
		final Context fctx = this;
		
		runOnUiThread(new Runnable() {
			  public void run() {
				  Toast.makeText(fctx, message, Toast.LENGTH_LONG).show();
			  }
			});
		
	}
	
	/**
	 * Shortcut function to logMessage with a debug level of 0.
	 * @param msg Message to show
	 */
	private void logMessage(String msg) {
		logMessage(msg, 0);
	}
	
	/**
	 * A shortcut function to display messages in the main output feed
	 * @param msg Message to show
	 * @param level Debug level, where 0 only shows error messages and 1 includes informational messages
	 */
	private void logMessage(String msg, int level) {
		
		if (level <= DEBUGLEVEL) {
			statusLogText = "- " + msg + "\n" + statusLogText;
			runOnUiThread(new Runnable() {
				  public void run() {
					  statusText.setText(statusLogText);
				  }
			});
		}
	}
	
	/** 
	 * Given a hex representation of the public key fingerprint for a peer, search the database for any messages to be sent to this peer
	 * and return them in an ArrayList of type BleApplicationMessage.
	 * @param candidateFingerprint Public key fingerprint for peer, hexadecimal
	 * @return
	 */
	private ArrayList<ApplicationMessage> GetMessagesForFriend(String candidateFingerprint) {
		Cursor c = mDbHelper.fetchMsgsForFriend(candidateFingerprint);
		
		ArrayList<ApplicationMessage> results = new ArrayList<ApplicationMessage>();
		
		ApplicationMessage m = null; 

		// if we have any messages
		if (c.getCount() > 0) {
				//loop over these messages
				while (c.moveToNext()) {
				
				m = new ApplicationMessage();
				
				String msg_content = c.getString(c.getColumnIndex(FriendsDb.KEY_M_CONTENT));
				String msg_type = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGTYPE));
				String msg_signature = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGID));
				byte[] puk = c.getBlob(c.getColumnIndex(FriendsDb.KEY_F_PUK));
				
				if (msg_signature == null) {
					msg_signature = "";
				}
				
				m.RecipientFingerprint = ByteUtilities.hexToBytes(candidateFingerprint);
				m.SenderFingerprint = ByteUtilities.hexToBytes(myFingerprint);  // should probably pull from database instead; for relaying of messages
				m.ApplicationIdentifier = msg_signature;
				
				// in case we need to encrypt this message
				byte[] msgbytes = null;
				byte[] aesKeyEncrypted = null;

				// if our message is meant to be encrypted, do that first
				if (msg_type.equalsIgnoreCase("encrypted")) {
					
					SecureRandom sr = new SecureRandom();
					byte[] aeskey = new byte[32]; // 512 bit key
					sr.nextBytes(aeskey);

					byte[] iv = new byte[16];
					sr.nextBytes(iv);
					
					AESCrypt aes = null;
					
					try {
						aes = new AESCrypt(aeskey, iv);

					} catch (Exception e) {
						Log.v(TAG, "can't instantiate AESCrypt");
					}
					
					if (aes != null) {
						
						try {
							// prepend the initialization vector to the encrypted payload
							msgbytes = Bytes.concat(iv, aes.encrypt(msg_content.getBytes()));
						} catch (Exception x) {
							Log.v(TAG, "encrypt error: " + x.getMessage());
						}
						
						if (msgbytes != null) {
							// encrypt our encryption key using our recipient's public key 							
							try {
								aesKeyEncrypted = aes.encryptedSymmetricKey(puk);
								Log.v(TAG, "encrypted aes key: " + ByteUtilities.bytesToHex(aesKeyEncrypted));
							} catch (Exception e) {
								Log.v(TAG, "couldn't encrypt aes key");	
							}
						} else {
							logMessage("couldnt encrypt message");
							break;
						}
					}
					
				} else {
					msgbytes = msg_content.getBytes();
				}
				
				if (msg_type.equalsIgnoreCase("encrypted")) {
					
					
					m.MessageType = (byte)MSGTYPE_ENCRYPTED_PAYLOAD & 0xFF;
					m.setPayload(msgbytes);
					
					ApplicationMessage m_key = new ApplicationMessage();
					
					// get the fingerprint from the Friend object
					m_key.RecipientFingerprint = m.RecipientFingerprint;
					
					// gotta give it a pre-determined messagetype to know this is an encryption key
					m_key.MessageType = (byte)MSGTYPE_ENCRYPTED_KEY & 0xFF;
					
					// get the sending fingerprint from the main message
					m_key.SenderFingerprint = m.SenderFingerprint;
					
					m_key.ApplicationIdentifier = "key_" + msg_signature;
					
					// the payload needs to include the encrypted key, and the orig msg's fingerprint
					// if the hash is a certain size, then we can assume the rest of the message is the
					// encrypted portion of the aes key
					byte[] aes_payload = Bytes.concat(m.MessageHash, aesKeyEncrypted);
					m_key.setPayload(aes_payload);
					
					Log.v(TAG, "aes key payload out: " + ByteUtilities.bytesToHex(aes_payload));
					
					results.add(m_key);
				
				} else {
					m.MessageType = (byte) MSGTYPE_PLAIN & 0xFF;
					m.setPayload(msgbytes);
				}
				
				
				
				results.add(m);
			}
			
		}
		
		return results;
		
	}
	
	/**
	 * Returns a secret message share for any topic that this person hasn't received a share for yet
	 * 
	 * @param candidateFingerprint The PukFP of the person we're ensure only gets a single share of a particular topic
	 * @return A message in a BleMessage object 
	 */
	private ApplicationMessage GetUnsentEligibleTopicMessage(String candidateFingerprint) {
		
		// topic messages eligible to go to this recipient
		Cursor c = mDbHelper.topicsNotSentToRecipient(candidateFingerprint);
		
		ApplicationMessage m = null; 
		
		if (c.getCount() > 0) {
			c.moveToFirst();
			
			// found an eligible message in the database, so build it for the application
			m = new ApplicationMessage();
			
			String recipient_name = c.getString(c.getColumnIndex(FriendsDb.KEY_M_FNAME));
			String msg_content = c.getString(c.getColumnIndex(FriendsDb.KEY_M_CONTENT));
			//String msg_type = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGTYPE));
			String msg_signature = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGID));
			
			if (msg_signature == null) {
				msg_signature = "";
			}
	
			byte[] rfp = new byte[20];
			rfp = Arrays.copyOf(recipient_name.getBytes(), 20);
			m.RecipientFingerprint = rfp;
			m.SenderFingerprint = anonFP;
			m.MessageType = (byte)(MSGTYPE_DROP & 0xFF);
			m.setPayload(msg_content.getBytes());
			
			m.SetSignature(msg_signature);

			
		}
		
		return m;

	}
	
	
	/**
	 * Creates and returns a BleApplication message object with this peer's identifying details 
	 * @return
	 */
	private ApplicationMessage identityMessage() {
		ApplicationMessage m = new ApplicationMessage();
		m.MessageType = (byte)1 & 0xFF;
		m.SenderFingerprint = rsaKey.PuFingerprint();
		m.RecipientFingerprint = new byte[20];
		
		m.setPayload(rsaKey.PublicKey());
		
		return m;
	}

	


    
}
