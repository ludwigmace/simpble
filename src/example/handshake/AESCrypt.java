package example.handshake;

import java.security.MessageDigest;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import simpble.ByteUtilities;
import android.R.integer;
import android.util.Base64;
import android.util.Log;

// adapted from http://stackoverflow.com/questions/5928915/wanted-compatible-aes-code-encrypt-decrypt-for-iphone-android-windows-xp
// apparently a good corollary for iOS is: https://github.com/Gurpartap/AESCrypt-ObjC
public class AESCrypt {

private static final String TAG = "AESCrypt";
private final Cipher cipher;
private final SecretKeySpec key;
private AlgorithmParameterSpec spec;


public AESCrypt(byte[] EncryptionKeyRawBytes) throws Exception
{
    // hash password with SHA-256 and crop the output to 128-bit for key
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(EncryptionKeyRawBytes);
    byte[] keyBytes = new byte[32];
    System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);

    cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
    key = new SecretKeySpec(keyBytes, "AES");
    spec = getIV();
    
    
}       

public AlgorithmParameterSpec getIV()
{
    byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, };
    IvParameterSpec ivParameterSpec;
    ivParameterSpec = new IvParameterSpec(iv);

    return ivParameterSpec;
}

public byte[] encrypt(byte[] BytesToEncrypt) throws Exception
{	
	int msglength = BytesToEncrypt.length;
	
	// let's make this thing at least 16
	/*
	if (BytesToEncrypt.length < 16) {
		BytesToEncrypt = Arrays.copyOf(BytesToEncrypt, 16);
	}*/

	if ((msglength % 16) != 0) {
		int pad = 16 - (msglength % 16);
		BytesToEncrypt = Arrays.copyOf(BytesToEncrypt, msglength + pad);
	}
	
	
    cipher.init(Cipher.ENCRYPT_MODE, key, spec);
    byte[] encrypted = cipher.doFinal(BytesToEncrypt);

    Log.v(TAG, "plainmsg:" + new String(BytesToEncrypt));
    Log.v(TAG, "encrypting bytes:" + ByteUtilities.bytesToHex(BytesToEncrypt));
    Log.v(TAG, "using key:" + ByteUtilities.bytesToHex(key.getEncoded()));
    //String encryptedText = new String(Base64.encode(encrypted, Base64.DEFAULT), "UTF-8");

    return encrypted;
}

public byte[] decrypt(byte[] BytesToDecrypt) throws Exception
{
    cipher.init(Cipher.DECRYPT_MODE, key, spec);
    //byte[] bytes = Base64.decode(cryptedText, Base64.DEFAULT);
    byte[] decrypted = cipher.doFinal(BytesToDecrypt);

    return decrypted;
}

}