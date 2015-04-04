package simpble;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;

interface BleCentralHandler {
	
	/**
	 * Executed after BleCentral is done scanning and has found some devices
	 * @param devices
	 */
	public void intakeFoundDevices(ArrayList<BluetoothDevice> devices);
	public void connectedServiceGood(String remoteAddress);
	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes);
	public void reportDisconnect(String remoteAddress);
	
	public void subscribeSuccess(String remoteAddress, UUID remoteCharUUID);
	
}
