package example.handshake;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


// adapted from http://stackoverflow.com/questions/5928915/wanted-compatible-aes-code-encrypt-decrypt-for-iphone-android-windows-xp
// apparently a good corollary for iOS is: https://github.com/Gurpartap/AESCrypt-ObjC
public class AESCrypt {

private static final String TAG = "AESCrypt";
private final Cipher cipher;
private final SecretKeySpec key;
private AlgorithmParameterSpec spec;
private byte[] initVector;
private byte[] rawKeyBytes;

/** Provides some very basic symmetric encryption functionality
 * 
 * @param EncryptionKeyRawBytes A 32 byte AES key you'd like to use for encryption/decryption, and optionally wrap in RSA Public Key encryption
 * @param iV 16 bytes a random initialization vector you'll need to pass to the message recipient in plaintext
 * @throws Exception
 */
public AESCrypt(byte[] EncryptionKeyRawBytes, byte[] iV) throws Exception
{
	
	rawKeyBytes = EncryptionKeyRawBytes;
	
    // hash password with SHA-256 and crop the output to 128-bit for key
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(EncryptionKeyRawBytes);
    byte[] keyBytes = new byte[32];
    System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);

    cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
    key = new SecretKeySpec(keyBytes, "AES");
    initVector = iV;
    spec = getIV();
    
    
}       
/** Takes a provided Initialization Vector and returns as AlgorithmParameterSpec for use in the encrypt and  decrypt functions
 * 
 * @return
 */
private AlgorithmParameterSpec getIV()
{
    IvParameterSpec ivParameterSpec;
    ivParameterSpec = new IvParameterSpec(initVector);

    return ivParameterSpec;
}

/** Encrypts provided bytes using the key and IV passed in AESCrypt constructor, as follows: AES/CBC/PKCS7Padding
 * 
 * @param BytesToEncrypt Byte array of the Message you want to encrypt
 * @return Byte array of the Message encrypted with this spec: 
 * @throws Exception
 */
public byte[] encrypt(byte[] BytesToEncrypt) throws Exception
{	
	int msglength = BytesToEncrypt.length;
	
	// pad to at least 16 bytes
	if ((msglength % 16) != 0) {
		int pad = 16 - (msglength % 16);
		BytesToEncrypt = Arrays.copyOf(BytesToEncrypt, msglength + pad);
	}
	
    cipher.init(Cipher.ENCRYPT_MODE, key, spec);
    byte[] encrypted = cipher.doFinal(BytesToEncrypt);

    return encrypted;
}


/** Decrypts provided bytes using the key and IV passed in AESCrypt constructor, as follows: AES/CBC/PKCS7Padding
 * 
 * @param BytesToDecrypt An encrypted message
 * @return A byte array of the decrypted message
 * @throws Exception
 */
public byte[] decrypt(byte[] BytesToDecrypt) throws Exception
{
    cipher.init(Cipher.DECRYPT_MODE, key, spec);

    byte[] decrypted = cipher.doFinal(BytesToDecrypt);

    return decrypted;
}

/**  Encrypts the key passed in the AESCrypt constructor using the provided public key per the following spec: RSA/ECB/PKCS1Padding 
 * 
 * @param friendPuk The recipient's Public Key in bytes
 * @return An encrypted AES key
 * @throws Exception
 */
public byte[] encryptedSymmetricKey(byte[] friendPuk) throws Exception {
	
	PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(friendPuk));
	Cipher mCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    mCipher.init(Cipher.WRAP_MODE, publicKey);
    
    SecretKey symmkey = new SecretKeySpec(rawKeyBytes, "AES");
    
    byte[] encryptedSK = mCipher.wrap(symmkey);
    
    return encryptedSK;
    
}


}