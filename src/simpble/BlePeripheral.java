package simpble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

public class BlePeripheral {
	
    private static final String TAG = "BLEP";
    
    // all our BLE android objects
    private BluetoothManager btMgr;
    private BluetoothAdapter btAdptr;
    
    // this particular one is only available on advertise-capable devices
    private BluetoothLeAdvertiser btLeAdv;
    
    // global flag for whether or not we're advertising
    private boolean isAdvertising;
      
    private ArrayList<BluetoothGattService> gattServices;
    private List<ParcelUuid> gattServiceIDs;
    
    // the BLE gatt stuff
    private BluetoothGattServer btGattServer;
    private BluetoothDevice btClient;
    
    private Context thisContext;
    
    private BlePeripheralHandler peripheralHandler;
        
	private String serviceBaseUUID;
	int globalCharacteristicCount;
	
	// keep instances of BluetoothGattCharacteristics, able to look them up via UUID
    private Map<UUID, BluetoothGattCharacteristic> uuidToGattCharacteristics = new HashMap<UUID, BluetoothGattCharacteristic>();
    
    // right now this only supports a single central being subscribed to a characteristic, which is fine for BT 4.0 but needs to be expanded for BT 4.1
    private Map<BluetoothGattCharacteristic, BluetoothDevice> mySubscribers = new HashMap<BluetoothGattCharacteristic, BluetoothDevice>();
	
    /**
     * A helper class for dealing with peripheral operations.
     * 
     * @param baseUUID  A base uuid for creating characteristics
     * @param ctx The application's context, necessary for Bluetooth operations
     * @param btAdapter The system's Bluetooth adapter, necessary for Bluetooth operations
     * @param btManager The system's Bluetooth manager, necessary for Bluetooth operations
     * @param myHandler
     */
	BlePeripheral(String baseUUID, Context ctx, BluetoothAdapter btAdapter, BluetoothManager btManager, BlePeripheralHandler myHandler) {

		serviceBaseUUID = baseUUID;
		globalCharacteristicCount = 0;
		
		thisContext = ctx;
		btAdptr = btAdapter;      
		btMgr = btManager;
		
		peripheralHandler = myHandler;
		
		if (btAdptr.isMultipleAdvertisementSupported()) {
			Log.v(TAG, "advertisement is SUPPORTED on this chipset!");
			btLeAdv = btAdptr.getBluetoothLeAdvertiser();
		} else {
			Log.v(TAG, "advertisement NOT supported on this chipset!");
		}
		btLeAdv = btAdptr.getBluetoothLeAdvertiser();
		
        // create a list of BluetoothGattService(s)
        gattServices = new ArrayList<BluetoothGattService>();
        
        // now create a list of Parcel UUIDs, which will be used along with the previous list
        gattServiceIDs = new ArrayList<ParcelUuid>();
        
        // we're not advertising yet
        isAdvertising = false;
   
	}
	
	/**
	 * Close the connection to the central 
	 */
	public void closeConnection(String peerAddress) {
		btGattServer.cancelConnection(btClient);
	}
	
	public boolean updateCharValue(String remoteAddress, UUID charUUID, byte[] value) {
		//TODO: use remoteAddress to identify by lookig up their Bluetoothclient
		// right now this just works for Bluetooth 4.0 and a singly connected central
		
		// get the Characteristic we want to update
		BluetoothGattCharacteristic bgc = uuidToGattCharacteristics.get(charUUID);

		boolean sent = false;
		
		if (bgc == null) {
			Log.v(TAG, "can't find characteristic for " + charUUID.toString());
			return false;
		}
		
		// set the characteristic's value
		bgc.setValue(value);
		Log.v(TAG, "set characteristic value of size:" + String.valueOf(value.length));

		// if these are notify or indicate characteristics, send an update/indication
		if (((bgc.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
				|| ((bgc.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) {

			Log.v(TAG, "characteristic Notify/Indicate");

			if (mySubscribers.get(bgc) != null) {
				Log.v(TAG, "client has subscribed; try to send");
				
				BluetoothDevice btClient = mySubscribers.get(bgc);
				sent = btGattServer.notifyCharacteristicChanged(btClient, bgc, ((bgc.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0));
				
				if (sent) {
					Log.v(TAG, "send SUCCESS");
				} else {
					Log.v(TAG, "send FAILURE");
				}
				
			} else {
				Log.v(TAG, "No Subscribers!");
			}

		} else {
			Log.v(TAG, "characteristic is NOT Notify/Indicate");
		}
		
		// get rid of that reference
		bgc = null;
				
		return sent;
	}
	
	public void advertiseOff() {
		// if we're not advertising, then don't call this
        if(!isAdvertising) {
        	Log.v(TAG, "we weren't advertising in the first place; return");
        	return;
        }
        
        // flip our flag to indicate we're no longer advertising
        isAdvertising = false;
        
        // tell the system's BluetoothLeAdvertiser to stop advertising
        Log.v(TAG, "tell system to stop advertising");
        btLeAdv.stopAdvertising(advertiseCallback);
        
        // clear out the system's BluetoothGattServer's services, and close it
        btGattServer.clearServices();
        btGattServer.close();
        
        // clear our local list of advertised services
        gattServices.clear();
        
        // tell our handler that we've stopped advertising
        peripheralHandler.handleAdvertiseChange(false);
        
	}
	
	
	public void shutErDown() {
        if(btGattServer != null) btGattServer.close();
	}
	
	public boolean advertiseNow() {

		// if we don't have a handle to the system advertiser, then just stop
        if (btLeAdv == null) {
        	Log.v(TAG, "btLeAdv is null!");
        	isAdvertising = false;
        	return false;
        }
		
		// make our Base UUID the service UUID
        UUID serviceUUID = UUID.fromString(serviceBaseUUID);
		
        // make a new service
        BluetoothGattService theService = new BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		
		// loop over all the characteristics and add them to the service
        for (Entry<UUID, BluetoothGattCharacteristic> entry : uuidToGattCharacteristics.entrySet()) {
        	theService.addCharacteristic(entry.getValue());
        	Log.v(TAG, "adding characteristic " + entry.getKey().toString());
        }
        
        // make sure we're all cleared out before we add new stuff
        gattServices.clear();
        gattServiceIDs.clear();
        
    	gattServices.add(theService);
        gattServiceIDs.add(new ParcelUuid(theService.getUuid()));

        //  TODO: if we get this far and advertising is already started, we may want reset everything!
        
    	// but, for right now, if we're already advertising, just quit here
        if(isAdvertising) return true;

        // - calls bluetoothManager.openGattServer(activity, whatever_the_callback_is) as gattServer
        // --- this callback needs to override: onCharacteristicWriteRequest, onCharacteristicReadRequest,
        // ---- onServiceAdded, and onConnectionStateChange
        // then iterates over an ArrayList<BluetoothGattService> and calls .addService for each
        

        // start the gatt server and get a handle to it
        btGattServer = btMgr.openGattServer(thisContext, gattServerCallback);

        // loop over the ArrayList of BluetoothGattService(s) and add each to the gatt server 
        for(int i = 0; i < gattServices.size(); i++) {
        	btGattServer.addService(gattServices.get(i));
        }
        
        // the AdvertiseData and AdvertiseSettings are both required
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        
        // allows us to fit in a 31 byte advertisement; we're not worried about proximity
        dataBuilder.setIncludeTxPowerLevel(false);
        
        // this is the operative call which gives the parceluuid info to our advertiser to link to our gatt server
        // dataBuilder.setServiceUuids(gattServiceIDs); // correspond to the advertisingServices UUIDs as ParcelUUIDs
        
        // API 5.0
        for (ParcelUuid pu: gattServiceIDs) {
        	dataBuilder.addServiceUuid(pu);
        }
                
        // this spells FART, and right now apparently doesn't do anything
        byte[] serviceData = {0x46, 0x41, 0x52, 0x54}; 
        
        UUID tUID = new UUID((long) 0x46, (long) 0x41);
        ParcelUuid serviceDataID = new ParcelUuid(tUID);
        
        // service data, apparently Android asks for it but it's not used
        dataBuilder.addServiceData(serviceDataID, serviceData);
        
        // advertise settings
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        //settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        //settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
        //settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW);
        settingsBuilder.setConnectable(true);
        
        isAdvertising = true;
       
        // start advertising
        try {
        	btLeAdv.startAdvertising(settingsBuilder.build(), dataBuilder.build(), advertiseCallback);
        } catch (Exception ex) {
        	Log.v(TAG, "call to btLeAdv.startAdvertising failed");
        	isAdvertising = false;	
        }
        
        return true;
    
	}
	
	public BluetoothGattCharacteristic getChar(UUID uuid) {
		return uuidToGattCharacteristics.get(uuid);
	}
		
	public UUID addChar(String charType, UUID uuid, BlePeripheralHandler charHandler) {
		Log.v(TAG, "adding chartype:" + charType + " uuid:" + uuid.toString());
		//TODO: convert to simpler intProperties/intPermissions and add subscribe descriptors for appropriate characteristics
		if (charType.equals(BleGattCharacteristic.GATT_NOTIFY)) {
		
			Log.v(TAG, "adding notify characteristic");
	        uuidToGattCharacteristics.put(uuid, new BleGattCharacteristic(
	        		uuid,
	                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
	                BluetoothGattCharacteristic.PERMISSION_READ,
	                charHandler
	        		)
	        );
	        
	        // since this is a Notify, add the descriptor
	        BluetoothGattDescriptor gD = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
	        BluetoothGattCharacteristic bgc = uuidToGattCharacteristics.get(uuid);
	        bgc.addDescriptor(gD);
	        bgc = null;
		}

		if (charType.equals(BleGattCharacteristic.GATT_READ)) {
			
			Log.v(TAG, "adding read characteristic");
	        uuidToGattCharacteristics.put(uuid, new BleGattCharacteristic(
	        		uuid,
	                BluetoothGattCharacteristic.PROPERTY_READ,
	                BluetoothGattCharacteristic.PERMISSION_READ,
	                charHandler
	        		)
	        );
		}

		if (charType.equals(BleGattCharacteristic.GATT_READWRITE)) {
			
			Log.v(TAG, "adding readwrite characteristic");
	        uuidToGattCharacteristics.put(uuid, new BleGattCharacteristic(
	        		uuid,
	                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
	                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ,
	                charHandler
	        		)
	        );
	        
		}
		
		if (charType.equals(BleGattCharacteristic.GATT_WRITE)) {
			
			Log.v(TAG, "adding write characteristic");
	        uuidToGattCharacteristics.put(uuid, new BleGattCharacteristic(
	        		uuid,
	                BluetoothGattCharacteristic.PROPERTY_WRITE,
	                BluetoothGattCharacteristic.PERMISSION_WRITE,
	                charHandler
	        		)
	        );
	        
		}
		
		if (charType.equals(BleGattCharacteristic.GATT_INDICATE)) {
			
			Log.v(TAG, "adding indicate characteristic");
	        uuidToGattCharacteristics.put(uuid, new BleGattCharacteristic(
	        		uuid,
	                BluetoothGattCharacteristic.PROPERTY_INDICATE,
	                BluetoothGattCharacteristic.PERMISSION_READ,
	                charHandler
	        		)
	        );
	        
	        // since this is an Indicate, add the descriptor
	        BluetoothGattDescriptor gD = new BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"), BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
	        BluetoothGattCharacteristic bgc = uuidToGattCharacteristics.get(uuid);
	        bgc.addDescriptor(gD);
	        bgc = null;
		}
		
		return uuid;
	}
	
	
    public BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == 2) {
            	btClient = device;
            }
            Log.v(TAG, "onConnectionStateChange status=" + status + "->" + newState);
            
            peripheralHandler.ConnectionState(device.getAddress(), status, newState);
            
            
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.d(TAG, "onServiceAddedCalled");
        }
        
        @Override
        public void onDescriptorReadRequest (BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
        	
        	Log.d(TAG, "onReadDescriptorCalled");
        	// An application must call sendResponse(BluetoothDevice, int, int, int, byte[]) to complete the request.
        	btGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.getValue());
        	
        }
 
        @Override
        public void onDescriptorWriteRequest (BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        	Log.d(TAG, "onWriteDescriptorCalled");
        	// this is only called when somebody subscribes to indications/notifications
        	
        	String status = "";
        	
        	// if the subscriber just subscribed, mark as such
        	if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
            	mySubscribers.put(descriptor.getCharacteristic(), device);
            	status = "indicate";
            	Log.v(TAG, "enable_indication");
        	} else if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            	mySubscribers.put(descriptor.getCharacteristic(), device);
            	status = "notify";
            	Log.v(TAG, "enable_notification");
        	} else {
            	mySubscribers.remove(descriptor.getCharacteristic());
            	Log.v(TAG, "disable indicate/notify");
        	}
        	
        	// once this goes off, the client should know they're all signed up (or not) for updates
        	btGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        	
        	// find my custom characteristic class . . .
        	BleGattCharacteristic myBGC = (BleGattCharacteristic) uuidToGattCharacteristics.get(descriptor.getCharacteristic().getUuid());
        	
        	// . . . and call the correct handler
        	if (status == "notify" || status == "indicate") {
        		myBGC.charHandler.handleNotifyRequest(device.getAddress(), myBGC.getUuid());
        	}

        }
        
        @Override
        public void onExecuteWrite (BluetoothDevice device, int requestId, boolean execute) {
        	Log.d(TAG, "onExecuteWriteCalled");
        	// An application must call sendResponse(BluetoothDevice, int, int, int, byte[]) to complete the request.
        }
        
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        	// get the characteristic that was affected
            BleGattCharacteristic myBGC = (BleGattCharacteristic) uuidToGattCharacteristics.get(characteristic.getUuid());

            // prep the read characteristic for send
            myBGC.charHandler.prepReadCharacteristic(device.getAddress(), characteristic.getUuid());
            
            if (characteristic.getValue() == null) {
				Log.v(TAG, "can't respond to read request; characteristic value is null");	
			} else {
				Log.v(TAG, "sending message:" + new String(characteristic.getValue()));
			}
            
            // per spec, this has to be called
            btGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

        	Log.v(TAG, "write request incoming, bytes:" + ByteUtilities.bytesToHex(value));
        	
        	characteristic.setValue(value);
        	
        	// get the characteristic that was affected, and call its handler!
            BleGattCharacteristic myBGC = (BleGattCharacteristic) uuidToGattCharacteristics.get(characteristic.getUuid());
            
            // since this is a Write request, use the incomingBytes method for the characteristic we want
            // -- device, requestId, characteristic, preparedwrite, responseneeded, offset, value
            myBGC.charHandler.incomingMissive(device.getAddress(), characteristic.getUuid(), value);
            
        	// An application must call sendResponse(BluetoothDevice, int, int, int, byte[]) to complete the request.        	
        	btGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
    };

    /**
     * AdvertiseCallback, calls BlePeripheralHandler.handleAdvertiseChange when advertising is successful
     * Right now nothing is done with AdvertiseSettings.getMode() or AdvertiseSettings.getTxPowerLevel()
     */
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings aS) {
        	
        	boolean bType = false;
        	/*
        	int iMode = 9;
        	int iPowerLevel = 9;
        	try { iMode = aS.getMode(); } catch (Exception e) { }
        	
        	try { iPowerLevel = aS.getTxPowerLevel(); } catch (Exception e) { }
        	*/
        	
        	try { bType = aS.isConnectable(); } catch (Exception e) { }
        	
        	if (bType) {
        		peripheralHandler.handleAdvertiseChange(isAdvertising);
        	}
        }

        @Override
        public void onStartFailure(int i) {
            peripheralHandler.handleAdvertiseChange(false);
        }

    };

	
}
