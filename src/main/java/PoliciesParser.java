import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PoliciesParser extends Parser {
    public record policiesEntry(String id, String name, String ClientApp, String ClientDispatcher, XmlNode details) {
    }

    public record deviceEntry(String name, List<policiesEntry> policiesEntries){}
    public record usageEntry (String name, List<XmlNode> children){}

    public List<usageEntry> getUsage(String policiesPath) throws Exception{
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(policiesPath));
        doc.getDocumentElement().normalize();

        NodeList nodeList = doc.getElementsByTagName("usages");
        List<usageEntry> usagesEntries = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element usage = (Element) node;
            String name = usage.getAttribute("ID");

            usagesEntries.add(new usageEntry(name, parseChildElements(usage)));
        }

        return usagesEntries;
    };

    public List<PVConfigurationParser.ConnectionComponentEntry> GetConnectionComponents(String pvConfigurationPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(pvConfigurationPath));
        doc.getDocumentElement().normalize();

        NodeList componentNodes = doc.getElementsByTagName("ConnectionComponent");
        List<PVConfigurationParser.ConnectionComponentEntry> entries = new ArrayList<>();

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

            entries.add(new PVConfigurationParser.ConnectionComponentEntry(id, name, clientApp, clientDispatcher, parseElementTree(component)));
        }

        return entries;
    }

}
