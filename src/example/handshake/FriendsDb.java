package example.handshake;

import java.util.ArrayList;

import simpble.ByteUtilities;
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
    private static final int DBVERSION = 5;
 
    private static final String MSGS_TABLE = "msgs";
    
    public static final String KEY_M_ROWID = "_id";
	public static final String KEY_M_FNAME = "addressee";
    public static final String KEY_M_CONTENT = "content";
    public static final String KEY_M_MSGTYPE = "type";
    public static final String KEY_M_RECIP = "recipient";
    public static final String KEY_M_MSGID = "signature";
    
    public static final String CONSTANTS_TABLE = "constants";
    
    public static final String KEY_C_ROWID = "_id";
    public static final String KEY_C_CONSTANT = "constant_name";
    public static final String KEY_C_VALUE = "constant_value";
    
    public static final String CONSTANT_SEND_ID = "send_id";
    public static final String CONSTANT_RECV_ID = "recv_id";
    
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
    
    private static final String CONSTANTS_CREATE =
            "create table " + CONSTANTS_TABLE + " ("
            + KEY_C_ROWID + " integer primary key autoincrement, "
            + KEY_C_CONSTANT + " text not null, "
            + KEY_C_VALUE + " text not null); ";

    private static final String MSGS_CREATE =
    	"create table " + MSGS_TABLE + " ("
    	+ KEY_M_ROWID + " integer primary key autoincrement, "
    	+ KEY_M_CONTENT + " text not null, "
    	+ KEY_M_MSGTYPE + " text not null, "
    	+ KEY_M_RECIP + " text null, "
    	+ KEY_M_MSGID + " text null, "
    	+ KEY_M_FNAME + " text null); ";
    

	public FriendsDb(Context context) {
		super(context, DBNAME, null, DBVERSION);
	    mDb = getWritableDatabase();
	}
    
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(FRIENDS_CREATE);
        db.execSQL(MSGS_CREATE);
        db.execSQL(CONSTANTS_CREATE);
        
        createDefaultConstants(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + FRIENDS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + MSGS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + CONSTANTS_TABLE);
        onCreate(db);
    }

    /**
     * Scaffold constants for what  mode we want to be in, sending, receiving, id-friendly, etc
     */
    public void createDefaultConstants(SQLiteDatabase db) {
    	
        ContentValues initialValues = new ContentValues();
        
        initialValues.put(KEY_C_CONSTANT, CONSTANT_SEND_ID);
        initialValues.put(KEY_C_VALUE, "true");
        db.insert(CONSTANTS_TABLE, null, initialValues);
        
        initialValues.put(KEY_C_CONSTANT, CONSTANT_RECV_ID);
        initialValues.put(KEY_C_VALUE, "true");
        db.insert(CONSTANTS_TABLE, null, initialValues);
        
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
    
    public long queueMsg(String friend_name, String message_content, String message_type, String msg_id) {
        ContentValues initialValues = new ContentValues();
        
        initialValues.put(KEY_M_FNAME, friend_name);
        initialValues.put(KEY_M_CONTENT, message_content);
        initialValues.put(KEY_M_MSGTYPE, message_type);
        initialValues.put(KEY_M_MSGID, msg_id);
        
        return mDb.insert(MSGS_TABLE, null, initialValues);
       
    }

    //storeIncomingMessage(topic_name, "topic", msgSignature, ByteUtilities.bytesToHex(payload));
   
 
    
    public Cursor fetchMsgByType(String msgtype) {
		return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
				KEY_M_MSGTYPE + " = ?", new String[] {msgtype}, null, null, null);
    }
    
    public Cursor fetchMsgById(String msgid) {
		return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
				KEY_M_MSGID + " = ?", new String[] {msgid}, null, null, null);
    }

    public Cursor fetchMsgsForFriend(String friendFP) {
		
		String qry = "SELECT a." + KEY_M_ROWID + ", " + KEY_M_CONTENT + ", " + KEY_M_MSGTYPE + ", " + KEY_M_MSGID + ", " + KEY_F_PUK;
		qry += " FROM " + MSGS_TABLE + " a INNER JOIN " + FRIENDS_TABLE + " b ON ";
		qry += "a." + KEY_M_FNAME + " = b." + KEY_F_NAME + " WHERE b." + KEY_F_FP + " = ?";
		
		Log.v(TAG, "fetchMsgsForFriend: " + qry);
		Log.v(TAG, "friendFP: " + friendFP);
		
		return mDb.rawQuery(qry, new String[]{friendFP});
		
    }
    
    public Cursor fetchAllConstants() {
		return mDb.query(CONSTANTS_TABLE, new String[] {KEY_C_ROWID, KEY_C_CONSTANT, KEY_C_VALUE},
				null, null, null, null, null);
    }
    
    
    public Cursor fetchMsgs() {
		return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
				null, null, null, null, null);
    }
    
    public Cursor fetchUnsentMsgs() {
		return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
				KEY_M_RECIP + " = '' OR " + KEY_M_RECIP + " IS NULL", null, null, null, null);
    }
    
    public Cursor fetchUnsentDirectMsgs() {
		return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
				"(" + KEY_M_RECIP + " = '' OR " + KEY_M_RECIP + " IS NULL) AND " +  KEY_M_MSGTYPE + " <> 'topic'", null, null, null, null);
    }
    
    public Cursor fetchMsgsAbbrev() {
    	Log.v(TAG, "fetchMsgsAbbrev is dun been run");
    	
		return mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_MSGTYPE, KEY_M_FNAME, KEY_M_CONTENT},
				null, null, null, null, null);
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
    
    public Cursor fetchFriend(String name) throws SQLException {

        Cursor mCursor =

            mDb.query(true, FRIENDS_TABLE, new String[] {KEY_F_ROWID,
                    KEY_F_NAME, KEY_F_FP, KEY_F_PUK}, KEY_F_NAME + "='" + name + "'", null,
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
    
    // the message id from the database OBVIOUSLY doesn't match the message id for a message session
    public boolean updateMsgSent(String signature, String fp) {
        ContentValues args = new ContentValues();
        args.put(KEY_M_RECIP, fp);
        Log.v(TAG, "update msg " + signature + " w/ fp " + fp);
        return mDb.update(MSGS_TABLE, args, KEY_M_MSGID + " = ?", new String[] {signature}) > 0;
        
    }
    
    // the message id from the database OBVIOUSLY doesn't match the message id for a message session
    public boolean updateMsgMarkUnsent(long id) {
        ContentValues args = new ContentValues();
        args.put(KEY_M_RECIP, "");
        String criteria = KEY_M_ROWID + " = " + String.valueOf(id);
        
        return mDb.update(MSGS_TABLE, args, criteria, null) > 0;
    }

    
    public boolean updateToggleConstantByRow(long id) {
        
    	boolean success = false;
    	
    	Cursor c = mDb.query(CONSTANTS_TABLE, new String[] {KEY_C_VALUE}, KEY_C_ROWID + " = " + id, null, null, null, null);
    	
    	c.moveToFirst();
    	
    	String val = c.getString(0);
    	String newval = "";
    	
    	if (val.equalsIgnoreCase("true")) {
    		newval = "false";
    	} else if (val.equalsIgnoreCase("false")) {
    		newval = "true";
    	}
    	
    	if (newval.length() > 0) {
    	
    		ContentValues args = new ContentValues();
    		args.put(KEY_C_VALUE, newval);
        
    		String criteria = KEY_C_ROWID + " = " + String.valueOf(id);
        
    		success = mDb.update(CONSTANTS_TABLE, args, criteria, null) > 0;
    	}
    	
    	return success;
    }
    
    
    
    
    public String getTopicForMsg(long id) {
    	String result = "";
    	
    	Cursor c = mDb.query(MSGS_TABLE, new String[] {KEY_M_FNAME},
				KEY_M_ROWID + " = " + String.valueOf(id), null, null, null, null);
    	
    	if (c.getCount() > 0) {
    		c.moveToFirst();
    		
    		result = c.getString(0);
    	}
    	
    	
    	
    	return result;
    	
    }
    
    public String getConstant(String constant_name) {
    	String result = "";
    	
    	Cursor c = mDb.query(CONSTANTS_TABLE, new String[] {KEY_C_VALUE}, KEY_C_CONSTANT + "='" + constant_name + "'", null, null, null, null);
    	
    	c.moveToFirst();
    	
    	result = c.getString(0);
    	
    	return result;
    	
    }
    
    public boolean setConstant(String constant_name, String constant_value) {

        ContentValues args = new ContentValues();
        args.put(KEY_C_VALUE, constant_value);

        return mDb.update(CONSTANTS_TABLE, args, KEY_C_CONSTANT + "= ?", new String[] {constant_name}) > 0;
    	

    }
    
    
    
    public ArrayList<String> topicsSentToRecipient(String recipient_fp) {
    	
    	ArrayList<String> topics = new ArrayList<String>();
        Cursor mCursor =
        	mDb.query(true, MSGS_TABLE, new String[] {KEY_M_FNAME}, KEY_M_RECIP + "='" + recipient_fp + "'", null, null, null, null, null);
        
            if (mCursor != null) {
            	while (mCursor.moveToNext()) {
            		topics.add(mCursor.getString(0));
            	}
            }
            
            return topics;
    }
    
    public Cursor topicsNotSentToRecipient(String recipient_fp) {
    	
    	// if anything is off-limits, exclude
    	ArrayList<String> alreadySentTopics = topicsSentToRecipient(recipient_fp);
    	String topicList = "";
    	if (alreadySentTopics.size() > 0) {

    		for (String s: alreadySentTopics) {
    			topicList = topicList + "'" + s + "', ";
    		}
        
    		// remove trailing space and comma
    		topicList = topicList.substring(0, topicList.length()-2);
    	}
    	
    	// set up criteria for our topix query
    	String crit = "";
    	if (topicList.length() > 0) {
    		crit = " AND " + KEY_M_FNAME + " NOT IN (" + topicList + ")";
    	}
    	
    				//KEY_M_RECIP + " = '' OR " + KEY_M_RECIP + " IS NULL", null, null, null, null);
    	Cursor mCursor =
        	mDb.query(true, MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
        			 "(" + KEY_M_RECIP + " = '' OR " + KEY_M_RECIP + " IS NULL) AND " + KEY_M_MSGTYPE + "='topic'" + crit, 
        			null, null, null, null, null);
        
            if (mCursor != null) {
                mCursor.moveToFirst();
            }
            
            return mCursor;
    }
    
    /**
     * Given a topic, return an ArrayList<String> of all the shares that make this up
     * 
     * @param topic_name
     * @return
     */
    public ArrayList<String> getMsgSharesForTopic(String topic_name) {
    	
    	ArrayList<String> shares = new ArrayList<String>();
    	
    	Cursor c = mDb.query(MSGS_TABLE, new String[] {KEY_M_ROWID, KEY_M_FNAME, KEY_M_CONTENT, KEY_M_MSGTYPE, KEY_M_MSGID},
				KEY_M_MSGTYPE + " = 'topic' AND " + KEY_M_FNAME + " = ?", new String[] {topic_name}, null, null, null);

    	if (c != null) {
    		while (c.moveToNext()) {
    			shares.add(c.getString(c.getColumnIndex(FriendsDb.KEY_M_CONTENT)));
    		}
    	}
    	
    	return shares;
    	
    }
    
    public ArrayList<String> recipientsForTopic(String topic_name) {
    	
    	ArrayList<String> recipients = new ArrayList<String>();
        Cursor mCursor =
        	mDb.query(true, MSGS_TABLE, new String[] {KEY_M_RECIP}, KEY_M_FNAME + "='" + topic_name + "'", null, null, null, null, null);
        
            if (mCursor != null) {
             
            	while (mCursor.moveToNext()) {
            		recipients.add(mCursor.getString(0));
            	}
            }
            
            return recipients;
    }
	
}
