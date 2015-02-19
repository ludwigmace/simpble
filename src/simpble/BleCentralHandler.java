package simpble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;

public interface BleCentralHandler {
	
	public void intakeFoundDevices(ArrayList<BluetoothDevice> devices);
	public void connectedServiceGood(String remoteAddress);
	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes);
	public void reportDisconnect();
	
}
