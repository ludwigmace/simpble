
package example.handshake;

import java.util.Arrays;

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
                
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
    

	public void handleButtonQueueMsg(View view) {

		String msg_content = messageContent.getText().toString();
		
		Cursor cursorFriends = (Cursor) friendSpinner.getSelectedItem();

		String friend_name = cursorFriends.getString(cursorFriends.getColumnIndex("friend_name"));
		
		Log.v(TAG, "try to queue msg for " + friend_name);
		
		mDbHelper = new FriendsDb(this);
		
		long new_msg_id = 0;
		
		try {
			
			// only read the first 294 bytes - probably not the best idea
			//byte[] puk = Arrays.copyOf(ByteUtilities.hexToBytes(fk), 294);
		
			new_msg_id = QueueMsg(friend_name, msg_content);
		} catch (Exception x) {
			Log.v(TAG, "can't add msg " + x.getMessage());
			Toast.makeText(this, x.getMessage(), Toast.LENGTH_SHORT).show();
		}
		
		mDbHelper.close();
		
		Toast.makeText(this, "created msg with id " + String.valueOf(new_msg_id), Toast.LENGTH_SHORT).show();
		
		this.finish();
		
	}
    
	private long QueueMsg(String friend_name, String msg_content) {
		long new_msg_id = 0;
		
		new_msg_id = mDbHelper.queueMsg(friend_name, msg_content); // need to add argument for adding this friend's PuK
		
		
		return new_msg_id;
	}

}