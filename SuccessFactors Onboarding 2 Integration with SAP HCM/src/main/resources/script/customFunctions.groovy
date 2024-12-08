import com.sap.it.api.mapping.MappingContext;

def String getPropertyValue(String property, MappingContext context){
	return context.getProperty(property);
}

def String generateGUID(String input){
	return UUID.randomUUID().toString();
}

def String convertUUIDtoID(String UUID){
	return UUID.replaceAll("-", "").toUpperCase();
}

def String setSapMessageId(String MessageID, MappingContext context){
	context.setHeader("SapMessageId", MessageID);
	return MessageID;
}

def String setSapMessageIdEx(String MessageIDEx, MappingContext context){
	context.setHeader("SapMessageIdEx", MessageIDEx);
	return MessageIDEx;
}