package simpble;

public interface BleStatusCallback {
	
	/**
	 * Callback executed when your peer receives a message 
	 * 
	 * @param remoteAddress Bluetooth address of the remote peer, only useful as an index to identify your peer
	 * @param MessageBytes Raw byte payload of the message that you must now operate on
	 */
	public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes);
	
	/**
	 * If your client is in Peripheral mode, this notifies your application that your Advertising status has changed; ie, are you visible to scanning Centrals or not?
	 * 
	 * @param isAdvertising
	 */
	public void advertisingStatusUpdate(boolean isAdvertising);
	
	/**
	 * Callback executed when a message you have queued has been delivered.  Between the remoteAddress and digest you should be able to uniquely identify your message 
	 * 
	 * @param remoteAddress Bluetooth address (just used as an index, could be anything) of the peer to whom BleMessenger delivered the message
	 * @param payloadDigest Hex digest of the delivered message
	 */
	public void messageDelivered(String remoteAddress, String payloadDigest);
	
	/**
	 * Regardless of whether you were connected as Central or Peripheral, you're now no longer connected to this device.
	 * 
	 * @param remoteAddress Bluetooth address (just used as an index, could be anything) of the now-disconnected peer 
	 */
	public void peerDisconnect(String remoteAddress);
	
	
	/**
	 * You can use this for debugging; for example in the implementation of the callback you can show messages in popups or otherwise 
	 * @param msg
	 */
	public void headsUp(String msg);
	
}
