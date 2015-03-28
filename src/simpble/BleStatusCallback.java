package simpble;

public interface BleStatusCallback {
	
	/**
	 * Callback executed when your peer receives a message 
	 * 
	 * @param remoteAddress Bluetooth address of the remote peer, only useful as an index to identify your peer
	 * @param MessageBytes Raw byte payload of the message that you must now operate on
	 */
	public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes);
	
	
	public void advertisingStarted();
	
	public void advertisingStopped();
	
	public void headsUp(String msg);
	
	public void messageDelivered(String remoteAddress, String payloadDigest);
	
	public void peerDisconnect(String device);
	
}
