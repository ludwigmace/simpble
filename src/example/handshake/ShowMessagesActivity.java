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

public class ShowMessagesActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final String TAG = "ShowMessagesActivity";
	long current_msg;
	SimpleCursorAdapter mAdapter;
    ListView mListView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.msgs_list);
       
        current_msg = 0;
        
        mAdapter = new SimpleCursorAdapter(getBaseContext(),
                R.layout.listview_msg_layout,
                null,
                new String[] { FriendsDb.KEY_M_ROWID, FriendsDb.KEY_M_MSGTYPE, FriendsDb.KEY_M_CONTENT},
                new int[] { R.id.msg_rowid, R.id.msg_msgtype, R.id.msg_content }, 0);
        
        mListView = (ListView) findViewById(R.id.msgslistview);
        
        
        mListView.setAdapter(mAdapter);
        
        mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				FriendsDb mDbHelper;
				mDbHelper = new FriendsDb(getApplicationContext());
				
				boolean success = mDbHelper.updateMsgMarkUnsent(id);
				
				if (success) {
					showMessage("msg recipient cleared out");
				} else {
					showMessage("nothing happened");
				}
				
			}
        
        	}); 
   
        getLoaderManager().initLoader(0, null, this);

    }
    
    private void showMessage(String msg) {
    	Toast.makeText(ShowMessagesActivity.this, msg, Toast.LENGTH_SHORT).show();
    }
    
    
    
    // These are the Contacts rows that we will retrieve.
    static final String[] MSGS_SUMMARY_PROJECTION = new String[] {
    	FriendsDb.KEY_M_ROWID,
    	FriendsDb.KEY_M_MSGTYPE,
    	FriendsDb.KEY_M_CONTENT
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
    
    /** A callback method invoked by the loader when initLoader() is called */
    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    	Log.v(TAG, "onCreateLoader called");
        Uri uri = Message.CONTENT_URI;
        return new CursorLoader(this, uri, MSGS_SUMMARY_PROJECTION, null, null, null);
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