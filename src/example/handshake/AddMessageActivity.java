
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

		String msgtype = "";
		if (chkEncrypt.isChecked()) {
			msgtype = "encrypted";
		}
		
		String msg_content = messageContent.getText().toString();
		
		Cursor cursorFriends = (Cursor) friendSpinner.getSelectedItem();

		String friend_name = cursorFriends.getString(cursorFriends.getColumnIndex("friend_name"));
		
		Log.v(TAG, "try to queue msg for " + friend_name);
		
		mDbHelper = new FriendsDb(this);
		
		long new_msg_id = 0;
		
		try {			
			//new_msg_id = QueueMsg(friend_name, msg_content, encrypt);
			new_msg_id = mDbHelper.queueMsg(friend_name, msg_content, msgtype);
		} catch (Exception x) {
			Log.v(TAG, "can't add msg " + x.getMessage());
			Toast.makeText(this, x.getMessage(), Toast.LENGTH_SHORT).show();
		}
		
		mDbHelper.close();
		
		Toast.makeText(this, "created msg with id " + String.valueOf(new_msg_id), Toast.LENGTH_SHORT).show();
		
		this.finish();
		
	}
    


}