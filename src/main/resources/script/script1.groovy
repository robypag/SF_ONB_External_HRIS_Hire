import com.sap.gateway.ip.core.customdev.util.Message;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;
import groovy.xml.*;

def Message logMessage(Message message) {
    def body = message.getBody(java.lang.String) as String;
    def messageLog = messageLogFactory.getMessageLog(message);
    if(messageLog != null){
        messageLog.setStringProperty("Query Mapping", "Printing Query As Attachment")
        messageLog.addAttachmentAsString("Mapped Query:", body, "application/xml");
     }
    return message;
}

def Message logCompound(Message message) {
    def body = message.getBody(java.lang.String) as String;
    def messageLog = messageLogFactory.getMessageLog(message);
    if(messageLog != null){
        messageLog.setStringProperty("Compound Result", "Printing Query As Attachment")
        messageLog.addAttachmentAsString("SF CE Result:", body, "application/xml");
     }
    return message;
}

class XMLHelper {

	private XPath xPath;
	
	public XMLHelper() {
		XPathFactory factory = XPathFactory.newInstance();
		xPath = factory.newXPath();
	}
	
	// Create a new node with a tag name and value
	public Node createNode(String name, String value, Node newParentNode, Document doc) {		
		Node newNode = doc.createElement(name);
		newNode.setTextContent(value);		
		newParentNode.appendChild(newNode);
		return newNode;
	}

	// Create a new node based on an existing node
	public Node createNode(String name, Node baseNode, Node newParentNode, Document doc) {
		if (baseNode != null) {
			return createNode(name, baseNode.getNodeName(), newParentNode, doc);
		} else {
			return null;
		}
	}

	// Retrieve a node of a given path for a specific parent node
	public Node retrieveNodeOfParentNode(String path, Node parentNode) throws XPathExpressionException {		
		return (Node) this.xPath.evaluate(path, parentNode, XPathConstants.NODE);		
	}

	// Retrieve a node of a given path for a specific parent node
	public Node retrieveNodeOfDocument(String path, Document doc) throws XPathExpressionException {
		return (Node) this.xPath.evaluate(path, doc, XPathConstants.NODE);
	}
	
	public Document createXMLNodes(String sourceString) {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = null;
	    builder = builderFactory.newDocumentBuilder();
	    InputStream is = new ByteArrayInputStream(sourceString.getBytes(StandardCharsets.UTF_8));
		return builder.parse(is);
	}
}

def Message processData(Message message) {
	
	//get payload (compound employee api query result)
	Document body = message.getBody(org.w3c.dom.Document);
    
	//initiate xml helper
	XMLHelper xmlHelper = new XMLHelper();
	
	def pmap = message.getProperties();
	def messageLog = messageLogFactory.getMessageLog(message);
	
	// body
	String rawbody = message.getBody(java.lang.String) as String;
	
	//get process properties 
	String enableLogging = pmap.get("ENABLE_LOGGING");
	String replicationTargetSystem = pmap.get("SENDER_SYSTEM");
	String referenceIDParameter = pmap.get("ID");
	String referenceUUIDParameter = pmap.get("UUID");
	
	Integer loopCounter = message.getProperty("loopCounter");
	loopCounter = loopCounter + 1;
	message.setProperty("loopCounter", loopCounter);
	
	//log payload before mapping
	if(enableLogging != null && enableLogging.toUpperCase().equals("TRUE")){
		if(messageLog != null){
			messageLog.addAttachmentAsString("Payload " + loopCounter.toString() + " Before Mapping", rawbody, "text/xml");
		}
	}	
	
    //create message ids for erp inbound message
	String messIDEx = java.util.UUID.randomUUID().toString();
	String messID = messIDEx.replaceAll("-", "").toUpperCase();
	message.setHeader("SapMessageId", messID);
	message.setHeader("SapMessageIdEx", messIDEx);
	
	//define erp target interface structure (including namespace)
	String targetXML = "<n0:EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest xmlns:n0=\"http://sap.com/xi/PASEIN\"><MessageHeader><ID/><UUID/><ReferenceID/><ReferenceUUID/><CreationDateTime/><SenderBusinessSystemID/><RecipientBusinessSystemID/></MessageHeader><EmplMasterDataAndOrgAssignmBndlReplReq><SourceSystemOutputBase64/></EmplMasterDataAndOrgAssignmBndlReplReq></n0:EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest>";
	
	//get list of queried employee master data records
	Node queryCompoundEmployeeResponseNode = xmlHelper.retrieveNodeOfDocument("//queryCompoundEmployeeResponse", body);
	
	//get execution timestamp for notification message
	Node executionTimestamp = xmlHelper.retrieveNodeOfParentNode("./CompoundEmployee/execution_timestamp", queryCompoundEmployeeResponseNode);
	message.setProperty("EXECUTION_TIMESTAMP", executionTimestamp.getTextContent());
	
	//create erp inbound message
	Document EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest;
	try {
	    EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest = xmlHelper.createXMLNodes(targetXML);
	} catch (Exception e) {
	    if(messageLog != null && e != null){
            messageLog.setStringProperty("Exception occured while trying to create ERP inbound message Document ", e.getClass().getName() + " - " + e.getMessage());
            return;
        }
	}
	
	// define message header node
	Node messageHeader = xmlHelper.retrieveNodeOfDocument("//MessageHeader", EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest);
	
	//set header ids
	Node id = xmlHelper.retrieveNodeOfParentNode("./ID", messageHeader);
	id.setTextContent(messID);
	Node uuid = xmlHelper.retrieveNodeOfParentNode("./UUID", messageHeader);
	uuid.setTextContent(messIDEx);
	Node referenceId = xmlHelper.retrieveNodeOfParentNode("./ReferenceID", messageHeader);
	referenceId.setTextContent(referenceIDParameter);
	Node referenceUuid = xmlHelper.retrieveNodeOfParentNode("./ReferenceUUID", messageHeader);
	referenceUuid.setTextContent(referenceUUIDParameter);
	
	//set replication target system
	Node recipientBusinessSystemID = xmlHelper.retrieveNodeOfParentNode("./RecipientBusinessSystemID", messageHeader);
	recipientBusinessSystemID.setTextContent(replicationTargetSystem);
	
	//set replication source system
	Node senderBusinessSystemID = xmlHelper.retrieveNodeOfParentNode("./SenderBusinessSystemID", messageHeader);
	senderBusinessSystemID.setTextContent("Employee Central");
	
	//set creation time stamp
	Node creationDateTime = xmlHelper.retrieveNodeOfParentNode("./CreationDateTime", messageHeader);
	Date date = new Date();
	SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	creationDateTime.setTextContent(formater.format(date));
	
	//define replication request node
	Node emplMasterDataAndOrgAssignmBndlReplReq = xmlHelper.retrieveNodeOfDocument("//EmplMasterDataAndOrgAssignmBndlReplReq", EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest);
		
	//create node source system output 
	Node sourceSystemOutputNode = xmlHelper.retrieveNodeOfParentNode("./SourceSystemOutputBase64", emplMasterDataAndOrgAssignmBndlReplReq);
	sourceSystemOutputNode.setTextContent(rawbody.getBytes("UTF-8").encodeBase64().toString());		
	
	//set attributes of node source system output
    NamedNodeMap attributes = sourceSystemOutputNode.getAttributes();
    Attr attributeId = EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest.createAttribute("id");
    attributeId.setValue(messID);
    Attr attributeFileName = EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest.createAttribute("fileName");
    attributeFileName.setValue("PayloadSFAPI");
    attributes.setNamedItem(attributeId);  
    attributes.setNamedItem(attributeFileName);
		
	//set mapped target message as payload
	message.setBody(EmployeeMasterDataAndOrgAssignmentBundleReplicationRequest);
	
	return message;
}

def Message enrichCE(Message message) {
    def compoundEmployeeBody = message.getBody(java.lang.String) as String;
    def onboardingProcessBody = message.getProperty("OnboardingData");
    def compoundEmployeeXmlData = new XmlSlurper().parseText(compoundEmployeeBody);
    def onboardingProcessXmlData = new XmlSlurper().parseText(onboardingProcessBody);
    compoundEmployeeXmlData.CompoundEmployee.person.appendNode(onboardingProcessXmlData);
    String outxml = XmlUtil.serialize( compoundEmployeeXmlData );
    message.setBody(outxml);
    return message;
}
