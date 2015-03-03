package example.handshake;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class FriendsActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final String TAG = "FriendsActivity";
	SimpleCursorAdapter mAdapter;
    ListView mListView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.friends_list);
       
        mAdapter = new SimpleCursorAdapter(getBaseContext(),
                R.layout.listview_item_layout,
                null,
                new String[] { FriendsDb.KEY_ROWID, FriendsDb.KEY_NAME, FriendsDb.KEY_FP},
                new int[] { R.id.rowid, R.id.name, R.id.fp }, 0);
        
        mListView = (ListView) findViewById(R.id.friendslistview);
        mListView.setAdapter(mAdapter);
        
        getLoaderManager().initLoader(0, null, this);

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