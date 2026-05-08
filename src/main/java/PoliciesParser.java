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
    public record XmlNode(String name, Map<String, String> attributes, List<PVConfigurationParser.XmlNode> children) {
    }

    public record policiesEntry(String id, String name, String ClientApp, String ClientDispatcher, PVConfigurationParser.XmlNode details) {
    }

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