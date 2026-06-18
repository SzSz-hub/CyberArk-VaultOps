import org.w3c.dom.*;
import java.util.ArrayList;
import java.util.List;

public class PVConfigurationParser extends Parser {
    public record ConnectionComponentEntry(String id, String name, String ClientApp, String ClientDispatcher, Integer assignmentCount, XmlNode details) {
    }

    public List<ConnectionComponentEntry> GetConnectionComponents(String pvConfigurationPath) throws Exception {
        Document doc = loadSecureDocument(pvConfigurationPath);

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

            entries.add(new ConnectionComponentEntry(id, name, clientApp, clientDispatcher, 0, parseElementTree(component)));
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
        Document doc = loadSecureDocument(pvConfigurationPath);

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
        Document doc = loadSecureDocument(pvConfigurationPath);

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
}