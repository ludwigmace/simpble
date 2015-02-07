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

	BleMessenger bleMessenger;
	
	// maybe for these guys I should leave these inside of the BleMessenger class?
	// because if I reference a BlePeer from here, I'm not hitting up the same memory address in BleMessenger 
	
	Map <String, BlePeer> bleFriends;  // folks whom i have previously connected to, or i have their id info
	Map <String, BlePeer> bleFolks; // folks i have connected to for this session of the program's memory
	
	
	String myFingerprint;
	String myIdentifier;
	
	KeyStuff rsaKey;
	TextView statusText;
	
	private Button btnAdvertise;
	private Button btnXfer;
	private Button btnGetId;
	private Button btnSendId;
	
	private boolean visible;
	
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	
	// this is just temporary to allow setting an address for subscription that we can call manually later
	String ourMostRecentFriendsAddress;
	String statusLogText;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		statusLogText = "";
		
		// we're not showing ourselves when the program starts
		visible = false;
		
		// get a pointer to our Be A Friend button, and our transfer packet button
		btnAdvertise = (Button)findViewById(R.id.be_a_friend);
		btnXfer = (Button)findViewById(R.id.xfer_packet);
		btnGetId = (Button)findViewById(R.id.get_id);
		btnSendId = (Button)findViewById(R.id.send_id);
		
		
		// disable the Xfer button, because you're not connected and don't have anything set up to transfer
		btnXfer.setEnabled(false);
		// disable Id button, because you're not even connected yet, and thus not ready to identify
		btnGetId.setEnabled(false);
		btnSendId.setEnabled(false);
		
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
		
		// this part should be populated by a database or previous application data
		String testFriendFP = "";
		String testMessage = "";
		
		// nexus 5: BC966C8DB89F0EBB91C82C97E561DF631DB96DC3
		// gnex: D6DB868F6260FB74ADFE6E340288E77FCA3BA9E5
		if (myFingerprint.equalsIgnoreCase("BC966C8DB89F0EBB91C82C97E561DF631DB96DC3")) {
			testMessage = "i'm a nexus 5 and i love you SO MUCHCHCHCHCH - it's creepy";
			testFriendFP = "D6DB868F6260FB74ADFE6E340288E77FCA3BA9E5";
		} else if (myFingerprint.equalsIgnoreCase("D6DB868F6260FB74ADFE6E340288E77FCA3BA9E5")) {
			testMessage = "i'm a GNEX and i'm tolerant of your existence";
			testFriendFP = "BC966C8DB89F0EBB91C82C97E561DF631DB96DC3";
		} else {
			Log.v(TAG, "myFingerprint matches nothing!!!!");
		}
		 		
		// generate message of particular byte size
		//byte[] bytesMessage = benchGenerateMessage(45);

		bleFolks = new HashMap<String, BlePeer>();
		bleFriends = new HashMap<String, BlePeer>();

		// create the test message, identified as being sent by me
		BleMessage testBleMsg = new BleMessage();
		testBleMsg.MessageType = "datatext";
		testBleMsg.RecipientFingerprint = ByteUtilities.hexToBytes(testFriendFP);
		testBleMsg.SenderFingerprint = ByteUtilities.hexToBytes(myFingerprint); 
		testBleMsg.setMessage(testMessage.getBytes());
		
		// don't make a peer here
		BlePeer testPeer = new BlePeer(""); // constructor takes an address; since this goes
		testPeer.addBleMessageOut(testBleMsg);
		testPeer.SetFingerprint(testFriendFP);
		
		bleMessenger.QueueUpFriend(testPeer);
		
		bleFriends.put(testFriendFP, testPeer);
		
		if (myFingerprint != null) {
			logMessage("our fp is:" + myFingerprint.substring(0, 20) + " . . .");
			Log.v(TAG, "our fp:" + myFingerprint);
		} else {
			logMessage("global myFingerprint is null");
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
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
		
	BleStatusCallback bleMessageStatus = new BleStatusCallback() {

		// this is when all the packets have come in, and a message is received in its entirety (hopefully)
		@Override
		public void handleReceivedMessage(String recipientFingerprint, String senderFingerprint, byte[] payload, String msgType) {

			//Log.v(TAG, "received msg of type:"+ msgType);
			logMessage("received msg of type:" + msgType);
			logMessage("msg intended for " + recipientFingerprint);
			
			// this is an identity message so handle it as such
			if (msgType.equalsIgnoreCase("identity")) {
				Log.v(TAG, "received identity msg");
								
				if (recipientFingerprint.length() == 0) {
					// there is no recipient; this is just an identifying message
					logMessage("no particular recipient for this msg");
				} else if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					logMessage("msg intended for us");
				} else {
					// TODO: something
				}
				
				// if the sender is in our friends list
				if (bleFriends.containsKey(senderFingerprint)) {
					logMessage("known peer: " + senderFingerprint.substring(0,20));

					
					 // enable the Xfer button
					runOnUiThread(new Runnable() { public void run() { btnXfer.setEnabled(true); } });
					
					// now send some messages to this peer - we'll already have our Id message queued up
					//bleMessenger.sendMessagesToPeer(bleFolks.get(senderFingerprint));
					
					// pull our peer's info from our friends group, put in Folks (friends are forever, folks are around now)
					bleFolks.put(senderFingerprint, bleFriends.get(senderFingerprint));
				} else {
					logMessage("new peer: " + senderFingerprint.substring(0,20));
					
					BlePeer b = new BlePeer("");
					b.SetFingerprint(senderFingerprint);
					b.addBleMessageOut(identityMessage());

					bleFolks.put(senderFingerprint, b);
					
					//Log.v(TAG, "received msg from new friend, payload size:"+ String.valueOf(payload.length));
					// we don't know the sender and should add them;
					// parse the public key & friendly name out of the payload, and add this as a new person
				}
				
				// the First message we send them, however, needs to be our own ID message
				
				// enable the "send our id" button
				runOnUiThread(new Runnable() { public void run() { btnSendId.setEnabled(true); }});
				
				
			} else {
				logMessage("received data msg of size:" + String.valueOf(payload.length));
				
				if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					logMessage("message is for us (as follows):");
					logMessage(new String(payload));
				} else {
					logMessage("message isn't for us");
				}
				
				Log.v(TAG, "received data msg, payload size:"+ String.valueOf(payload.length));
			}
			
		}
		
		@Override
		public void messageSent(byte[] MessageHash, BlePeer blePeer) {
			logMessage("message sent to " + blePeer.GetFingerprint().substring(0,20));
			
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
			logMessage("advertising started");
			
			runOnUiThread(new Runnable() {
				  public void run() {
						visible = true;
						btnAdvertise.setText("!Adv");
				  }
				});
			
		}

		@Override
		public void advertisingStopped() {
			logMessage("advertising stopped");
			
			runOnUiThread(new Runnable() {
				  public void run() {
						visible = false;
						btnAdvertise.setText("Advt");
				  }
				});

			
		}

		@Override
		public void headsUp(String msg) {
			logMessage(msg);
		}

		@Override
		public void readyToTalk(String remo) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void headsUp(String msg, String action) {
			logMessage(msg);
			
			// quick and dirty way to call actions passed from other object
			String task = action.substring(0, action.indexOf("="));
			String target = action.substring(action.indexOf("=")+1, action.length());
			
			Log.v(TAG, "task=" + task + ";target=" + target);
			
			if (task.equalsIgnoreCase("subscribe")) {
				// now that we've got a target to subscribe to, enable our start Id button
				 ourMostRecentFriendsAddress = target;
				  
				 // enable the Get Id button
				runOnUiThread(new Runnable() { public void run() { btnGetId.setEnabled(true); } });
			}
			
		}
		
		
	};
	
	public void handleButtonXfer(View view) {
		Log.v(TAG, "Xfer Toggle Pressed");

		// iterate over our currently connected folks and see if anybody needs a message we have
		logMessage("iterating over " + String.valueOf(bleFolks.keySet().size()) + " entries in our folks keyset");
		
		for (String fp : bleFolks.keySet()) {
			// send pending messages to this peer
			logMessage("send messages to peer fp: " + fp);
			bleMessenger.sendMessagesToPeer(fp);
		}
		
	}
	
	public void handleButtonGetID(View view) {
		Log.v(TAG, "Start Get ID");
		
		// right now this is a public method, but will end up being private
		bleMessenger.getPeripheralIdentifyingInfo(ourMostRecentFriendsAddress);
		
	}
	
	public void handleButtonSendID(View view) {
		Log.v(TAG, "Start Send ID to: " + ourMostRecentFriendsAddress);		
		// now send some messages to this peer - we'll already have our Id message queued up
		
		// the other party should have been added to our friends list, bleFolks
		// just get one
		BlePeer p = bleFolks.values().iterator().next();
		
		// if we pull a friend from the next one in the list, send to that peer
		if (p != null) {
			logMessage("send to:" + ByteUtilities.bytesToHexShort(p.GetFingerprintBytes()));
			bleMessenger.sendMessagesToPeer(p.GetFingerprint());
			
		} else {
			logMessage("can't get a bleFriend to send to");
		}
	}
    
	
	public void handleButtonBeAFriend(View view) {
		// now we need to create the payload with our friendly name and public key
		Log.v(TAG, "Advertising Toggle Pressed");
		
		if (!visible) {
			Log.v(TAG, "Not currently visible, begin stuff");

			
			BleMessage m = new BleMessage();
			
			m.MessageType = "identity";
			m.SenderFingerprint = rsaKey.PuFingerprint();
			m.RecipientFingerprint = new byte[20]; // blank recipient for Id message
			
			// since this is an identity message, the payload is my public key
			m.setMessage(rsaKey.PublicKey());
	
			// now add this message as our identifier to BleMessenger to send upon any new connection
			bleMessenger.idMessage = m;
			
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
		logMessage("look around");

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

		statusLogText = "- " + msg + "\n" + statusLogText;
		
		// String oldText = statusText.getText().toString();
		
		//final String newText = oldText + "\n" + "- " + msg;
		
		runOnUiThread(new Runnable() {
			  public void run() {
				  statusText.setText(statusLogText);
			  }
		});
		
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
		
		m.setMessage(rsaKey.PublicKey());
		
		return m;
	}
	
}
