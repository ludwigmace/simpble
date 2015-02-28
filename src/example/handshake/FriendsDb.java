package example.handshake;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

// re-using the database stuff from Google's Notes sample application
public class FriendsDb extends SQLiteOpenHelper {


	public static final String KEY_NAME = "friend_name";
    public static final String KEY_FP = "friend_fp";
    public static final String KEY_ROWID = "_id";

    private static String DBNAME = "friends";
    private static final int DBVERSION = 2;
 
    private static final String TAG = "FriendsDbAdapter";
    private SQLiteDatabase mDb;

    /**
     * Database creation sql statement
     */
    private static final String DATABASE_CREATE =
        "create table friends (_id integer primary key autoincrement, "
        + "friend_fp text not null, friend_name text not null);";

    private static final String DATABASE_TABLE = "friends";

	public FriendsDb(Context context) {
		super(context, DBNAME, null, DBVERSION);
	    mDb = getWritableDatabase();
	}
    
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS friends");
        onCreate(db);
    }

    /**
     * Create a new friend using the name and fp provided. If the friend is
     * successfully created return the new rowId for that friend, otherwise return
     * a -1 to indicate failure.
     * 
     * @param name the name of the friend
     * @param fp the fingerprint of the friend
     * @return rowId or -1 if failed
     */
    public long createFriend(String name, String fp) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_NAME, name);
        initialValues.put(KEY_FP, fp);

        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }

    /**
     * Delete the friend with the given rowId
     * 
     * @param rowId id of friend to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteFriend(long rowId) {

        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
    }

    /**
     * Return a Cursor over the list of all friends in the database
     * 
     * @return Cursor over all friends
     */
    public Cursor fetchAllFriends() {

        return mDb.query(DATABASE_TABLE, new String[] {KEY_ROWID, KEY_NAME,
                KEY_FP}, null, null, null, null, null);
    }

    /**
     * Return a Cursor positioned at the friend that matches the given rowId
     * 
     * @param rowId id of friend to retrieve
     * @return Cursor positioned to matching friend, if found
     * @throws SQLException if note could not be found/retrieved
     */
    public Cursor fetchFriend(long rowId) throws SQLException {

        Cursor mCursor =

            mDb.query(true, DATABASE_TABLE, new String[] {KEY_ROWID,
                    KEY_NAME, KEY_FP}, KEY_ROWID + "=" + rowId, null,
                    null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    /**
     * Update the friend using the details provided. The friend to be updated is
     * specified using the rowId, and it is altered to use the name and fp
     * values passed in
     * 
     * @param rowId id of friend to update
     * @param title value to set friend's name to
     * @param body value to set friend's fp to
     * @return true if the friend was successfully updated, false otherwise
     */
    public boolean updateFriend(long rowId, String name, String fp) {
        ContentValues args = new ContentValues();
        args.put(KEY_NAME, name);
        args.put(KEY_FP, fp);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }
	
}
