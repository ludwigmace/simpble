package simpble;

import java.util.UUID;

public interface BleStatusCallback {

	public void messageSent(byte[] MessageHash, BlePeer blePeer);
	
	public void remoteServerAdded(String serverName);
	
	public void foundPeer(BlePeer blePeer);
	
	public void handleReceivedMessage(String recipientFingerprint, String senderFingerprint, byte[] payload, String msgType);

	public void advertisingStarted();
	public void advertisingStopped();
	
	public void headsUp(String msg);
	public void headsUp(String msg, String action);
	
	public void readyToTalk(String remo);
	
}
