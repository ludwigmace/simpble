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
	Map <String, BlePeer> bleFriends;
	String myFingerprint;
	String myIdentifier;
	
	KeyStuff rsaKey;
	TextView statusText;
	
	private Button btnAdvertise;
	private boolean visible;
	
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// we're not showing ourselves when the program starts
		visible = false;
		
		// get a pointer to our Be A Friend button 
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
        myIdentifier = Installation.id(this);
        
        // get your name (that name part isn't working)
        String userName = getUserName(this.getContentResolver());
        EditText yourNameControl = (EditText) findViewById(R.id.your_name);
        yourNameControl.setText(userName);
        
        statusText = (TextView) findViewById(R.id.status_log);
        
        rsaKey = null;
        
		try {
			rsaKey = new KeyStuff(this, myIdentifier);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// get our BLE operations going
		if (btAdptr.isEnabled()) {
			bleMessenger = new BleMessenger(btMgr, btAdptr, this, bleMessageStatus);
		} // if not enabled, the onResume will catch this
		
		String testFriend = "";
		String testMessage = "";
		
		// if our SDK isn't lollipop, then disable our advertising button
		// for testing i can also tell which is my 5 and which is my Gnex
		if (Build.VERSION.SDK_INT < 21) {
			btnAdvertise.setEnabled(false);
		} else if (!btAdptr.isMultipleAdvertisementSupported()) {
			btnAdvertise.setEnabled(false);
			testFriend = "4088D5A01D57320CA7D5E60A42ACD4EA333C5005";
			testMessage = "I'd like to tell you about my feet.";
		} else {
			testFriend = "ABCDEF";
			testMessage = "I'd like to tell you about my hands.";			
		}
		
		// generate message of particular byte size
		//byte[] bytesMessage = benchGenerateMessage(45);

		bleFriends = new HashMap<String, BlePeer>();

		// make a test message
		BleMessage testBleMsg = new BleMessage();
		testBleMsg.MessageType = "datatext";
		testBleMsg.RecipientFingerprint = ByteUtilities.hexToBytes(testFriend);
		testBleMsg.SenderFingerprint = ByteUtilities.hexToBytes("5555EFA01D57320CA7D5E60A42ACD4EA333C7117"); 
		testBleMsg.setMessage(testMessage.getBytes());

		// create our test peer, and add this message for them
		BlePeer testPeer = new BlePeer("");
		testPeer.addBleMessageOut(testBleMsg);
		testPeer.SetFingerprint(testFriend);
		bleFriends.put(testFriend, testPeer);
		
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
	
	// creates a message formatted for identity exchange
	private BleMessage identityMessage(byte[] payload) {
		BleMessage m = new BleMessage();
		m.MessageType = "identity";
		m.SenderFingerprint = rsaKey.PublicKey();
		m.RecipientFingerprint = new byte[20];
		
		m.setMessage(payload);
		
		return m;
	}

	
	BleStatusCallback bleMessageStatus = new BleStatusCallback() {

		@Override
		public void handleReceivedMessage(String recipientFingerprint, String senderFingerprint, byte[] payload, String msgType) {

			//Log.v(TAG, "received msg of type:"+ msgType);
			logMessage("received msg of type:" + msgType);
			
			
			// this is an identity message so handle it as such
			if (msgType.equalsIgnoreCase("identity")) {
				Log.v(TAG, "received identity msg"); 
				
				
				if (recipientFingerprint.length() == 0) {
					// there is no recipient; this is just an identifying message
				} else if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					// recipient is us!
				} else if (bleFriends.containsValue(recipientFingerprint)) {
					Log.v(TAG, "received msg from existing friend, payload size:"+ String.valueOf(payload.length));
				}
				
				if (bleFriends.containsKey(senderFingerprint)) {
					logMessage("known peer: " + senderFingerprint.substring(0,20));

					// now send some messages to this peer!
					bleMessenger.sendMessagesToPeer(bleFriends.get(senderFingerprint));
				} else {
					BlePeer b = new BlePeer("");

					bleFriends.put(senderFingerprint, b);
					logMessage("new peer: " + senderFingerprint.substring(0,20));
					//Log.v(TAG, "received msg from new friend, payload size:"+ String.valueOf(payload.length));
					// we don't know the sender and should add them;
					// parse the public key & friendly name out of the payload, and add this as a new person
				}
				
				// the First message we send them, however, needs to be our own ID message
			} else {
				logMessage("received data msg of size:" + String.valueOf(payload.length));
				Log.v(TAG, "received data msg, payload size:"+ String.valueOf(payload.length));
			}
			
		}
		
		@Override
		public void messageSent(UUID uuid) {
			
			final String sUUID = uuid.toString(); 
			
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                	showMessage("message sent:" + sUUID);
                }
            });
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
						btnAdvertise.setText("Hide Yourself!");
				  }
				});
			
		}

		@Override
		public void advertisingStopped() {
			logMessage("advertising stopped");
			
			runOnUiThread(new Runnable() {
				  public void run() {
						visible = false;
						btnAdvertise.setText("Show Yourself!");
				  }
				});

			
		}

		@Override
		public void headsUp(String msg) {
			logMessage(msg);
		}
		
		
	};
	
	public void handleButtonBeAFriend(View view) {
		// now we need to create the payload with our friendly name and public key
		Log.v(TAG, "Advertising Toggle Pressed");
		
		if (!visible) {
			Log.v(TAG, "Not currently visible, begin stuff");
	        KeyStuff rsaKey = null;
	        
			try {
				rsaKey = new KeyStuff(this, myIdentifier);
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			myFingerprint = bytesToHex(rsaKey.PuFingerprint());
			
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
	
	public void queueOutboundMessage(String destinationFingerprint, byte[] message) {
		
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
		// tell bleMessenger to look for folks and use the callback bleMessageStatus
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

		String oldText = statusText.getText().toString();
		
		final String newText = oldText + "\n" + "- " + msg;
		
		runOnUiThread(new Runnable() {
			  public void run() {
				  statusText.setText(newText);
			  }
			});
		
	}
	
	
	
	
	private static byte[] trim(byte[] bytes) {
		int i = bytes.length - 1;
		while(i >= 0 && bytes[i] == 0) { --i; }
		
		return Arrays.copyOf(bytes,  i+1);
	}
	
}
