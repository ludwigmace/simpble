package example.handshake;

import java.io.IOException;
import java.security.GeneralSecurityException;

import java.security.KeyFactory;

import java.security.PublicKey;
import java.security.SecureRandom;

import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.HashMap;

import java.util.Map;


import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import simpble.BleApplicationMessage;
import simpble.BleApplicationPeer;
import simpble.BleMessenger;
import simpble.BleStatusCallback;
import simpble.ByteUtilities;

import com.google.common.primitives.Bytes;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import android.os.Bundle;
import android.provider.ContactsContract;
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
	private static final int DEBUGLEVEL = 0;

    private static final int ACTIVITY_CREATE=0;

	
	BleMessenger bleMessenger;
	
	// maybe for these guys I should leave these inside of the BleMessenger class?
	// because if I reference a BlePeer from here, I'm not hitting up the same memory address in BleMessenger 
	
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
    
    String currentTask;
	
    private boolean messageReceiving = false;
    private boolean messageSending = false;
    private boolean sendToAnybody = true;
    
    private boolean EnableSendID;
    private boolean EnableReceiveID;
    
    private Map<String, byte[]> hashToKey;
    private Map<String, byte[]> hashToPayload;
    
    private Map<String, String> addressesToFriends;
    
    private byte[] anonFP;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		currentTask = "";
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
		
		PopulateFriends();
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
			
			// for testing, don't add your peer
			bleFriends.put(peer_fp, new_peer);
			logMessage("adding peer " + peer_fp.substring(0,8));
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

		// we just got a notification
		public void peerNotification(String peerIndex, String notification) {
			
			// you don't know who this person is yet
			if (notification.equalsIgnoreCase("new_contract")) {
				logMessage("a: peripheral peer meets contract");
				
				// if we're willing to receive messages then we need to transmit our info to the other peer
				if (messageReceiving) {
					
					BleApplicationMessage idenM = identityMessage();
					String queuedMsg = "";
					
					if (idenM != null && EnableSendID) {
						queuedMsg = bleMessenger.peerMap.get(peerIndex).BuildBleMessageOut(idenM.GetAllBytes()).substring(0,8);
						logMessage("queued id msg for " + peerIndex);
					}
				}
				
				// subscribe to the peripheral's transport
				bleMessenger.initRequestForData(peerIndex);
			}
			
						
			// only peripheral mode gets this; we've accepted a connection and we're either wanting to pair or just data stuff
			if (notification.equalsIgnoreCase("accepted_connection")) {
				logMessage("a: connected to " + peerIndex);

				// since i've just accepted a connection, queue up an identity message
				BleApplicationMessage idenM = identityMessage();
				String queuedMsg = "";
				
				if (idenM != null && EnableSendID) {
					queuedMsg = bleMessenger.peerMap.get(peerIndex).BuildBleMessageOut(idenM.GetAllBytes()).substring(0,8);
					logMessage("queued id msg for " + peerIndex);
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

				BleApplicationMessage sentMsg = new BleApplicationMessage();
				
				sentMsg.SetRawBytes(bleMessenger.peerMap.get(peerIndex).getBleMessageOut(msg_id).GetAllBytes());
				
				final String peerSentTo = peerIndex;
				String sig = "no_signature";
				
				if (sentMsg.GetSignature() != null) {
					sig = sentMsg.GetSignature();
				}
				
				final String msgSignature = sig; 
				
				runOnUiThread(new Runnable() { public void run() { MarkMsgSent(msgSignature, peerSentTo); } });
				
				
			}
			
			
		}
		
		// this is when all the packets have come in, and a message is received in its entirety
		// TODO: too much happens in this callback; need to move things out of here!
		@Override
		public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes) {

			BleApplicationMessage incomingMsg = new BleApplicationMessage();
			
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
						
			switch (mt) {
				case BleMessenger.MSGTYPE_ID:
					Log.v(TAG, "received identity msg");
					
	
					// if we'll send to any old person, shouldn't this be on connect?
					// or how do we know when the other person wants messages?
					if (sendToAnybody) {
						
						BleApplicationMessage mTopic = null;
						
						try {
						// TODO: consider location as well
							mTopic = GetUnsentEligibleTopicMessage(senderFingerprint);
						} catch (Exception x) {
							Log.v(TAG, "error trying to pull an eligible topic message");
						}
	
						if (mTopic != null) {
							
							String queuedMsg = "";
							queuedMsg = bleMessenger.peerMap.get(remoteAddress).BuildBleMessageOut(mTopic.GetAllBytes()).substring(0,8);
							
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
						
						ArrayList<BleApplicationMessage> outMsgs = GetMessageForFriend(senderFingerprint);
	
						logMessage("a: known peer: " + senderFingerprint.substring(0,20));
						
						// we know that we have this peer as a friend
	
						// queue up all the messages we've got for this dude
						for (BleApplicationMessage m : outMsgs) {
	
							String queuedMsg = "";
							
							if (m != null) {
								// I just want to pass in raw bytes here
								queuedMsg = bleMessenger.peerMap.get(remoteAddress).BuildBleMessageOut(m.GetAllBytes()).substring(0,8);
								logMessage("a5: queued " + queuedMsg + " for " + remoteAddress);
								
							} else {
								logMessage("a: no msg found for " + remoteAddress);
							}						
						}
	
					} else if (EnableReceiveID) {  // if we actually care who this person is, then store their FP
						logMessage("a: this guy's FP isn't known to me: " + senderFingerprint.substring(0,20));
						
				        Intent i = new Intent(ctx, AddFriendsActivity.class);
				        i.putExtra("fp", senderFingerprint);
				        i.putExtra("puk", payload);
				        startActivityForResult(i, ACTIVITY_CREATE);
											
						// we don't know the sender and maybe should add them?
						// parse the public key & friendly name out of the payload, and add this as a new person
					}
					
					break;
					
				case BleMessenger.MSGTYPE_PLAIN:
				
					// TODO: store in database
					logMessage("message recvd of size " + String.valueOf(payload.length));
	
					break;
					
				case BleMessenger.MSGTYPE_ENCRYPTED_PAYLOAD:
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
					
				case BleMessenger.MSGTYPE_ENCRYPTED_KEY:
					logMessage("received encrypted key of size:" + String.valueOf(payload.length));
					
					byte[] incomingMessageHash = processIncomingKeyMsg(payload);
					
					Log.v(TAG, "added key for incoming msg hash " + ByteUtilities.bytesToHex(incomingMessageHash));
	
					byte[] encryptedPayload = hashToPayload.get(ByteUtilities.bytesToHex(incomingMessageHash));
					
					if (encryptedPayload != null ) {
						logMessage("found encrypted payload for the key we just got");
						// now decrypt!
					} else {
						logMessage("NO encrypted payload found for this new key");
					}
					
					break;
				
				case BleMessenger.MSGTYPE_DROP:
					
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
					
			}
			
		}


		@Override
		public void remoteServerAdded(String serverName) {
			showMessage(serverName);
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
	
	
	private ArrayList<BleApplicationMessage> GetMessageForFriend(String candidateFingerprint) {
		Cursor c = mDbHelper.fetchMsgsForFriend(candidateFingerprint);
		
		ArrayList<BleApplicationMessage> results = new ArrayList<BleApplicationMessage>();
		
		BleApplicationMessage m = null; 

		// if we have any messages
		if (c.getCount() > 0) {
				//loop over these messages
				while (c.moveToNext()) {
				
				m = new BleApplicationMessage();
				
				String recipient_name = c.getString(c.getColumnIndex(FriendsDb.KEY_M_FNAME));
				String msg_content = c.getString(c.getColumnIndex(FriendsDb.KEY_M_CONTENT));
				String msg_type = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGTYPE));
				String msg_signature = c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGID));
				byte[] puk = c.getBlob(c.getColumnIndex(FriendsDb.KEY_F_PUK));
				
				if (msg_signature == null) {
					msg_signature = "";
				}
		
				if (msg_type.equalsIgnoreCase("encrypt")) {
					
				}
				
				m.RecipientFingerprint = candidateFingerprint.getBytes();
				m.SenderFingerprint = myFingerprint.getBytes();  // should probably pull from database instead; for relaying of messages
				m.SetSignature(msg_signature);
				
				// in case we need to encrypt this message
				byte[] msgbytes = null;
				byte[] aesKeyEncrypted = null;

				// if our message is meant to be encrypted, do that first
				if (msg_type.equalsIgnoreCase("encrypted")) {
					
					//make this a random encryption key
					SecureRandom sr = new SecureRandom();
					byte[] aeskey = new byte[32]; // 512 bit key
					sr.nextBytes(aeskey);

					// and random IV
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
								aesKeyEncrypted = encryptedSymmetricKey(puk, aeskey);
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
					
					
					m.MessageType = (byte)BleMessenger.MSGTYPE_ENCRYPTED_PAYLOAD;
					m.setPayload(msgbytes);
					
					BleApplicationMessage m_key = new BleApplicationMessage();
					
					// get the fingerprint from the Friend object
					m_key.RecipientFingerprint = m.RecipientFingerprint;
					
					// gotta give it a pre-determined messagetype to know this is an encryption key
					m_key.MessageType = (byte)BleMessenger.MSGTYPE_ENCRYPTED_KEY;
					
					// get the sending fingerprint from the main message
					m_key.SenderFingerprint = m.SenderFingerprint;
					
					// the payload needs to include the encrypted key, and the orig msg's fingerprint
					// if the hash is a certain size, then we can assume the rest of the message is the
					// encrypted portion of the aes key
					byte[] aes_payload = Bytes.concat(m.MessageHash, aesKeyEncrypted);
					m_key.setPayload(aes_payload);
					
					results.add(m_key);
				
				} else {
					m.MessageType = (byte)BleMessenger.MSGTYPE_PLAIN;
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
	private BleApplicationMessage GetUnsentEligibleTopicMessage(String candidateFingerprint) {
		
		// topic messages eligible to go to this recipient
		Cursor c = mDbHelper.topicsNotSentToRecipient(candidateFingerprint);
		
		BleApplicationMessage m = null; 
		
		if (c.getCount() > 0) {
			c.moveToFirst();
			
			// found an eligible message in the database, so build it for the application
			m = new BleApplicationMessage();
			
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
			m.MessageType = (byte)(90 & 0xFF); // just throwing out 90 as indicating a share msg
			m.setPayload(msg_content.getBytes());
			
			m.SetSignature(msg_signature);
			
		}
		
		return m;

	}
		
	private static byte[] trim(byte[] bytes) {
		int i = bytes.length - 1;
		while(i >= 0 && bytes[i] == 0) { --i; }
		
		return Arrays.copyOf(bytes,  i+1);
	}
	
	// creates a message formatted for identity exchange
	private BleApplicationMessage identityMessage() {
		BleApplicationMessage m = new BleApplicationMessage();
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
	
	private byte[] encryptedSymmetricKey(byte[] friendPuk, byte[] secretKeyBytes) throws Exception {
    	PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(friendPuk));
    	Cipher mCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        mCipher.init(Cipher.WRAP_MODE, publicKey);
        
        SecretKey symmkey = new SecretKeySpec(secretKeyBytes, "AES");
        
        byte[] encryptedSK = mCipher.wrap(symmkey);
        
        return encryptedSK;
	}

    
}
