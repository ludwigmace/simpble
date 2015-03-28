package simpble;

public interface BleStatusCallback {
	
	public void remoteServerAdded(String serverName);
	
	public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes);
	
	public void advertisingStarted();
	
	public void advertisingStopped();
	
	public void headsUp(String msg);
	
	public void headsUp(String msg, String action);
	
	public void messageDelivered(String remoteAddress, String payloadDigest);
	
	public void peerDisconnect(String device);
	
}
