package example.handshake;

import simpble.ByteUtilities;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ShowMessagesActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final String TAG = "ShowMessagesActivity";
    private static final int ACTIVITY_CREATE=0;
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
                new String[] { FriendsDb.KEY_M_ROWID, FriendsDb.KEY_M_MSGTYPE, FriendsDb.KEY_M_FNAME, FriendsDb.KEY_M_CONTENT},
                new int[] { R.id.msg_rowid, R.id.msg_msgtype, R.id.msg_msgtarget, R.id.msg_msgcontent }, 0);
        
        mListView = (ListView) findViewById(R.id.msgslistview);
        
        
        mListView.setAdapter(mAdapter);
        
        mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				
				// http://stackoverflow.com/questions/4412449/how-to-highlight-listview-items
	            for(int a = 0; a < parent.getChildCount(); a++)
	            {
	                parent.getChildAt(a).setBackgroundColor(Color.TRANSPARENT);
	            }

	            view.setBackgroundColor(Color.LTGRAY);
	            
	            current_msg = id;
				
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
    	FriendsDb.KEY_M_FNAME,
    	FriendsDb.KEY_M_CONTENT
    };

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.msgs_menu, menu);
		return true;
	}
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
		int id = item.getItemId();
		if (id == R.id.action_mark_unsent) {
			FriendsDb mDbHelper;
			mDbHelper = new FriendsDb(getApplicationContext());
			
			boolean success = mDbHelper.updateMsgMarkUnsent(current_msg);
			
			if (success) {
				showMessage("msg recipient cleared out");
			} else {
				showMessage("nothing happened");
			}
			
		}
		
		if (id == R.id.action_secret_combine) {
			// pass in the current_msg id to pull the topic; then the topic to get all the shares to rebuild and display
		}	
    
    	
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