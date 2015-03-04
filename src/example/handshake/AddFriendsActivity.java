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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class AddFriendsActivity extends Activity  {

	private FriendsDb mDbHelper;
	
	private static final String TAG = "AddFriendsActivity";
	
	EditText friendName;
	EditText friendFp;
	EditText friendPuk;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);
        
		friendName = (EditText) findViewById(R.id.friend_name);
		friendFp = (EditText) findViewById(R.id.friend_fp);
		friendPuk= (EditText) findViewById(R.id.friend_puk);
        
        Bundle extras = getIntent().getExtras();
        
        if (extras != null) {
        	String fp = extras.getString("fp");
        	byte[] puk = extras.getByteArray("puk");
        	
        	friendFp.setText(fp);
        	friendPuk.setText(ByteUtilities.bytesToHex(puk));
        	
        }
        
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
    

	public void handleButtonAddaFriend(View view) {

		String fn = friendName.getText().toString();
		String fp = friendFp.getText().toString();
		String fk = friendPuk.getText().toString();
		
		Log.v(TAG, "try to add this friend");
		
		mDbHelper = new FriendsDb(this);
		
		long new_friend_id = 0;
		
		try {
			
			// only read the first 294 bytes - probably not the best idea
			byte[] puk = Arrays.copyOf(ByteUtilities.hexToBytes(fk), 294);
		
			new_friend_id = mDbHelper.createFriend(fn, fp, puk); // need to add argument for adding this friend's PuK
		} catch (Exception x) {
			Log.v(TAG, "can't add friend " + x.getMessage());
			Toast.makeText(this, x.getMessage(), Toast.LENGTH_SHORT).show();
		}
		
		mDbHelper.close();
		
		Toast.makeText(this, "created friend with id " + String.valueOf(new_friend_id), Toast.LENGTH_SHORT).show();
		
		this.finish();
		
	}
    

}