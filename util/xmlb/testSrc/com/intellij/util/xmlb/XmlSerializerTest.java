package com.intellij.util.xmlb;

import junit.framework.TestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

/**
 * @author mike
 */
public class XmlSerializerTest extends TestCase {
    private static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    public static class EmptyBean {}
    public void testEmptyBeanSerialization() throws Exception {
        assertSerialization(
                "<EmptyBean/>",
                new EmptyBean()
        );
    }

    @Tag(name="Bean")
    public static class  EmptyBeanWithCustomName {}
    public void testEmptyBeanSerializationWithCustomName() throws Exception {
        assertSerialization(
                "<Bean/>",
                new EmptyBeanWithCustomName()
        );
    }

    

    public static class BeanWithPublicFields {
        public int INT_V = 1;
        public String STRING_V = "hello";
    }
    public void testPublicFieldSerialization() throws Exception {
        assertSerialization(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>",
                new BeanWithPublicFields()
        );
    }


    public static class BeanWithPublicFieldsDescendant extends BeanWithPublicFields {
        public String NEW_S = "foo";
    }
    public void testPublicFieldSerializationWithInheritance() throws Exception {
        assertSerialization(
                "<BeanWithPublicFieldsDescendant><option name=\"NEW_S\" value=\"foo\"/><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFieldsDescendant>",
                new BeanWithPublicFieldsDescendant()
        );
    }

    public void testSubBeanSerialization() throws Exception {
        fail();
    }

    public void testListSerialization() throws Exception {
        fail();
    }

    public void testSetSerialization() throws Exception {
        fail();
    }

    public void testMapSerialization() throws Exception {
        fail();
    }

    private void assertSerialization(String expectedText, Object bean) throws ParserConfigurationException, TransformerException, XmlSerializer.XmlSerializationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();

        Element element = XmlSerializer.serialize(bean, document);
        document.appendChild(element);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(element);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(byteArrayOutputStream);
        transformer.transform(source, result);

        String actualString = new String(byteArrayOutputStream.toByteArray());

        if (!expectedText.startsWith(XML_PREFIX)) {
            if (actualString.startsWith(XML_PREFIX)) actualString = actualString.substring(XML_PREFIX.length());
        }

        assertEquals(expectedText, actualString);
    }


}
