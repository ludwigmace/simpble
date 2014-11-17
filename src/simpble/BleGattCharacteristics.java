package simpble;

import java.util.UUID;

import android.bluetooth.BluetoothGattCharacteristic;

public class BleGattCharacteristics extends BluetoothGattCharacteristic {

	public static final String GATT_NOTIFY = "notify";
	public static final String GATT_READ = "read";
	public static final String GATT_WRITE = "write";
	public static final String GATT_INDICATE = "indicate";
	public static final String GATT_READWRITE = "readwrite";
	
	public BlePeripheralHandler charHandler;
	
	public BleGattCharacteristics(UUID uuid, int properties, int permissions) {
		super(uuid, properties, permissions);
	}
	
	public BleGattCharacteristics(UUID uuid, int properties, int permissions, BlePeripheralHandler cHandler) {
		this(uuid, properties, permissions);
		charHandler = cHandler;
	}
	
	
	
}
