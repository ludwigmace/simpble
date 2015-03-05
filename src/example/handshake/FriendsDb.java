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

    
	private static final String FRIENDS_TABLE = "friends";
	public static final String KEY_F_NAME = "friend_name";
    public static final String KEY_F_FP = "friend_fp";
    public static final String KEY_F_PUK = "friend_puk";
    public static final String KEY_F_ROWID = "_id";

    private static String DBNAME = "friends";
    private static final int DBVERSION = 2;
 

    private static final String MSGS_TABLE = "msgs";
	public static final String KEY_M_FNAME = "friend_name";
    public static final String KEY_M_CONTENT = "msg_content";
    public static final String KEY_M_ROWID = "_id";
    
    private static final String TAG = "FriendsDbAdapter";
    private SQLiteDatabase mDb;

    /**
     * Database creation sql statement
     */
    private static final String FRIENDS_CREATE =
        "create table " + FRIENDS_TABLE + " ("
        + KEY_F_ROWID + " integer primary key autoincrement, "
        + KEY_F_FP + " text not null, "
        + KEY_F_NAME + " text not null, "
        + KEY_F_PUK + " blob null); ";

    private static final String MSGS_CREATE =
    	"create table " + MSGS_TABLE + " ("
    	+ KEY_M_ROWID + " integer primary key autoincrement, "
    	+ KEY_M_CONTENT + " text not null, "
    	+ KEY_M_FNAME + " text not null); ";


	public FriendsDb(Context context) {
		super(context, DBNAME, null, DBVERSION);
	    mDb = getWritableDatabase();
	}
    
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(FRIENDS_CREATE);
        db.execSQL(MSGS_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + FRIENDS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + MSGS_TABLE);
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
    public long createFriend(String name, String fp, byte[] puk) {
        ContentValues initialValues = new ContentValues();
        
        initialValues.put(KEY_F_NAME, name);
        initialValues.put(KEY_F_FP, fp);
        initialValues.put(KEY_F_PUK, puk);
        
        return mDb.insert(FRIENDS_TABLE, null, initialValues);
       
    }
    
    public long queueMsg(String friend_name, String message_content) {
        ContentValues initialValues = new ContentValues();
        
        initialValues.put(KEY_M_FNAME, friend_name);
        initialValues.put(KEY_M_CONTENT, message_content);
        
        return mDb.insert(MSGS_TABLE, null, initialValues);
       
    }
    
    public Cursor fetchAllMsgs() {

        return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME,
                KEY_M_CONTENT}, null, null, null, null, null);
    }

    /**
     * Delete the friend with the given rowId
     * 
     * @param rowId id of friend to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteFriend(long rowId) {

        return mDb.delete(FRIENDS_TABLE, KEY_F_ROWID + "=" + rowId, null) > 0;
    }

    /**
     * Return a Cursor over the list of all friends in the database
     * 
     * @return Cursor over all friends
     */
    public Cursor fetchAllFriends() {

        return mDb.query(FRIENDS_TABLE, new String[] {KEY_F_ROWID, KEY_F_NAME,
                KEY_F_FP, KEY_F_PUK}, null, null, null, null, null);
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

            mDb.query(true, FRIENDS_TABLE, new String[] {KEY_F_ROWID,
                    KEY_F_NAME, KEY_F_FP, KEY_F_PUK}, KEY_F_ROWID + "=" + rowId, null,
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
    public boolean updateFriend(long rowId, String name, String fp, byte[] puk) {
        ContentValues args = new ContentValues();
        args.put(KEY_F_NAME, name);
        args.put(KEY_F_FP, fp);
        args.put(KEY_F_PUK, puk);

        return mDb.update(FRIENDS_TABLE, args, KEY_F_ROWID + "=" + rowId, null) > 0;
    }
	
}
