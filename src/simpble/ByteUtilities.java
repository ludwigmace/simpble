package simpble;

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
    
    public static byte[] trimmedBytes(byte[] bytes) {
    	
    	int i = bytes.length - 1;
    	while (i >= 0 && bytes[i] == 0) {
    		--i;
    	}
    	
    	return Arrays.copyOf(bytes, i+1);
    	
    	
    }
	
}
