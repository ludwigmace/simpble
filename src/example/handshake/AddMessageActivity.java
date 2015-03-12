
package example.handshake;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import simpble.ByteUtilities;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.Toast;

public class AddMessageActivity extends Activity  {

	private FriendsDb mDbHelper;
	
	private static final String TAG = "AddMessageActivity";
	
	EditText messageContent;
	Spinner friendSpinner;
	CheckBox chkEncrypt;
	
	SimpleCursorAdapter mAdapter;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_message);
        
		mDbHelper = new FriendsDb(getApplicationContext());
		
		Cursor friendsCursor = mDbHelper.fetchAllFriends();
		
        mAdapter = new SimpleCursorAdapter(getBaseContext(),
        		android.R.layout.simple_spinner_dropdown_item,
                friendsCursor,
                new String[] { FriendsDb.KEY_F_NAME},
                new int[] { android.R.id.text1 }, 0);
        
		friendSpinner = (Spinner) findViewById(R.id.friend_spinner);
		messageContent = (EditText) findViewById(R.id.message_content);
		
		mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		friendSpinner.setAdapter(mAdapter);
		
		chkEncrypt = (CheckBox) findViewById(R.id.chkEncrypt);
                
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
    

	public void handleButtonQueueMsg(View view) {

		boolean encrypt = false;
		
		if (chkEncrypt.isChecked()) {
			encrypt = true;
		}
		
		String msg_content = messageContent.getText().toString();
		
		Cursor cursorFriends = (Cursor) friendSpinner.getSelectedItem();

		String friend_name = cursorFriends.getString(cursorFriends.getColumnIndex("friend_name"));
		
		Log.v(TAG, "try to queue msg for " + friend_name);
		
		mDbHelper = new FriendsDb(this);
		
		long new_msg_id = 0;
		
		try {			
			new_msg_id = QueueMsg(friend_name, msg_content, encrypt);
		} catch (Exception x) {
			Log.v(TAG, "can't add msg " + x.getMessage());
			Toast.makeText(this, x.getMessage(), Toast.LENGTH_SHORT).show();
		}
		
		mDbHelper.close();
		
		Toast.makeText(this, "created msg with id " + String.valueOf(new_msg_id), Toast.LENGTH_SHORT).show();
		
		this.finish();
		
	}
    
	private long QueueMsg(String friend_name, String msg_content, boolean encrypt) {
		long new_msg_id = 0;

		if (encrypt) {

			// we need to pull the public key for the friend
			Cursor c = mDbHelper.fetchFriend(friend_name);
			c.moveToFirst();
			byte[] friendPuk = c.getBlob(c.getColumnIndex(FriendsDb.KEY_F_PUK));
			
			// generate a symmetric key
			SecretKey key = null;
			try {
				key = KeyGenerator.getInstance("AES").generateKey();
			} catch (Exception e) {
				Log.v(TAG, "couldn't generate AES key");
			}
			
			// encrypt our payload bytes
			try {
				Cipher encryptCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
				encryptCipher.init(Cipher.ENCRYPT_MODE, key);
				
				ByteArrayOutputStream outS = new ByteArrayOutputStream();
				CipherOutputStream cipherOutS = new CipherOutputStream(outS, encryptCipher);
				
				cipherOutS.write(msg_content.getBytes());
				cipherOutS.flush();
				cipherOutS.close();
				
				msg_content = ByteUtilities.bytesToHex(outS.toByteArray());
				
			
			} catch (Exception x) {
				Log.v(TAG, "couldn't encrypt final payload");
			}
						
			byte[] aesKeyEncrypted = null; 
			
			if (key != null) {
				try {
					aesKeyEncrypted = encryptedSymmetricKey(friendPuk, key);
				} catch (Exception e) {
					Log.v(TAG, "couldn't encrypt aes key");	
				}
			}
			

		// encrypt the message with the symmetric key
		// get the fingerprint of this message and build the key msg
		}
		
		new_msg_id = mDbHelper.queueMsg(friend_name, msg_content); // need to add argument for adding this friend's PuK
		
		return new_msg_id;
	}
	
	private byte[] encryptedSymmetricKey(byte[] friendPuk, SecretKey symmkey) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException {
    	PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(friendPuk));
    	Cipher mCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        mCipher.init(Cipher.WRAP_MODE, publicKey);
        
        byte[] encryptedSK = mCipher.wrap(symmkey);
        
        return encryptedSK;
	}

}