package example.handshake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import secretshare.ShamirCombiner;
import secretshare.ShamirSplitter;
import simpble.BleMessage;
import simpble.BleMessenger;
import simpble.BlePeer;
import simpble.BleStatusCallback;
import simpble.ByteUtilities;

import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "MAIN";
	private static final int DEBUGLEVEL = 0;

    private static final int ACTIVITY_CREATE=0;
    private static final int ACTIVITY_EDIT=1;

    private static final int INSERT_ID = Menu.FIRST;
    private static final int DELETE_ID = Menu.FIRST + 1;

	
	BleMessenger bleMessenger;
	
	// maybe for these guys I should leave these inside of the BleMessenger class?
	// because if I reference a BlePeer from here, I'm not hitting up the same memory address in BleMessenger 
	
	Map <String, BlePeer> bleFriends;  // folks whom i have previously connected to, or i have their id info	
	
	String myFingerprint;
	String myIdentifier;
	
	KeyStuff rsaKey;
	TextView statusText;
	
	private Button btnAdvertise;
	private Button btnPull;
	
	private boolean visible;
	
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	
	// this is just temporary to allow setting an address for subscription that we can call manually later
	String ourMostRecentFriendsAddress;
	String statusLogText;
	
    private FriendsDb mDbHelper;
    
    private Context ctx;
    
    String currentTask;
	
    private boolean messageReceiving = false;
    private boolean messageSending = false;
    private boolean sendToAnybody = true;
    
    private Map<String, byte[]> hashToKey;
    private Map<String, byte[]> hashToPayload;
    
    private Map<String, String> addressesToFriends;
    
    // holds messages that you may want to mule out
    SparseArray<BleMessage> topicMessages;
    
    private byte[] anonFP;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		currentTask = "";
		statusLogText = "";
		ctx = this;
		
		mDbHelper = new FriendsDb(this);
		
		topicMessages = new SparseArray<BleMessage>();
		
		// initialize an anonymous fingerprint of 20 bytes of zeroes!
		// use this when you don't want to use your fingerprint
		anonFP = new byte[20];
		Arrays.fill(anonFP, (byte) 0);
		
        // get a pointer to the status text
        statusText = (TextView) findViewById(R.id.status_log);
        
		if (hashToKey == null) {
			hashToKey = new HashMap<String,byte[]>();
		}

		if (hashToPayload == null) {
			hashToPayload = new HashMap<String, byte[]>();
		}
		
		addressesToFriends = new HashMap<String, String>();
		
		// do you want to be able to receive messages?
		// TODO: have the UI capture this
		messageReceiving = true;
		
		// we're not showing ourselves when the program starts
		visible = false;
		
		// get a pointer to our Be A Friend button, and our transfer packet button
		btnAdvertise = (Button)findViewById(R.id.be_a_friend);
		
		btnPull = (Button)findViewById(R.id.pullmsgs);
		
		// disable Id button, because you're not even connected yet, and thus not ready to identify
		btnPull.setEnabled(false);
		
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
		
		PopulateFriendsAndMessages();
	}
	
	/**
	 * Update the messages table with the fingerprint of the person to whom you sent this msg
	 * 
	 * @param msg_content Content of the message
	 * @param msgtype Type of message, as a string
	 * @param peerIndex remote address for this peer
	 */
	public void MarkMsgSent(String messageSignature, String peerIndex) {
		
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
	
	private void PopulateFriendsAndMessages() {
		
		// let's build our friends that we've got stored up in the database
		bleFriends = new HashMap<String, BlePeer>();
		
		Cursor c = mDbHelper.fetchAllFriends();
		
		while (c.moveToNext()) {
			
			BlePeer new_peer = new BlePeer("");
			
			String peer_name = c.getString(c.getColumnIndex(FriendsDb.KEY_F_NAME));
			String peer_fp = c.getString(c.getColumnIndex(FriendsDb.KEY_F_FP));
			byte[] peer_puk = c.getBlob(c.getColumnIndex(FriendsDb.KEY_F_PUK));
			
			new_peer.SetFingerprint(peer_fp);
			new_peer.SetName(peer_name);
			new_peer.SetPublicKey(peer_puk);
			
			// for testing, don't add your peer
			bleFriends.put(peer_fp, new_peer);
			logMessage("adding peer " + peer_fp.substring(0,8));
		}

		c = mDbHelper.fetchUnsentMsgs();
		
		// we want to send some messages
		if (c.getCount() > 0) {
			messageSending = true;
		} else {
			logMessage("no messages to send");
		}
		
		// loop over all our messages
		while (c.moveToNext()) {

			BleMessage m = new BleMessage();
			
			String recipient_name = c.getString(c.getColumnIndex(FriendsDb.KEY_M_FNAME));
			String msg_content = c.getString(c.getColumnIndex(FriendsDb.KEY_M_CONTENT));
			String msg_type = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGTYPE));
			String msg_signature = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGID));
			
			if (msg_signature == null) {
				msg_signature = "";
			}
			
			logMessage("(" + recipient_name + ") " + msg_content);
			
			// if this is a drop (topic) message, the recipient shouldn't be in our friends list
			// throw all our muleMessages into the SparseArray muleMessages 
			if (msg_type.equalsIgnoreCase("topic") && sendToAnybody) {
				// need to make sure recipient fingerprint is 20 bytes
				byte[] rfp = new byte[20];
				rfp = Arrays.copyOf(recipient_name.getBytes(), 20);
				m.RecipientFingerprint = rfp;
				m.SenderFingerprint = anonFP;
				m.MessageType = (byte)(90 & 0xFF); // just throwing out 90 as indicating a secret share
				// TODO: for the above, have these all be constants and use msg_type (that'll be a constant too)
				m.setPayload(msg_content.getBytes());
				
				m.SetSignature(msg_signature);
				
				topicMessages.put(topicMessages.size(), m);
				
			// if it's not a drop message, loop over all our friends to see if anything is headed their way!
			// TODO: just fucking use a query; this is ridiculous
			} else {
				
				// inefficient way to get peer stuff
				for (BlePeer p: bleFriends.values()) {
	
					// if the peer in our friends list equals the name we've pulled out of the database for this message
					if (p.GetName().equalsIgnoreCase(recipient_name)) {
						String msgHash = "";
						
						//try {					
							// get the fingerprint from the Friend object
							m.RecipientFingerprint = p.GetFingerprintBytes();
							
							// get the sending fingerprint from our global variable
							// TODO: this won't work if the original sender is different
							m.SenderFingerprint = ByteUtilities.hexToBytes(myFingerprint);
							
							m.SetSignature(msg_signature);
							
							// in case we need to encrypt this message
							byte[] msgbytes = null;
							byte[] aesKeyEncrypted = null;
	
							// if our message is meant to be encrypted, do that first
							if (msg_type.equalsIgnoreCase("encrypted")) {
								
								// get our friend's public key from the friend's object
								byte[] friendPuk = p.GetPublicKey();
								
								//TODO: make this a random encryption key
								String encryption_key = "thisismydamnpassphrasepleaseacceptthisasthegodshonestthruthofmine!";
								
								AESCrypt aes = null;
								
								try {
									aes = new AESCrypt(encryption_key.getBytes());
								} catch (Exception e) {
									Log.v(TAG, "can't instantiate AESCrypt");
								}
								
								if (aes != null) {
								
									Log.v("DOIT", "encryption key raw: " + ByteUtilities.bytesToHex(encryption_key.getBytes()));
									Log.v("DOIT", "encrypting bytes: " + ByteUtilities.bytesToHex(msg_content.getBytes()));
									
									try {
										msgbytes = aes.encrypt(msg_content.getBytes());
									} catch (Exception x) {
										Log.v(TAG, "encrypt error: " + x.getMessage());
									}
									
									if (msgbytes != null) {
										Log.v("DOIT", "encrypted bytes: " + ByteUtilities.bytesToHex(msgbytes));
										//Log.v("DOIT", "test decrypt bytes: " + ByteUtilities.bytesToHex(aes.decrypt(msgbytes)));
										
										// encrypt our encryption key using our recipient's public key 							
										try {
											aesKeyEncrypted = encryptedSymmetricKey(friendPuk, encryption_key);
											Log.v(TAG, "encrypted key bytes: " + ByteUtilities.bytesToHex(aesKeyEncrypted));
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
								
								
								m.MessageType = (byte)(20 & 0xFF);  // just throwing out 20 as indicating an encrypted msg
								m.setPayload(msgbytes);
								
								BleMessage m_key = new BleMessage();
								
								// get the fingerprint from the Friend object
								m_key.RecipientFingerprint = p.GetFingerprintBytes();
								
								// gotta give it a pre-determined messagetype to know this is an encryption key
								m_key.MessageType = (byte)(21 & 0xFF);
								
								// get the sending fingerprint from the main message
								m_key.SenderFingerprint = m.SenderFingerprint;
								
								// the payload needs to include the encrypted key, and the orig msg's fingerprint
								// if the hash is a certain size, then we can assume the rest of the message is the
								// encrypted portion of the aes key
								logMessage("symmetric key " + ByteUtilities.bytesToHex(aesKeyEncrypted).substring(0,8));
								byte[] aes_payload = Bytes.concat(m.MessageHash, aesKeyEncrypted);
								m_key.setPayload(aes_payload);
								
								p.addBleMessageOut(m_key);
							
							} else {
								m.MessageType = (byte)(2 & 0xFF);  // raw data is 2
								m.setPayload(msgbytes);
								
							}
							
							p.addBleMessageOut(m);
							
							try {
								msgHash = ByteUtilities.bytesToHex(m.MessageHash).substring(0, 8);
							} catch (Exception e) {
								msgHash = "err";
							}
							
							logMessage("queued " + msgHash  + " for " + recipient_name);						
							
							break;
						//} catch (Exception x) {
	//						logMessage("e: " + x.getMessage());
						//}
						
	
					}
				}
			
			}
			
		}
		
	}
		
	private void SetUpBle() {
		if (btAdptr != null) {
			if (btAdptr.isEnabled()) {
				if (bleMessenger == null) {
					bleMessenger = new BleMessenger(btMgr, btAdptr, this, bleMessageStatus);
				} else {
					Log.v(TAG, "bleMessenger is already instantiated; we're good to go.");
				}
			} else {
				showMessage("Your Bluetooth Adapter isn't enabled; please close the app and enable it.");
			}
		} else {
			showMessage("Your Bluetooth Adapter isn't instantiated; something is wrong.");
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
		
		
		return super.onOptionsItemSelected(item);
	}
	
	// if i want to be able to receive a message meant for me, then i obviously must volunteer my identifying info
	// however if i just want to ferry messages or don't care to receive one (maybe i just want to send)
	// then i don't need to volunteer my info
	BleStatusCallback bleMessageStatus = new BleStatusCallback() {

		// we just got a notification
		public void peerNotification(String peerIndex, String notification) {
			
			// you don't know who this person is yet
			if (notification.equalsIgnoreCase("new_contract") && currentTask.equalsIgnoreCase("normal")) {
				logMessage("a: peripheral peer meets contract");
				
				ourMostRecentFriendsAddress = peerIndex;
				
				// if we're willing to receive messages then we need to transmit our info to the other peer
				if (messageReceiving) {
					
					BleMessage idenM = identityMessage();
					String queuedMsg = "";
					
					if (idenM != null) {
						queuedMsg = bleMessenger.peerMap.get(peerIndex).addBleMessageOut(idenM).substring(0,8);
						logMessage("a1: queued " + queuedMsg + " for " + peerIndex);
					}
				}
				
				// subscribe to the peripheral's transport
				bleMessenger.initRequestForData(ourMostRecentFriendsAddress);
			}
			
			// this notification is that BleMessenger just found a peer that met the service contract
			// only central mode gets this
			if (notification.equalsIgnoreCase("new_contract") && currentTask.equalsIgnoreCase("pair")) {

				ourMostRecentFriendsAddress = peerIndex;
				logMessage("a: connected to " + peerIndex);
				BleMessage idenM = identityMessage();
				String queuedMsg = "";
				if (idenM != null) {
					queuedMsg = bleMessenger.peerMap.get(peerIndex).addBleMessageOut(idenM).substring(0,8);
					logMessage("a2: queued " + queuedMsg + " for " + peerIndex);
					
					// now let the other guy know you're ready to receive data
					bleMessenger.initRequestForData(ourMostRecentFriendsAddress);
					
				}
				
			}
			
			// only peripheral mode gets this; we've accepted a connection and we're either wanting to pair or just data stuff
			if (notification.equalsIgnoreCase("accepted_connection") && currentTask.equalsIgnoreCase("pair")) {
				logMessage("a: connected to " + peerIndex);

				// since i've just accepted a connection, queue up an identity message
				BleMessage idenM = identityMessage();
				String queuedMsg = "";
				
				if (idenM != null) {
					queuedMsg = bleMessenger.peerMap.get(peerIndex).addBleMessageOut(idenM).substring(0,8);
					logMessage("a3: queued " + queuedMsg + " for " + peerIndex);
				}
				
				// if you're a peripheral, you can't initiate message send until the peer has subscribed
				
			}
			
			// only peripheral mode gets this; we've accepted a connection and we're either wanting to pair or just data stuff
			if (notification.equalsIgnoreCase("accepted_connection") && currentTask.equalsIgnoreCase("normal")) {
				logMessage("a: connected to " + peerIndex);

				// since i've just accepted a connection, queue up an identity message
				BleMessage idenM = identityMessage();
				String queuedMsg = "";
				
				if (idenM != null) {
					queuedMsg = bleMessenger.peerMap.get(peerIndex).addBleMessageOut(idenM).substring(0,8);
					logMessage("a4: queued " + queuedMsg + " for " + peerIndex);
				}
				
				// if you're a peripheral, you can't initiate message send until the peer has subscribed
				// you don't have the fingerprint yet, you just know that this person meets the contract
			}
			
			if (notification.equalsIgnoreCase("connection_change")) {
				logMessage("a: connection status changed for " + peerIndex);
			}
			
			if (notification.equalsIgnoreCase("server_disconnected")) {
				logMessage("a: disconnected from " + peerIndex);
			}
			
			if (notification.contains("msg_sent")) {
				
				int msg_id = -1;
				String msgid_as_string = "";
				// here's me not being thread safe
				try {
					msgid_as_string = notification.substring(9, notification.length());
					msg_id = Integer.valueOf(msgid_as_string);
				} catch (Exception e) {
					logMessage("!" + notification);
				}

				BleMessage sentMsg = bleMessenger.peerMap.get(peerIndex).getBleMessageOut(msg_id);
				
				final String peerSentTo = peerIndex;
				String sig = "no_signature";
				
				if (sentMsg.GetSignature() != null) {
					sig = sentMsg.GetSignature();
				}
				
				final String msgSignature = sig; 
				
				runOnUiThread(new Runnable() { public void run() { MarkMsgSent(msgSignature, peerSentTo); } });
				
				
			}
			
			
		}
		
		// this is when all the packets have come in, and a message is received in its entirety (hopefully)
		// TODO: too much happens in this callback; need to move things out of here!
		@Override
		//public void handleReceivedMessage(String remoteAddress, String recipientFingerprint, String senderFingerprint, byte[] payload, byte msgType, byte[] messageHash) {
		public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes) {

			BleMessage incomingMsg = new BleMessage();
			
			boolean bMessageBuilt = incomingMsg.SetRawBytes(MessageBytes);
			
			if (!bMessageBuilt) {
				return;
			}
			
			
			// get the message type in integer form; should be between 0 and 255 so &0xFF should work
			int mt = incomingMsg.MessageType & 0xFF;
			String recipientFingerprint = ByteUtilities.bytesToHex(incomingMsg.RecipientFingerprint);
			String senderFingerprint = ByteUtilities.bytesToHex(incomingMsg.SenderFingerprint);
			byte[] payload = incomingMsg.MessagePayload;
			byte[] messageHash = incomingMsg.BuildMessageMIC();
			
			
			// this is an identity message so handle it as such
			if (mt == 1) {
				Log.v(TAG, "received identity msg");
								
				if (recipientFingerprint.length() == 0) {
					// there is no recipient; this is just an identifying message
					logMessage("a: no particular recipient for this msg");
				} else if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					logMessage("a: msg intended for us");
				} else {
					// TODO: what if it's being forwarded?
				}
				
				/* so we just got an id
				 * we need to see if we've already sent this particular peer a message for this topic
				 * the "peer-ready-to-receive" notification means we can now to see if this peer needs a message
				 * 1)for dead-drop purposes; as an original sender, don't send more than one message for a 
				 *   topic to the same person
				 *   BUT, you also don't want to do it for other reasons that the calling app can consider
				 *   the most obvious one i can think of is Location
				 * - 
				 * 
				 * */
				if (sendToAnybody) {
					// well this is certainly indiscriminate, but will get only 1
					BleMessage mTopic = topicMessages.get(0);

					if (mTopic != null) { 
					
						String topic_name = new String(ByteUtilities.trimmedBytes(mTopic.RecipientFingerprint));
						
						// need to check against what we've already sent for this "topic name" so that
						// we don't send another share
						ArrayList<String> avoidSending = mDbHelper.recipientsForTopic(topic_name);
						
						
						// inspect avoidSending - because you're just not sending to 
						
						boolean OkToSend = false;
	
						if (!avoidSending.contains(addressesToFriends.get(remoteAddress))) {
							logMessage("ok to send topic");
							OkToSend = true;
						} else {
							logMessage("already got a share, not ok to send topic!");
						}
						
						String queuedMsg = "";
						
						if (OkToSend) {
							queuedMsg = bleMessenger.peerMap.get(remoteAddress).addBleMessageOut(mTopic).substring(0,8);
							logMessage("a5: queued " + queuedMsg + " for " + remoteAddress);
							ourMostRecentFriendsAddress = remoteAddress;
						}
					} else {
						logMessage("no topic messages to check");
					}
				}
				
				// if the sender is in our friends list
				if (bleFriends.containsKey(senderFingerprint)) {
					
					// we need to be able to look this person up by incoming address
					// TODO: remove this association upon disconnect
					addressesToFriends.put(remoteAddress, senderFingerprint);

					logMessage("a: known peer: " + senderFingerprint.substring(0,20));
					
					// we know that we have this peer as a friend

					// queue up all the messages we've got for this dude
					for (int i = 0; i < bleFriends.get(senderFingerprint).GetMessagesOut().size(); i++) {
						BleMessage m = bleFriends.get(senderFingerprint).GetMessagesOut().get(i);

						String queuedMsg = "";
						
						if (m != null) {
							queuedMsg = bleMessenger.peerMap.get(remoteAddress).addBleMessageOut(m).substring(0,8);
							logMessage("a5: queued " + queuedMsg + " for " + remoteAddress);
							ourMostRecentFriendsAddress = remoteAddress;
							
						} else {
							logMessage("a: no msg found for " + remoteAddress);
						}						
					}

				} else {
					logMessage("a: this guy's FP isn't known to me: " + senderFingerprint.substring(0,20));
					
			        Intent i = new Intent(ctx, AddFriendsActivity.class);
			        i.putExtra("fp", senderFingerprint);
			        i.putExtra("puk", payload);
			        startActivityForResult(i, ACTIVITY_CREATE);
										
					// we don't know the sender and maybe should add them?
					// parse the public key & friendly name out of the payload, and add this as a new person
				}
				
			} else if (mt == 2) {
				logMessage("a: received raw msg of size:" + String.valueOf(payload.length));
				
				if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					logMessage("a: message is for us (as follows, next line):");
					logMessage(new String(payload));
				} else {
					logMessage("a: message isn't for us");
				}
				
				Log.v(TAG, "received data msg, payload size:"+ String.valueOf(payload.length));
			} else if (mt == 20) {
				logMessage("a: received encrypted msg of size:" + String.valueOf(payload.length));
				Log.v(TAG, "received encrypted msg, payload size:"+ String.valueOf(payload.length));
				
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
						aes = new AESCrypt(aesKeyBytes);
					} catch (Exception e) {
						Log.v(TAG, "couldn't create AESCrypt class with String(aesKeyBytes):" + e.getMessage());
					}
				
					try {
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
				
			} else if (mt == 21) {
				logMessage("a: received encrypted key of size:" + String.valueOf(payload.length));
				Log.v(TAG, "received encrypted key, payload size:"+ String.valueOf(payload.length));
				
				byte[] incomingMessageHash = processIncomingKeyMsg(payload);
				
				Log.v(TAG, "added key for incoming msg hash " + ByteUtilities.bytesToHex(incomingMessageHash));

				byte[] encryptedPayload = hashToPayload.get(ByteUtilities.bytesToHex(incomingMessageHash));
				
				if (encryptedPayload != null ) {
					logMessage("a: found encrypted payload for the key we just got");
				} else {
					logMessage("a: NO encrypted payload found for this new key");
				}
				
			} else if (mt == 90) {
				
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
				
			}
			
		}
		
		@Override
		public void messageSent(byte[] MessageHash, BlePeer blePeer) {
			logMessage("a: message sent to " + blePeer.GetFingerprint().substring(0,20));
			
			// maybe add re-check of sending next message on UI thread?
		}

		@Override
		public void remoteServerAdded(String serverName) {
			showMessage(serverName);
		}

		@Override
		public void foundPeer(BlePeer blePeer) {
			final String peerName = blePeer.GetName(); 
			
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                	showMessage("peer found:" + peerName);
                }
            });
			
		}

		@Override
		public void advertisingStarted() {
			logMessage("a: advertising started");
			
			runOnUiThread(new Runnable() {
				  public void run() {
						visible = true;
						btnAdvertise.setText("!Adv");
				  }
				});
			
		}

		@Override
		public void advertisingStopped() {
			logMessage("a: advertising stopped");
			
			runOnUiThread(new Runnable() {
				  public void run() {
						visible = false;
						btnAdvertise.setText("Advt");
				  }
				});

			
		}

		@Override
		public void headsUp(String msg) {
			logMessage(msg, 1);
		}

		@Override
		public void readyToTalk(String remo) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void headsUp(String msg, String action) {
			logMessage(msg);			
		}
		
		
	};
	
	public SecretKey getKeyForMessageHash(byte[] incomingHash) {
				
		byte[] aesKey = hashToKey.get(ByteUtilities.bytesToHex(incomingHash));
		
		SecretKey key = null;
		
		if (aesKey != null) {
			Log.v(TAG, "found key for " + ByteUtilities.bytesToHex(incomingHash).substring(0,8));
			key = new SecretKeySpec(aesKey, 0, aesKey.length, "AES");
		} else {
			Log.v(TAG, "NO key found for " + ByteUtilities.bytesToHex(incomingHash).substring(0,8));
		}
		
		return key;
				
	}
	
	public byte[] processIncomingKeyMsg(byte[] keyPayload) {
		
		//keyPayload = Bytes.concat(m.MessageHash, aesKeyEncrypted);
		// first 15 bytes are the hash that corresponds to the encrypted msg this key is for
		// aesKey
		
		// read in the hash of the originating message
		byte[] hash = Arrays.copyOfRange(keyPayload, 0, 15);
		byte[] encrypted_key = Arrays.copyOfRange(keyPayload, 15, 256+15);
		
		Log.v(TAG, "encrypted_key length is: " + String.valueOf(encrypted_key.length));
		Log.v(TAG, "encrypted_key bytes are: " + ByteUtilities.bytesToHex(encrypted_key));
		
		// let's decrypt the key so we can unlock the other message
		SecretKey symmetric_key = null;
		
		try {
			symmetric_key = rsaKey.unwrap(encrypted_key);
		} catch (GeneralSecurityException e) {
			Log.v(TAG, e.getMessage());
			logMessage("can't unwrap the damn key");
		}
		
		// map our messages hashes to our encryption keys
		hashToKey.put(ByteUtilities.bytesToHex(hash), symmetric_key.getEncoded());
		
		Log.v("DOIT", "key is: " + ByteUtilities.bytesToHex(symmetric_key.getEncoded()));
		
		return hash;
		
		
	}
	
		
	public void handleButtonPull(View view) {
		Log.v(TAG, "Start Get ID");
		
		// should this be available for both central and peripheral, or just central?
		bleMessenger.initRequestForData(ourMostRecentFriendsAddress);
		
	}
	
	public void handleButtonToggleBusy(View view) {
		
		// if we're currently busy, stop being busy
		/*
		if (bleMessenger.BusyStatus()) {
			bleMessenger.StopBusy();
			btnToggleBusy.setText("!Busy");
		} else {
			bleMessenger.StartBusy();
			btnToggleBusy.setText("Busy!");
		}*/
		bleMessenger.GooseBusy();
		
	}
    
	
	public void handleButtonBeAFriend(View view) {
		// now we need to create the payload with our friendly name and public key
		Log.v(TAG, "Advertising Toggle Pressed");
		
		currentTask = "normal";
		
		if (!visible) {
			Log.v(TAG, "Not currently visible, begin stuff");

			if (bleMessenger.BeFound()) {
				Log.v(TAG, "advertising supported");
			} else {
				Log.v(TAG, "advertising NOT supported");
			}
		} else {
			Log.v(TAG, "Go Invisible");
			bleMessenger.HideYourself();
		}
		
		
	}
	
	public void handleButtonStartPair(View view) {
		
		currentTask = "pair";
		
		// very few Android devices support advertising, so if you can, start off with advertising 
		if (bleMessenger.SupportsAdvertising) {
			bleMessenger.BeFound();
		} else {
			// central needs to scan, connect
			bleMessenger.ScanForPeers(2500); // scan for 2.5 seconds			
		}
		
		
		// send id
		
	}
	
	private byte[] benchGenerateMessage(int MessageSize) {
		// get the lorem text from file
		byte[] bytesLorem = null;
		byte[] bytesMessage = null;
		InputStream is = getResources().openRawResource(R.raw.lorem);
    			
		int currentMessageLength = 0;
		int maxcount = 0;
		
		while ((currentMessageLength < MessageSize) && maxcount < 1000) {
			maxcount++;
	    	try {
	    		if (currentMessageLength == 0) {
	    			bytesMessage = ByteStreams.toByteArray(is);
	    		}
	    		is.reset();
	    		bytesLorem = ByteStreams.toByteArray(is);
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	
	    	try {
				is.reset();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	bytesMessage = Bytes.concat(bytesMessage, bytesLorem);
	    	
	    	currentMessageLength = bytesMessage.length;
    	
		}
		
		return Arrays.copyOf(bytesMessage, MessageSize);
	}
	
	private String getUserName(ContentResolver cr) {
        
        String displayName = "";
         
        Cursor c = cr.query(ContactsContract.Profile.CONTENT_URI, null, null, null, null); 
         
        try {
            if (c.moveToFirst()) {
                displayName = c.getString(c.getColumnIndex("display_name"));
            }  else {
            	displayName = "nexus5";
            	Log.v(TAG, "can't get user name; no error");	
            }
            
        } catch (Exception x) {
        	Log.v(TAG, "can't get user name; error");
        	
		} finally {
            c.close();
        }
        
        return displayName;
	}

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
	public void handleButtonFindAFriend(View view) {
		logMessage("a: look around");

		currentTask = "normal";
		
		// calls back bleMessageStatus when peers are found
		bleMessenger.ScanForPeers(2500);
	}
		
	private void showMessage(String msg) {

		final String message = msg;
		final Context fctx = this;
		
		runOnUiThread(new Runnable() {
			  public void run() {
				  Toast.makeText(fctx, message, Toast.LENGTH_LONG).show();
			  }
			});
		
	}
	
	private void logMessage(String msg) {
		logMessage(msg, 0);
	}
	
	
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
		
	private static byte[] trim(byte[] bytes) {
		int i = bytes.length - 1;
		while(i >= 0 && bytes[i] == 0) { --i; }
		
		return Arrays.copyOf(bytes,  i+1);
	}
	
	// creates a message formatted for identity exchange
	private BleMessage identityMessage() {
		BleMessage m = new BleMessage();
		m.MessageType = (byte)1 & 0xFF;
		m.SenderFingerprint = rsaKey.PuFingerprint();
		m.RecipientFingerprint = new byte[20];
		
		m.setPayload(rsaKey.PublicKey());
		
		return m;
	}

	
	private SecretKey genAesKey() {
	
		// generate a symmetric key
		SecretKey key = null;
		try {
			key = KeyGenerator.getInstance("AES").generateKey();
		} catch (Exception e) {
			Log.v(TAG, "couldn't generate AES key");
		}
		return key;
		
	}
	
	private byte[] encryptedSymmetricKey(byte[] friendPuk, String secretKeyText) throws Exception {
    	PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(friendPuk));
    	Cipher mCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        mCipher.init(Cipher.WRAP_MODE, publicKey);
        
        SecretKey symmkey = new SecretKeySpec(secretKeyText.getBytes("UTF-8"), "AES");
        
        byte[] encryptedSK = mCipher.wrap(symmkey);
        
        return encryptedSK;
	}

    
}
