package example.handshake;

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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class FriendsActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final String TAG = "FriendsActivity";
	long current_friend;
	SimpleCursorAdapter mAdapter;
    ListView mListView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.friends_list);
       
        current_friend = 0;
        
        mAdapter = new SimpleCursorAdapter(getBaseContext(),
                R.layout.listview_item_layout,
                null,
                new String[] { FriendsDb.KEY_ROWID, FriendsDb.KEY_NAME, FriendsDb.KEY_FP},
                new int[] { R.id.rowid, R.id.name, R.id.fp }, 0);
        
        mListView = (ListView) findViewById(R.id.friendslistview);
        mListView.setAdapter(mAdapter);
        
        
        
        mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				FriendsDb mDbHelper;
				mDbHelper = new FriendsDb(getApplicationContext());
				
				Cursor c = mDbHelper.fetchFriend(id);
				
				c.moveToFirst();
				
				String peer_fp = c.getString(c.getColumnIndex(FriendsDb.KEY_FP));
				byte[] peer_puk = c.getBlob(c.getColumnIndex(FriendsDb.KEY_PUK));
				
				boolean keyvalid = KeyStuff.CheckFingerprint(peer_puk, peer_fp);
				
				if (keyvalid) {
					showMessage("fp matches the puk");
				} else {
					showMessage("fp doesn't match the puk");
				}
				
				Log.v(TAG, "puk: " + ByteUtilities.bytesToHex(peer_puk));
			}
        
        	}); 
        
        getLoaderManager().initLoader(0, null, this);

    }
    
    private void showMessage(String msg) {
    	Toast.makeText(FriendsActivity.this, msg, Toast.LENGTH_SHORT).show();
    }
    
    
    
    // These are the Contacts rows that we will retrieve.
    static final String[] FRIENDS_SUMMARY_PROJECTION = new String[] {
    	FriendsDb.KEY_ROWID,
    	FriendsDb.KEY_FP,
    	FriendsDb.KEY_NAME
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
    
    /** A callback method invoked by the loader when initLoader() is called */
    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    	Log.v(TAG, "onCreateLoader called");
        Uri uri = Friend.CONTENT_URI;
        return new CursorLoader(this, uri, FRIENDS_SUMMARY_PROJECTION, null, null, null);
    }
 
    /** A callback method, invoked after the requested content provider returned all the data */
    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
        mAdapter.swapCursor(arg1);
    }
 
    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        mAdapter.swapCursor(null);
    }


}