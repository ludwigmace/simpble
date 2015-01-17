package simpble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

public class BleCentral {
	
	private static final String TAG = "BLECC";
	
	// we need the system context to perform Gatt operations
	private Context ctx;

	// acts as a base from which to calculate characteristic uuid's
    private String strSvcUuidBase;
  
	// scanning happens asynchronously, so get a link to a Handler
    private Handler mHandler;
    
    // used to determin if we're currently scanning
    private boolean mScanning;
    
    // keeps a list of devices we found during the scan
    private ArrayList<BluetoothDevice> foundDevices;
    
    // a hook to our bluetooth adapter
    private BluetoothAdapter centralBTA;
    
    // allows external actors to handle events generated by our gatt operations
    private BleCentralHandler gattClientHandler;
    
    // our service definition
    private List<BleCharacteristic> serviceDef;
    
    // bluetooth gatt functionality pointed to a particular remote server
    private Map<String, BluetoothGatt> gattS;
    
    // scan for 2 1/2 seconds at a time
    private long scanDuration;

    // scanning object
    private BluetoothLeScanner bleScanner;
    
    // scan settings
    private ScanSettings bleScanSettings;
    
    // scan filter
    private List<ScanFilter> bleScanFilter;
    
    /**
     * A helper class for dealing with Bluetooth Central operations
     * @param btA system bluetooth adapter
     * @param ctx system context
     * @param myHandler callback to handle events generated by this class
     * @param serviceUuidBase base uuid for calculating other characteristic uuid's
     * @param scanMS milliseconds to scan when given the scan command
     */
    BleCentral(BluetoothAdapter btA, Context ctx, BleCentralHandler myHandler, String serviceUuidBase, long scanMS) {
    	
    	scanDuration = scanMS;
    	
    	centralBTA = btA;

    	bleScanner = centralBTA.getBluetoothLeScanner();
    	
    	boolean validUuidBase = false;
    	
    	try {
    		UUID u = UUID.fromString(serviceUuidBase);
    		validUuidBase = true;
    	} catch (Exception e) {
    		validUuidBase = false;
    	}
    	
    	if (validUuidBase) {
    		strSvcUuidBase = serviceUuidBase;    		
    	} else {
    		strSvcUuidBase = "73A20000-2C47-11E4-8C21-0800200C9A66";
    	}
    	
    	this.ctx = ctx;
    
    	// to be used for scanning for LE devices
        mHandler = new Handler();
        
        foundDevices = new ArrayList<BluetoothDevice>(); 
        
        mScanning = false;
        
        gattClientHandler = myHandler;
        
        serviceDef = new ArrayList<BleCharacteristic>();
        
        gattS = new HashMap<String, BluetoothGatt>();
        
        ScanSettings.Builder sb = new ScanSettings.Builder();
        sb.setReportDelay(0);
        sb.setScanMode(1);         //sb.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        bleScanSettings = sb.build();
        
        ScanFilter.Builder sf = new ScanFilter.Builder();
        sf.setServiceUuid(ParcelUuid.fromString(strSvcUuidBase));
        bleScanFilter = new ArrayList<ScanFilter>();
        bleScanFilter.add(sf.build());
        
    }

    public void initConnect(BluetoothDevice b) {
    	b.connectGatt(ctx, false, mGattCallback);
    }
    
    public void connectAddress(String btAddress){
    	BluetoothDevice b = centralBTA.getRemoteDevice(btAddress);
    	b.connectGatt(ctx, false, mGattCallback);
    }
    
    public void disconnectAddress(String btAddress) {
    	// get the gatt connection to the particular server and disconnect
    	try {
    		gattS.get(btAddress).disconnect();
    	} catch (Exception e) {
    		Log.e(TAG, "error disconnecting");
    		Log.e(TAG, e.getMessage());
    	}

    }
    
    
    public void setRequiredServiceDef(List<BleCharacteristic> bleChars) {
    	
    	serviceDef = bleChars;
    	
    }
    
    public boolean submitSubscription(String remoteAddr, UUID uuidChar) {
    	boolean result = false;
    	
    	BluetoothGatt gatt = gattS.get(remoteAddr);
    	BluetoothGattCharacteristic indicifyChar = gatt.getService(UUID.fromString(strSvcUuidBase)).getCharacteristic(uuidChar);
   	
    	if (indicifyChar != null) {
        	int cProps = indicifyChar.getProperties();
        	
	   	     if ((cProps & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
		    	 Log.v(TAG, "sub for notifications from " + indicifyChar.getUuid().toString().substring(0,8));
			
				// enable notifications for this guy
		    	gatt.setCharacteristicNotification(indicifyChar, true);
				
				// tell the other guy that we want characteristics enabled
				BluetoothGattDescriptor descriptor = indicifyChar.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
				
				// if it's a notification value, subscribe by setting the descriptor to ENABLE_NOTIFICATION_VALUE
				if ((cProps & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
					descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				}

				// if it's an INDICATION value, subscribe by setting the descriptor to ENABLE_INDICATION_VALUE				
				if ((cProps & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
					descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
				}
				
				gatt.writeDescriptor(descriptor);
				
				result = true;
			
			}
    	} else {
    		Log.v(TAG, "can't pull characteristic so i can't sub it");
    	}
    	    	
		return result;
    }
    

    
    public boolean isScanning() {
    	return mScanning;
    }
    
    public boolean submitCharacteristicReadRequest(String remoteAddr, UUID uuidChar) {
    	
    	boolean charFound = false;
    	
    	BluetoothGatt gatt = gattS.get(remoteAddr);
    	BluetoothGattCharacteristic readChar = gatt.getService(UUID.fromString(strSvcUuidBase)).getCharacteristic(uuidChar);

    	if (readChar != null) {
    		Log.v(TAG, "issuing read request:" + readChar.getUuid().toString());
    		gatt.readCharacteristic(readChar);
    		charFound = true;
    	}
    	
    	return charFound;
    	
    }
    
    public boolean submitCharacteristicWriteRequest(String remoteAddr, UUID uuidChar, final byte[] val) {
		
    	boolean charWrote = false;

    	BluetoothGatt gatt = gattS.get(remoteAddr);
    	BluetoothGattCharacteristic writeChar = gatt.getService(UUID.fromString(strSvcUuidBase)).getCharacteristic(uuidChar);
    	
    	writeChar.setValue(val);
    	writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    		
    	try {
    		gatt.writeCharacteristic(writeChar);
    		charWrote = true;
    	} catch (Exception e) {
    		Log.v(TAG, "cannot write char ");
    		Log.v(TAG, e.getMessage());
    	}
		
		return charWrote;
    }
    
 // when a device is found from the ScanLeDevice method, call this
    private ScanCallback scanCallback = new ScanCallback() {

    	public void onBatchScanResults(List<ScanResult> results) {
    		
    	}
    	
    	public void onScanFailed (int errorCode) {
    		
    	}
    	
    	public void onScanResult (int callbackType, ScanResult result) {
    		
    		// if we haven't already gotten the device, then add it to our list of found devices
			if (!foundDevices.contains(result.getDevice())) {
				foundDevices.add(result.getDevice());
	    	}
    	}
    	
    };
    
    public ArrayList<BluetoothDevice> getAdvertisers() {
    	return foundDevices;
    }
    
    public void scanLeDevice(final boolean enable) {
    	final long SCAN_PERIOD = scanDuration;
    	
        if (enable) {

        	// call STOP after SCAN_PERIOD ms, which will spawn a thread to stop the scan
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bleScanner.stopScan(scanCallback);

        			gattClientHandler.intakeFoundDevices(foundDevices);
        			Log.v(TAG, "scan stopped, found " + String.valueOf(foundDevices.size()) + " devices");
                }
            }, SCAN_PERIOD);

            // start scanning!
            mScanning = true;
            
            Log.v(TAG, "scan started");

            //bleScanner.startScan(scanCallback);
            bleScanner.startScan(bleScanFilter, bleScanSettings, scanCallback);

        } else {
        	
        	// the "enable" variable passed wa False, so turn scanning off
            mScanning = false;
            bleScanner.stopScan(scanCallback);
            
        }

    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
    	@Override
    	// Characteristic notification
    	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
    		gattClientHandler.incomingMissive(gatt.getDevice().getAddress(), characteristic.getUuid(), characteristic.getValue());
    		
    	}
    	
    	public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    		//defaultHandler.DoStuff(gatt.getDevice().getAddress() + " - " + String.valueOf(rssi));
    	}
    	
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                		
                Log.v(TAG, "Connected to GATT server " + gatt.getDevice().getAddress());

                // save a reference to this gatt server!
                gattS.put(gatt.getDevice().getAddress(), gatt);
                gatt.discoverServices();
                //Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                // since we're disconnected, remove this guy
                gattS.remove(gatt.getDevice().getAddress());
                
                gattClientHandler.reportDisconnect();
                Log.i(TAG, "Disconnected from GATT server " + gatt.getDevice().getAddress());
                
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	// the passed in characteristic's value is always 00, so it must be a different characteristic
        	Log.v(TAG, "write submitted, BtGattChar.getValue=" + ByteUtilities.bytesToHex(characteristic.getValue()));
        	
        	
        	if (status == BluetoothGatt.GATT_SUCCESS) {
        		Log.v(TAG, "successful!");
        		//gattClientHandler.handleWriteResult(gatt, characteristic, status);
        	} else {
        		Log.v(TAG, "not successful!");
        	}
        	
           
          
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	Log.v("SERVICES", "services discovered on " + gatt.getDevice().getAddress());
            	
            	// we're pulling a specific service
            	BluetoothGattService s = gatt.getService(UUID.fromString(strSvcUuidBase));

            	boolean bServiceGood = false;
            	
            	// if we've found a service
            	if (s != null) {
            		// we need to determine which phase we're in, or what function we want, to decide what to do here...
            		// should we decide what to do here or pass that decision on somewhere else?
            		
            		bServiceGood = true;
            		
            		// check to make sure every characteristic we want is advertised in this service
                	for (BleCharacteristic b: serviceDef) {
                		if (s.getCharacteristic(b.uuid) == null) {
                			bServiceGood = false;
                			Log.v(TAG, "characteristic " + b.uuid.toString() + " not found");
                			break;
                		}
                	}
            		           
            	} else {
            		Log.v(TAG, "can't find service " + strSvcUuidBase);
            	}

            	// if this service is good, we can proceed to parlay with our remote party
            	// OR, you can actually go ahead and issue your READ for the id characteristic
        		if (bServiceGood) {
        			Log.v(TAG, "service definition found; stay connected");
        			//gattClientHandler.getFoundCharacteristics(gatt, s.getCharacteristics());
        			gattClientHandler.parlayWithRemote(gatt.getDevice().getAddress());
        			// initiate identification phase, and then data transfer phase!
        			
        		} else {
        			Log.v(TAG, "service definition not found, disconnect");
        			gatt.disconnect();
        		}

		        
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	
            	//gattClientHandler.readCharacteristicReturned(gatt, characteristic, characteristic.getValue(), status);
            	//gattClientHandler.getReadCharacteristic(gatt, characteristic, characteristic.getValue(), status);
            	gattClientHandler.incomingMissive(gatt.getDevice().getAddress(), characteristic.getUuid(), characteristic.getValue());
            	
                Log.v(TAG, "+read " + characteristic.getUuid().toString() + ": " + new String((characteristic.getValue())));
            } else {
            	Log.v(TAG, "-fail read " + characteristic.getUuid().toString());
            }
        }
    };
    
}
