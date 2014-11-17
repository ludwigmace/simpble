package simpble;

import java.util.UUID;

public class BleCharacteristic {

	
	BleCharacteristic(String CharacteristicName, UUID CharacteristicUUID, String CharacteristicType) {
		name = CharacteristicName;
		uuid = CharacteristicUUID;
		type = CharacteristicType;
	}
	
	public String name;
	public UUID uuid;
	public String type;
	
	
}
