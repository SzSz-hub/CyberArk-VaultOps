import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PVConfigurationParser {
    public record XmlNode(String name, Map<String, String> attributes, List<XmlNode> children) {
    }

    public record ConnectionComponentEntry(String id, String name, String ClientApp, String ClientDispatcher, XmlNode details) {
    }

    public List<ConnectionComponentEntry> GetConnectionComponents(String pvConfigurationPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(pvConfigurationPath));
        doc.getDocumentElement().normalize();

        NodeList componentNodes = doc.getElementsByTagName("ConnectionComponent");
        List<ConnectionComponentEntry> entries = new ArrayList<>();

        for (int i = 0; i < componentNodes.getLength(); i++) {
            Node node = componentNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element component = (Element) node;

            String id = attr(component, "Id");
            String name = attr(component, "DisplayName");
            Element targetSettings = firstChildElement(component, "TargetSettings");
            String clientApp = targetSettings == null ? null : attr(targetSettings, "ClientApp");
            String clientDispatcher = targetSettings == null ? null : attr(targetSettings, "ClientDispatcher");

            entries.add(new ConnectionComponentEntry(id, name, clientApp, clientDispatcher, parseElementTree(component)));
        }

        return entries;
    }

    public record PSMServerEntry(String id, String name, String psmProtocolVersion, String serverAddress,
                                 String serverPort, String serverSafe, String serverFolder, String serverObject,
                                 String adminObject, String tsGatewayAddress, String tsGatewayDomain,
                                 String tsGatewayEnable, String tsGatewaySafe, String tsGatewayFolder,
                                 String tsGatewayObject) {
    }

    public List<PSMServerEntry> getPSMServers(String pvConfigurationPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(pvConfigurationPath));
        doc.getDocumentElement().normalize();

        NodeList serverNodes = doc.getElementsByTagName("PSMServer");
        List<PSMServerEntry> entries = new ArrayList<>();

        for (int i = 0; i < serverNodes.getLength(); i++) {
            Node node = serverNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element psmServer = (Element) node;

            String id = attr(psmServer, "ID");
            String name = attr(psmServer, "Name");
            String psmProtocolVersion = attr(psmServer, "PSMProtocolVersion");

            Element connectionDetails = firstChildElement(psmServer, "ConnectionDetails");
            Element server = connectionDetails == null ? null : firstChildElement(connectionDetails, "Server");
            Element tsGateway = connectionDetails == null ? null : firstChildElement(connectionDetails, "TSGateway");

            String serverAddress = server == null ? "" : attr(server, "Address");
            String serverPort = server == null ? "" : attr(server, "Port");
            String serverSafe = server == null ? "" : attr(server, "Safe");
            String serverFolder = server == null ? "" : attr(server, "Folder");
            String serverObject = server == null ? "" : attr(server, "Object");
            String adminObject = server == null ? "" : attr(server, "AdminObject");

            String tsGatewayAddress = tsGateway == null ? "" : attr(tsGateway, "Address");
            String tsGatewayDomain = tsGateway == null ? "" : attr(tsGateway, "Domain");
            String tsGatewayEnable = tsGateway == null ? "" : attr(tsGateway, "Enable");
            String tsGatewaySafe = tsGateway == null ? "" : attr(tsGateway, "Safe");
            String tsGatewayFolder = tsGateway == null ? "" : attr(tsGateway, "Folder");
            String tsGatewayObject = tsGateway == null ? "" : attr(tsGateway, "Object");

            entries.add(new PSMServerEntry(
                    id, name, psmProtocolVersion,
                    serverAddress, serverPort, serverSafe, serverFolder, serverObject, adminObject,
                    tsGatewayAddress, tsGatewayDomain, tsGatewayEnable, tsGatewaySafe, tsGatewayFolder, tsGatewayObject
            ));
        }

        return entries;
    }

    public record PSMPServerEntry(String id, String name, String serverAddress,
                                 String serverPort) {
    }

    public List<PSMPServerEntry> getPSMPServers(String pvConfigurationPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(pvConfigurationPath));
        doc.getDocumentElement().normalize();

        NodeList serverNodes = doc.getElementsByTagName("PSMPServer");
        List<PSMPServerEntry> entries = new ArrayList<>();

        for (int i = 0; i < serverNodes.getLength(); i++) {
            Node node = serverNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element psmpServer = (Element) node;

            String id = attr(psmpServer, "ID");
            String name = attr(psmpServer, "Name");

            Element connectionDetails = firstChildElement(psmpServer, "ConnectionDetails");
            Element server = connectionDetails == null ? null : firstChildElement(connectionDetails, "Server");

            String serverAddress = server == null ? "" : attr(server, "Address");
            String serverPort = server == null ? "" : attr(server, "Port");


            entries.add(new PSMPServerEntry(
                    id, name,
                    serverAddress, serverPort
            ));
        }

        return entries;
    }

    private static String attr(Element element, String attrName) {
        return element.hasAttribute(attrName) ? element.getAttribute(attrName).trim() : "";
    }

    private static XmlNode parseElementTree(Element element) {
        return new XmlNode(element.getTagName(), parseAttributes(element), parseChildElements(element));
    }

    private static Map<String, String> parseAttributes(Element element) {
        Map<String, String> attributes = new LinkedHashMap<>();
        NamedNodeMap attributeNodes = element.getAttributes();
        for (int i = 0; i < attributeNodes.getLength(); i++) {
            Node node = attributeNodes.item(i);
            attributes.put(node.getNodeName(), node.getNodeValue());
        }
        return attributes;
    }

    private static List<XmlNode> parseChildElements(Element element) {
        List<XmlNode> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add(parseElementTree((Element) node));
            }
        }
        return children;
    }

    private static Element firstChildElement(Element parent, String childTagName) {
        NodeList children = parent.getElementsByTagName(childTagName);
        if (children.getLength() == 0) return null;
        Node child = children.item(0);
        return child.getNodeType() == Node.ELEMENT_NODE ? (Element) child : null;
    }
}