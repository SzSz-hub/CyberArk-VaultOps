import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Parser {
    public record XmlNode(String name, Map<String, String> attributes, List<XmlNode> children) {
    }

    static String attr(Element element, String attrName) {
        return element.hasAttribute(attrName) ? element.getAttribute(attrName).trim() : "";
    }

    static Element firstChildElement(Element parent, String childTagName) {
        NodeList children = parent.getElementsByTagName(childTagName);
        if (children.getLength() == 0) return null;
        Node child = children.item(0);
        return child.getNodeType() == Node.ELEMENT_NODE ? (Element) child : null;
    }

    static PVConfigurationParser.XmlNode parseElementTree(Element element) {
        return new PVConfigurationParser.XmlNode(element.getTagName(), parseAttributes(element), parseChildElements(element));
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

    static List<PVConfigurationParser.XmlNode> parseChildElements(Element element) {
        List<PVConfigurationParser.XmlNode> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add(parseElementTree((Element) node));
            }
        }
        return children;
    }
}
