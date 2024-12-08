import com.sap.gateway.ip.core.customdev.util.Message;
import java.util.HashMap;

def Message processData(Message message) {
    
	// Initialize Loop Counter and set property
	Integer loopCounter = 0;
	message.setProperty("loopCounter", loopCounter);
	
	//create ID & UUID for Notifications
	String UUID = java.util.UUID.randomUUID().toString();
	String ID = UUID.replaceAll("-", "").toUpperCase();
	
	message.setProperty("START_NOTIFICATION_UUID", UUID);
	message.setProperty("START_NOTIFICATION_ID", ID);
	
	UUID = java.util.UUID.randomUUID().toString();
	ID = UUID.replaceAll("-", "").toUpperCase();
	
	message.setProperty("END_NOTIFICATION_UUID", UUID);
	message.setProperty("END_NOTIFICATION_ID", ID);
	
	return message;    
    
}