import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Parser {
    public record XmlNode(String name, Map<String, String> attributes, List<XmlNode> children) {
    }

    protected static DocumentBuilderFactory newSecureDocumentBuilderFactory() throws ParserConfigurationException {
        return newSecureDocumentBuilderFactory(true);
    }

    static DocumentBuilderFactory newSecureDocumentBuilderFactory(boolean ignoreComments) throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setIgnoringComments(ignoreComments);

        // Primary defense: forbid DOCTYPE entirely. Any document containing a DTD fails fast.
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        // Defense in depth in case a parser implementation ignores the flag above.
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return dbf;
    }

    protected static Document loadSecureDocument(String xmlPath) throws Exception {
        DocumentBuilder db = newSecureDocumentBuilderFactory().newDocumentBuilder();
        Document doc = db.parse(new File(xmlPath));
        doc.getDocumentElement().normalize();
        return doc;
    }

    static String attr(Element element, String attrName) {
        return element.hasAttribute(attrName) ? element.getAttribute(attrName).trim() : "";
    }

    static Element firstChildElement(Element parent, String childTagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && childTagName.equals(((Element) child).getTagName())) {
                return (Element) child;
            }
        }
        return null;
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