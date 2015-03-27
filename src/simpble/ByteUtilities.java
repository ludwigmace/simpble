package simpble;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ByteUtilities {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    
    public static byte[] hexToBytes(String hex) {
    	byte[] bytes = new byte[hex.length() / 2];
    	
    	for (int i = 0; i < bytes.length; i++) {
    		bytes[i] = (byte) Integer.parseInt(hex.substring(2*i, 2*i+2),16);
    		
    	}
    	
    	return bytes;
    }
    
    /**
     * Given a primitive int datatype (0-2,147,483,647), return the corresponding byte array
     * 
     * @param i Primitive integer between 0 and 2147483647
     * @return byte array (max 4 bytes)
     */
    public static byte[] intToByte(int i) {
    	
    	if (i > 0 && i <= 255) {
    		return new byte[] {(byte) i};
    	} else if (i > 255 && i <= 65535) {
    		return new byte[] {(byte) (i >> 8), (byte) i};
    	} else if (i > 65535 && i <= 16777215) {
    		return new byte[] {(byte) (i >> 16), (byte) (i >> 8), (byte) i};
    	} else if (i > 65535 && i <= 16777215) {
    		return new byte[] {(byte) (i >> 16), (byte) (i >> 8), (byte) i};
    	} else if (i > 16777215 && i <= 2147483647) { // max for primitive int
    		return new byte[] {(byte) (i >> 24), (byte) (i >> 16), (byte) (i >> 8), (byte) i};
    	} else {
    		return null;
    	}
    }

    
    public static byte[] trimmedBytes(byte[] bytes) {
    	
    	int i = bytes.length - 1;
    	while (i >= 0 && bytes[i] == 0) {
    		--i;
    	}
    	
    	return Arrays.copyOf(bytes, i+1);
    	
    	
    }
    
    public static byte[] digestAsBytes(byte[] Payload) {
    	
        // get a digest for the message, to define it
        MessageDigest md = null;
        
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
        
        byte[] digestAsBytes = md.digest(Payload);
        
        return digestAsBytes;
    	
    }
    
    public static String digestAsHex(byte[] digestAsBytes) {
    	return bytesToHex(digestAsBytes(digestAsBytes));
    }

    public static String digestAsHex(String digestAsString) {
    	byte[] digestAsBytes = digestAsString.getBytes();
    	
    	return bytesToHex(digestAsBytes(digestAsBytes));
    }
	
}
