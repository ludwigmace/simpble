package example.handshake;

import java.util.ArrayList;

import secretshare.ShamirCombiner;
import simpble.ByteUtilities;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ConstantsActivity extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {

	private static final String TAG = "ShowMessagesActivity";

	long current_constant;
	SimpleCursorAdapter mAdapter;
    ListView mListView;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.constants_list);
       
        current_constant = 0;
        
        mAdapter = new SimpleCursorAdapter(getBaseContext(),
                R.layout.listview_constant_layout,
                null,
                new String[] { FriendsDb.KEY_C_ROWID, FriendsDb.KEY_C_CONSTANT, FriendsDb.KEY_C_VALUE},
                new int[] { R.id.constant_id, R.id.constant_name, R.id.constant_value}, 0);
        
        mListView = (ListView) findViewById(R.id.constantslistview);
        
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
	            
	            current_constant = id;
				
			}
        
        	}); 
   
        getLoaderManager().initLoader(0, null, this);

    }
    
    
    // These are the Contacts rows that we will retrieve.
    static final String[] CONSTANTS_SUMMARY_PROJECTION = new String[] {
    	FriendsDb.KEY_C_CONSTANT,
    	FriendsDb.KEY_C_VALUE
    };

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.constants_menu, menu);
		return true;
	}
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
		int id = item.getItemId();
		
		if (id == R.id.action_mark_toggle) {
			FriendsDb mDbHelper;
			mDbHelper = new FriendsDb(getApplicationContext());
			
			boolean success = mDbHelper.updateToggleConstantByRow(current_constant);
			
			if (success) {
				Toast.makeText(this, "updated", Toast.LENGTH_SHORT).show();
				getLoaderManager().restartLoader(0, null, this);
			}
			
			mDbHelper.close();
			
		}
    	
        return super.onOptionsItemSelected(item);
    }
    
    /** A callback method invoked by the loader when initLoader() is called */
    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    	Log.v(TAG, "onCreateLoader called");
        Uri uri = Constant.CONTENT_URI;
        return new CursorLoader(this, uri, CONSTANTS_SUMMARY_PROJECTION, null, null, null);
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