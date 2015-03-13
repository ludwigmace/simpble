package example.handshake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

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
	private static final int DEBUGLEVEL = 1;

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
	private Button btnToggleBusy;
	private Button btnPull;
	private Button btnStartPair;
	
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
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		currentTask = "";
		statusLogText = "";
		ctx = this;
		
		// do you want to be able to receive messages?
		// TODO: have the UI capture this
		messageReceiving = true;
		
		// we're not showing ourselves when the program starts
		visible = false;
		
		// get a pointer to our Be A Friend button, and our transfer packet button
		btnAdvertise = (Button)findViewById(R.id.be_a_friend);
		
		btnToggleBusy = (Button)findViewById(R.id.toggle_busy);
		btnPull = (Button)findViewById(R.id.pullmsgs);
		btnStartPair = (Button)findViewById(R.id.startpair);
		
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
        myIdentifier = Installation.id(this);
        
        // get your name (that name part isn't working on Android 5.0)
        //String userName = getUserName(this.getContentResolver());
        String userName = btAdptr.getName();
        
        EditText yourNameControl = (EditText) findViewById(R.id.your_name);
        yourNameControl.setText(userName);
        
        // get a pointer to the status text
        statusText = (TextView) findViewById(R.id.status_log);
        
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
		
		PopulateFriendsAndMessages();
		
	}
	
	private void PopulateFriendsAndMessages() {
		mDbHelper = new FriendsDb(this);
		
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

		c = mDbHelper.fetchAllMsgs();
		
		// we want to send some messages
		if (c.getCount() > 0) {
			messageSending = true;
		}
		
		while (c.moveToNext()) {

			BleMessage m = new BleMessage();
			
			String recipient_name = c.getString(c.getColumnIndex(FriendsDb.KEY_M_FNAME));
			String msg_content = c.getString(c.getColumnIndex(FriendsDb.KEY_M_CONTENT));
			String msg_type =  c.getString(c.getColumnIndex(FriendsDb.KEY_M_MSGTYPE));
			
			//logMessage("found msg to add for " + recipient_name);
			
			// inefficient way to get peer stuff
			for (BlePeer p: bleFriends.values()) {

				// if the peer in our friends list equals the name we've pulled out of the database for this message
				if (p.GetName().equalsIgnoreCase(recipient_name)) {
					String msgHash = "";
					
					try {					
						// get the fingerprint from the Friend object
						m.RecipientFingerprint = p.GetFingerprintBytes();
						
						// just call this msg raw data - should we?
						m.MessageType = (byte)(2 & 0xFF);  //msg type for identity is 1, raw data is 2
						
						// get the sending fingerprint from our global variable
						// TODO: this won't work if the original sender is different
						m.SenderFingerprint = ByteUtilities.hexToBytes(myFingerprint);
						
						// in case we need to encrypt this message
						byte[] msgbytes = null;
						byte[] aesKeyEncrypted = null;

						// if our message is meant to be encrypted, do that first
						if (msg_type.equalsIgnoreCase("encrypted")) {
							
							// generate a brand new symmetric key
							SecretKey key = genAesKey();
							
							// get our friend's public key from the friend's object
							byte[] friendPuk = p.GetPublicKey();
							
							// encrypt the body of this message with our symmetric key
							msgbytes = encryptMsg(key, msg_content);
							
							// encrypt our encryption key using our recipient's public key 							
							if (key != null) {
								try {
									aesKeyEncrypted = encryptedSymmetricKey(friendPuk, key);
								} catch (Exception e) {
									Log.v(TAG, "couldn't encrypt aes key");	
								}
							}
							
						} else {
							msgbytes = msg_content.getBytes();
						}
						
						m.setPayload(msgbytes);
						
						
						if (msg_type.equalsIgnoreCase("encrypted")) {
							
							BleMessage m_key = new BleMessage();
							
							// get the fingerprint from the Friend object
							m_key.RecipientFingerprint = p.GetFingerprintBytes();
							
							// gotta give it a pre-determined messagetype to know this is an encryption key
							m_key.MessageType = (byte)(10 & 0xFF);
							
							// get the sending fingerprint from the main message
							m_key.SenderFingerprint = m.SenderFingerprint;
							
							// the payload needs to include the encrypted key, and the orig msg's fingerprint
							// if the hash is a certain size, then we can assume the rest of the message is the
							// encrypted portion of the aes key
							byte[] aes_payload = Bytes.concat(m.MessageHash, aesKeyEncrypted);
							m_key.setPayload(aesKeyEncrypted);
						
						}
						
						
						
						p.addBleMessageOut(m);
						
						try {
							msgHash = ByteUtilities.bytesToHex(m.MessageHash).substring(0, 8);
						} catch (Exception e) {
							msgHash = "err";
						}
						
						logMessage("queued " + msgHash  + " for " + recipient_name);						
						
						break;
					} catch (Exception x) {
						logMessage("e: " + x.getMessage());
					}
					

				}
			}
			
		}
		
		mDbHelper.close();
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
					
					// go ahead and send this other person our stuff
					// try1: runOnUiThread(new Runnable() { public void run() { bleMessenger.sendMessagesToPeer(ourMostRecentFriendsAddress);} });
					
					// we've got at least 1 msg queued up to go out, so enable our push button
					//runOnUiThread(new Runnable() { public void run() { btnPush.setEnabled(true); } });
				}
				
				// since we're a central we'll have to pull anything from the peripheral, so enable the Pull button
				//runOnUiThread(new Runnable() { public void run() { btnPull.setEnabled(true); } });
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
				
				 // you don't have the fingerprint yet, you just know that this person meets the contract
				 //runOnUiThread(new Runnable() { public void run() { btnPush.setEnabled(true); } });
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
				 //runOnUiThread(new Runnable() { public void run() { btnPush.setEnabled(true); } });
			}
			
			if (notification.equalsIgnoreCase("connection_change")) {
				logMessage("a: connection status changed for " + peerIndex);
			}
			
			if (notification.equalsIgnoreCase("server_disconnnected")) {
				logMessage("a: disconnected from " + peerIndex);
			}
			
			if (notification.contains("msg_sent")) {
				logMessage("a: " + notification + " sent to " + peerIndex);
			}
			
			
		}
		
		// this is when all the packets have come in, and a message is received in its entirety (hopefully)
		// the secret sauce
		@Override
		public void handleReceivedMessage(String remoteAddress, String recipientFingerprint, String senderFingerprint, byte[] payload, int msgType) {

			
			
			logMessage("a: rcvd " + msgType + " msg for " + recipientFingerprint.substring(0, 10) + "...");
			
			// this is an identity message so handle it as such
			if (msgType == 1) {
				Log.v(TAG, "received identity msg");
								
				if (recipientFingerprint.length() == 0) {
					// there is no recipient; this is just an identifying message
					logMessage("a: no particular recipient for this msg");
				} else if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					logMessage("a: msg intended for us");
				} else {
					// TODO: what if it's being forwarded?
				}
				
				// if the sender is in our friends list
				if (bleFriends.containsKey(senderFingerprint)) {

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
				
			} else {
				logMessage("a: received data msg of size:" + String.valueOf(payload.length));
				
				if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					logMessage("a: message is for us (as follows, next line):");
					logMessage(new String(payload));
				} else {
					logMessage("a: message isn't for us");
				}
				
				Log.v(TAG, "received data msg, payload size:"+ String.valueOf(payload.length));
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

	private byte[] encryptMsg(SecretKey key, String msg_content) {
			
		byte[] encrypted = null;
		
		// encrypt our payload bytes
		try {
			Cipher encryptCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			encryptCipher.init(Cipher.ENCRYPT_MODE, key);
			
			ByteArrayOutputStream outS = new ByteArrayOutputStream();
			CipherOutputStream cipherOutS = new CipherOutputStream(outS, encryptCipher);
			
			cipherOutS.write(msg_content.getBytes());
			cipherOutS.flush();
			cipherOutS.close();
			
			encrypted = outS.toByteArray();
		
		
		} catch (Exception x) {
			Log.v(TAG, "couldn't encrypt final payload");
		}
					
		return encrypted;
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
	
	private byte[] encryptedSymmetricKey(byte[] friendPuk, SecretKey symmkey) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException {
    	PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(friendPuk));
    	Cipher mCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        mCipher.init(Cipher.WRAP_MODE, publicKey);
        
        byte[] encryptedSK = mCipher.wrap(symmkey);
        
        return encryptedSK;
	}
	
}
