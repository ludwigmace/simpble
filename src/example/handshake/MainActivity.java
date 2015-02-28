package example.handshake;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

	private static final String TAG = "main";
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
	private Button btnPush;
	private Button btnPull;
	private Button btnSendId;
	
	private boolean visible;
	
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	
	// this is just temporary to allow setting an address for subscription that we can call manually later
	String ourMostRecentFriendsAddress;
	String statusLogText;
	
    private FriendsDb mDbHelper;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		statusLogText = "";
		
		// we're not showing ourselves when the program starts
		visible = false;
		
		// get a pointer to our Be A Friend button, and our transfer packet button
		btnAdvertise = (Button)findViewById(R.id.be_a_friend);
		
		btnPush = (Button)findViewById(R.id.pushmsgs);
		btnPull = (Button)findViewById(R.id.pullmsgs);
		
		
		// disable the Xfer button, because you're not connected and don't have anything set up to transfer
		btnPush.setEnabled(false);
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
        String userName = getUserName(this.getContentResolver());
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
		
		myFingerprint = ByteUtilities.bytesToHex(rsaKey.PuFingerprint());
		
		// get our BLE operations going
		if (btAdptr.isEnabled()) {
			bleMessenger = new BleMessenger(btMgr, btAdptr, this, bleMessageStatus);
		} // if not enabled, the onResume will catch this

		// if our SDK isn't lollipop, then disable our advertising button
		// for testing i can also tell which is my 5 and which is my Gnex
		if (Build.VERSION.SDK_INT < 21) {
			btnAdvertise.setEnabled(false);
		} else if (!btAdptr.isMultipleAdvertisementSupported()) {
			btnAdvertise.setEnabled(false);
		} else {
			btnAdvertise.setEnabled(true);			
		}
		
		
		// nexus 5: BC966C8DB89F0EBB91C82C97E561DF631DB96DC3
		// gnex: D6DB868F6260FB74ADFE6E340288E77FCA3BA9E5
		
		
		String testFriendFP = "";
		String testMessage = ""; 
		
		mDbHelper = new FriendsDb(this);
		
		if (myFingerprint.equalsIgnoreCase("BC966C8DB89F0EBB91C82C97E561DF631DB96DC3")) {
			testMessage = "i'm a nexus 5 and i love you SO MUCHCHCHCHCH - it's creepy";
			testFriendFP = "D6DB868F6260FB74ADFE6E340288E77FCA3BA9E5";
			
			//mDbHelper.createFriend("gnex", testFriendFP);
			
		} else if (myFingerprint.equalsIgnoreCase("D6DB868F6260FB74ADFE6E340288E77FCA3BA9E5")) {
			testMessage = "i'm a GNEX and i'm tolerant of your existence";
			testFriendFP = "BC966C8DB89F0EBB91C82C97E561DF631DB96DC3";
			
			//mDbHelper.createFriend("nex5", testFriendFP);
			
		} else {
			Log.v(TAG, "myFingerprint matches nothing!!!!");
		}
		
		
		mDbHelper.close();
		
		bleFriends = new HashMap<String, BlePeer>();

		// create the test message, identified as being sent by me
		/*
		BleMessage testBleMsg = new BleMessage();
		testBleMsg.MessageType = "datatext";
		testBleMsg.RecipientFingerprint = ByteUtilities.hexToBytes(testFriendFP);
		testBleMsg.SenderFingerprint = ByteUtilities.hexToBytes(myFingerprint); 
		testBleMsg.setPayload(testMessage.getBytes());
		
		// don't make a peer here - make something else!
		BlePeer testFriend = new BlePeer(""); // constructor takes an address; may want to have BleFriends and BlePeers as different classes
		testFriend.addBleMessageOut(testBleMsg);
		testFriend.SetFingerprint(testFriendFP);
			
		// let's add this friend
		bleFriends.put(testFriendFP, testFriend);
		*/
		if (myFingerprint != null) {
			logMessage("a: our fp is:" + myFingerprint.substring(0, 20) + " . . .");
			Log.v(TAG, "our fp:" + myFingerprint);
		} else {
			logMessage("a: global myFingerprint is null");
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
		
		if (id == R.id.action_add_message) {
			return true;
		}
		
		
		
		return super.onOptionsItemSelected(item);
	}
		
	BleStatusCallback bleMessageStatus = new BleStatusCallback() {

		// we just got a notification
		public void peerNotification(String peerIndex, String notification) {
			
			// this notification is that BleMessenger just found a peer that met the service contract
			// only central mode gets this
			if (notification.equalsIgnoreCase("new_contract")) {

				ourMostRecentFriendsAddress = peerIndex;
				logMessage("a: connected to " + peerIndex);
				BleMessage idenM = identityMessage();
				String queuedMsg = "";
				if (idenM != null) {
					queuedMsg = bleMessenger.peerMap.get(peerIndex).addBleMessageOut(idenM);
					logMessage("a: queued " + queuedMsg + " for " + peerIndex);
					
					// we've got at least 1 msg queued up to go out, so enable our push button
					runOnUiThread(new Runnable() { public void run() { btnPush.setEnabled(true); } });
				}
				
				// since we're a central we'll have to pull anything from the peripheral, so enable the Pull button
				runOnUiThread(new Runnable() { public void run() { btnPull.setEnabled(true); } });
			}
			
			// only peripheral mode gets this
			if (notification.equalsIgnoreCase("accepted_connection")) {
				logMessage("a: connected to " + peerIndex);

				// since i've just accepted a connection, queue up an identity message 
				BleMessage idenM = identityMessage();
				String queuedMsg = "";
				if (idenM != null) {
					queuedMsg = bleMessenger.peerMap.get(peerIndex).addBleMessageOut(idenM);
					logMessage("a: queued " + queuedMsg + " for " + peerIndex);
				}
				
				 // you don't have the fingerprint yet, you just know that this person meets the contract
				 runOnUiThread(new Runnable() { public void run() { btnPush.setEnabled(true); } });
			}
			
			if (notification.contains("msg_sent")) {
				logMessage("a: " + notification + " sent to " + peerIndex);
			}
			
			
		}
		
		// this is when all the packets have come in, and a message is received in its entirety (hopefully)
		// the secret sauce
		@Override
		public void handleReceivedMessage(String remoteAddress, String recipientFingerprint, String senderFingerprint, byte[] payload, String msgType) {

			logMessage("a: rcvd " + msgType + " msg for " + recipientFingerprint.substring(0, 10) + "...");
			
			// this is an identity message so handle it as such
			if (msgType.equalsIgnoreCase("identity")) {
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
					
					// we know that we have this peer as a friend
					// so now we can either get the message we have for this friend
					// and add to the peer in the BleMessenger list
					
					// let's keep them separate, and pass it in
					BleMessage m = bleFriends.get(senderFingerprint).getBleMessageOut();
					
					// if we've got a message, add it in
					logMessage("a: known peer: " + senderFingerprint.substring(0,20));
					
					String queuedMsg = "";
					if (m != null) {
						queuedMsg = bleMessenger.peerMap.get(remoteAddress).addBleMessageOut(m);
						logMessage("a: queued " + queuedMsg + " for " + remoteAddress);
						
					}
					
				} else {
					logMessage("a: this guy's FP isn't known to me: " + senderFingerprint.substring(0,20));
										
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
	
	public void handleShowFriends(View view) {
		/*
        mDbHelper = new FriendsDbAdapter(this);
        mDbHelper.open();

		mFriendsCursor = mDbHelper.fetchAllFriends();
		
		statusLogText = "";
		
		if (mFriendsCursor.getCount() > 0) {
		
		while (mFriendsCursor.moveToNext()) {
			String friend_name = mFriendsCursor.getString(mFriendsCursor.getColumnIndex("friend_name"));
			String friend_fp = mFriendsCursor.getString(mFriendsCursor.getColumnIndex("friend_fp"));
			
			logMessage(friend_name + ": " + friend_fp);
		}
		} else {
			logMessage("no friends!");
		}
		
		mFriendsCursor.close();
		
        mDbHelper.close();
		*/
	}
		
	public void handleButtonPull(View view) {
		Log.v(TAG, "Start Get ID");
		
		// should this be available for both central and peripheral, or just central?
		bleMessenger.getPeripheralIdentifyingInfo(ourMostRecentFriendsAddress);
		
	}
	
	public void handleButtonPush(View view) {
		
		// iterate over our currently connected folks and see if anybody needs a message we have
		logMessage("a: iterating over " + String.valueOf(bleMessenger.peerMap.keySet().size()) + " connected peers");
		
		for (String remoteAddress : bleMessenger.peerMap.keySet()) {
			// send pending messages to this peer
			logMessage("a: " + remoteAddress + " needs " + bleMessenger.peerMap.get(remoteAddress).PendingMessageCount() + " msgs");
			bleMessenger.sendMessagesToPeer(remoteAddress);
		}
		
	}
    
	
	public void handleButtonBeAFriend(View view) {
		// now we need to create the payload with our friendly name and public key
		Log.v(TAG, "Advertising Toggle Pressed");
		
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

		// calls back bleMessageStatus when peers are found
		bleMessenger.ShowFound();
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
		m.MessageType = "identity";
		m.SenderFingerprint = rsaKey.PuFingerprint();
		m.RecipientFingerprint = new byte[20];
		
		m.setPayload(rsaKey.PublicKey());
		
		return m;
	}
	
}
