import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class XmlProtocol {
    private static final DocumentBuilderFactory DOCUMENTS = DocumentBuilderFactory.newInstance();

    static {
        DOCUMENTS.setIgnoringElementContentWhitespace(true);
    }

    public static Document read(DataInputStream input) throws Exception {
        int length = input.readInt();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IllegalStateException("XML сообщение получено не полностью");
        }
        return DOCUMENTS.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    public static void write(DataOutputStream output, Document document) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(document), new StreamResult(bytes));
        byte[] payload = bytes.toByteArray();
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }

    public static Document command(String name) throws Exception {
        Document document = newDocument();
        Element command = document.createElement("command");
        command.setAttribute("name", name);
        document.appendChild(command);
        return document;
    }

    public static Document success(String session, List<UserInfo> users, List<ChatEvent> history) throws Exception {
        Document document = newDocument();
        Element success = document.createElement("success");
        document.appendChild(success);
        if (session != null) {
            append(document, success, "session", session);
        }
        if (users != null) {
            Element listUsers = document.createElement("listusers");
            success.appendChild(listUsers);
            for (UserInfo info : users) {
                Element user = document.createElement("user");
                listUsers.appendChild(user);
                append(document, user, "name", info.name);
                append(document, user, "type", info.type);
            }
        }
        if (history != null) {
            Element historyElement = document.createElement("history");
            success.appendChild(historyElement);
            for (ChatEvent event : history) {
                Element item = document.createElement("item");
                item.setAttribute("kind", event.kind.name());
                historyElement.appendChild(item);
                append(document, item, "name", event.name == null ? "" : event.name);
                append(document, item, "message", event.text == null ? "" : event.text);
            }
        }
        return document;
    }

    public static Document error(String message) throws Exception {
        Document document = newDocument();
        Element error = document.createElement("error");
        document.appendChild(error);
        append(document, error, "message", message);
        return document;
    }

    public static Document event(ChatEvent event) throws Exception {
        Document document = newDocument();
        Element root = document.createElement("event");
        root.setAttribute("name", switch (event.kind) {
            case MESSAGE -> "message";
            case USER_LOGIN -> "userlogin";
            case USER_LOGOUT -> "userlogout";
            case SYSTEM -> "system";
        });
        document.appendChild(root);
        if (event.name != null) {
            append(document, root, "name", event.name);
        }
        if (event.text != null) {
            append(document, root, "message", event.text);
        }
        return document;
    }

    public static Document parseString(String xml) throws Exception {
        return DOCUMENTS.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    public static String rootName(Document document) {
        return document.getDocumentElement().getTagName();
    }

    public static String attr(Document document, String name) {
        return document.getDocumentElement().getAttribute(name);
    }

    public static String text(Document document, String tag) {
        return text(document.getDocumentElement(), tag);
    }

    public static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? "" : list.item(0).getTextContent();
    }

    public static void append(Document document, Element parent, String name, String text) {
        Element element = document.createElement(name);
        element.setTextContent(text);
        parent.appendChild(element);
    }

    private static Document newDocument() throws Exception {
        return DOCUMENTS.newDocumentBuilder().newDocument();
    }
}
