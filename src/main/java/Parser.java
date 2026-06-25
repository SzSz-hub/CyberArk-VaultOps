import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Parser {
    public record XmlNode(String name, Map<String, String> attributes, List<XmlNode> children) {
    }

    private static final long MAX_SOURCE_XML_BYTES = 64L * 1024 * 1024;
    private static final int MAX_SOURCE_XML_ELEMENTS = 500_000;

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
        Path path = Paths.get(xmlPath);
        long size = Files.size(path);
        if (size <= 0 || size > MAX_SOURCE_XML_BYTES) {
            throw new IOException("XML file size " + size + " bytes is outside the supported range (1.."
                    + MAX_SOURCE_XML_BYTES + " bytes): " + path.getFileName());
        }
        DocumentBuilder db = newSecureDocumentBuilderFactory().newDocumentBuilder();
        Document doc = db.parse(path.toFile());
        enforceElementBudget(doc, path);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private static void enforceElementBudget(Document doc, Path path) throws IOException {
        int elements = doc.getElementsByTagName("*").getLength();
        if (elements > MAX_SOURCE_XML_ELEMENTS) {
            throw new IOException("XML document has " + elements + " elements, exceeding the supported maximum of "
                    + MAX_SOURCE_XML_ELEMENTS + ": " + path.getFileName());
        }
    }

    static Element requireRoot(Document doc, String expectedRootName) {
        Element root = doc == null ? null : doc.getDocumentElement();
        if (root == null || !expectedRootName.equals(root.getTagName())) {
            String actual = root == null ? "(none)" : root.getTagName();
            throw new IllegalStateException("Unexpected XML root: expected <" + expectedRootName
                    + "> but found <" + actual + ">.");
        }
        return root;
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
        return parseElementTree(element, 0);
    }

    private static final int MAX_TREE_DEPTH = 512;

    private static PVConfigurationParser.XmlNode parseElementTree(Element element, int depth) {
        if (depth > MAX_TREE_DEPTH) {
            throw new IllegalStateException("XML nesting exceeds the supported depth of " + MAX_TREE_DEPTH + ".");
        }
        return new PVConfigurationParser.XmlNode(element.getTagName(), parseAttributes(element), parseChildElements(element, depth));
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
        return parseChildElements(element, 0);
    }

    private static List<PVConfigurationParser.XmlNode> parseChildElements(Element element, int depth) {
        List<PVConfigurationParser.XmlNode> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add(parseElementTree((Element) node, depth + 1));
            }
        }
        return children;
    }
}