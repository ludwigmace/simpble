package simpble;

import java.util.UUID;

public interface BleStatusCallback {
	
	public void remoteServerAdded(String serverName);
	
	public void handleReceivedMessage(String remoteAddress, byte[] MessageBytes);
	

	public void peerNotification(String peerIndex, String notification);
	
	public void advertisingStarted();
	public void advertisingStopped();
	
	public void headsUp(String msg);
	public void headsUp(String msg, String action);
	
	public void readyToTalk(String remo);
	
}
