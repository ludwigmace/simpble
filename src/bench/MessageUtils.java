package bench;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import android.content.Context;

import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import example.handshake.R;

public class MessageUtils {
	/**
	 * Generates a message of MessageSize bytes using Lorem text
	 * @param MessageSize
	 * @return Byte array of Lorem text of indicated size
	 */
	
	Context ctx;
	
	public MessageUtils(Context context) {
		ctx = context;
	}
	
	public byte[] GenerateMessage(int MessageSize) {
		// get the lorem text from file
		byte[] bytesLorem = null;
		byte[] bytesMessage = null;
		InputStream is = ctx.getResources().openRawResource(R.raw.lorem);
    			
		int currentMessageLength = 0;
		int maxcount = 0;
		
		while ((currentMessageLength < MessageSize) && maxcount < 1000) {
			maxcount++;
	    	try {
	    		if (currentMessageLength == 0) {
	    			bytesMessage = ByteStreams.toByteArray(is);
	    		}
	    		is.reset();
	    		bytesLorem = ByteStreams.toByteArray(is);
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	
	    	try {
				is.reset();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	bytesMessage = Bytes.concat(bytesMessage, bytesLorem);
	    	
	    	currentMessageLength = bytesMessage.length;
    	
		}
		
		return Arrays.copyOf(bytesMessage, MessageSize);
	}
	
}
