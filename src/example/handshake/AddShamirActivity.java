package example.handshake;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.google.common.primitives.Bytes;

import secretshare.ShamirSplitter;
import simpble.ByteUtilities;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.Toast;

public class AddShamirActivity extends Activity  {

	private FriendsDb mDbHelper;
	
	private static final String TAG = "AddShamirActivity";
	
	EditText textMessageContent;
	EditText textMessageIdentifier;
	
	Spinner spinTotalShares;
	Spinner spinMinShares;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_shamir);
        
        spinTotalShares = (Spinner) findViewById(R.id.num_total_shares);
        spinMinShares = (Spinner) findViewById(R.id.num_min_shares);
        
        textMessageContent = (EditText) findViewById(R.id.message_content);
        textMessageIdentifier = (EditText) findViewById(R.id.public_id);
        
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    
	public void handleButtonQueueMsg(View view) {
        
		String t = String.valueOf(spinTotalShares.getSelectedItem());
		String m = String.valueOf(spinMinShares.getSelectedItem());
		
		int totalShares = Integer.valueOf(t);
		int minShares = Integer.valueOf(m);
		
		String msgContent = textMessageContent.getText().toString();
        String msgId = textMessageIdentifier.getText().toString();
		
        ShamirSplitter splitMsg = new ShamirSplitter();
        
        SparseArray<String> shareList = splitMsg.Workin(minShares, totalShares, msgContent);
        
        // get a digest for the message, to define it
        MessageDigest md = null;
        
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
        
        // build a digest of the original payload
        byte[] plainTextAsBytes = null;
        plainTextAsBytes = msgContent.getBytes();
        byte[] digestAsBytes = md.digest(plainTextAsBytes);
        String digestAsText = ByteUtilities.bytesToHex(digestAsBytes);
        
        mDbHelper = new FriendsDb(this);
		
		long new_msg_id = 0;
		
		String msgtype = "drop";
		int i = 0;
		for (i = 0; i < shareList.size(); i++) {
			int shareNum = shareList.keyAt(i);
			String shareText = shareList.get(shareNum);
			
			String msgPayload = String.valueOf(minShares) + String.valueOf(shareNum) + digestAsText + shareText;
			
			try {			
				new_msg_id = mDbHelper.queueMsg("", msgPayload, msgtype);
			} catch (Exception x) {
				Log.v(TAG, "can't add share " + x.getMessage());
			}
		}
		
		Toast.makeText(this, "Added " + String.valueOf(i) + " shares as msgs.", Toast.LENGTH_SHORT).show();
		
		mDbHelper.close();
		
	}

}