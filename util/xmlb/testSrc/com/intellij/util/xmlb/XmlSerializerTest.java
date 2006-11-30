package com.intellij.util.xmlb;

import junit.framework.TestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * @author mike
 */
public class XmlSerializerTest extends TestCase {
    private static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    public static class EmptyBean {}
    public void testEmptyBeanSerialization() throws Exception {
        doSerializerTest(
                "<EmptyBean/>",
                new EmptyBean()
        );
    }

    @Tag(name="Bean")
    public static class  EmptyBeanWithCustomName {}
    public void testEmptyBeanSerializationWithCustomName() throws Exception {
        doSerializerTest(
                "<Bean/>",
                new EmptyBeanWithCustomName()
        );
    }

    

    public static class BeanWithPublicFields {
        public int INT_V = 1;
        public String STRING_V = "hello";
    }
    public void testPublicFieldSerialization() throws Exception {
        BeanWithPublicFields bean = new BeanWithPublicFields();

        doSerializerTest(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>",
                bean
        );

        bean.INT_V = 2;
        bean.STRING_V = "bye";

        doSerializerTest(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFields>",
                bean
        );
    }


    public static class BeanWithPublicFieldsDescendant extends BeanWithPublicFields {
        public String NEW_S = "foo";
    }
    public void testPublicFieldSerializationWithInheritance() throws Exception {
        BeanWithPublicFieldsDescendant bean = new BeanWithPublicFieldsDescendant();

        doSerializerTest(
                "<BeanWithPublicFieldsDescendant><option name=\"NEW_S\" value=\"foo\"/><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFieldsDescendant>",
                bean
        );

        bean.INT_V = 2;
        bean.STRING_V = "bye";
        bean.NEW_S = "bar";

        doSerializerTest(
                "<BeanWithPublicFieldsDescendant><option name=\"NEW_S\" value=\"bar\"/><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFieldsDescendant>",
                bean
        );
    }

    public static class BeanWithSubBean {
        public EmptyBeanWithCustomName BEAN1 = new EmptyBeanWithCustomName();
        public BeanWithPublicFields BEAN2 = new BeanWithPublicFields();
    }
    public void testSubBeanSerialization() throws Exception {
        BeanWithSubBean bean = new BeanWithSubBean();
        doSerializerTest(
                "<BeanWithSubBean><option name=\"BEAN1\"><Bean/></option><option name=\"BEAN2\"><BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields></option></BeanWithSubBean>",
                bean
        );
        bean.BEAN2.INT_V = 2;
        bean.BEAN2.STRING_V = "bye";

        doSerializerTest(
                "<BeanWithSubBean><option name=\"BEAN1\"><Bean/></option><option name=\"BEAN2\"><BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFields></option></BeanWithSubBean>",
                bean
        );
    }

    public void testNullFieldValue() throws Exception {
        BeanWithPublicFields bean1 = new BeanWithPublicFields();

        doSerializerTest(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>",
                bean1
        );

        bean1.STRING_V = null;

        doSerializerTest(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\"/></BeanWithPublicFields>",
                bean1
        );

        BeanWithSubBean bean2 = new BeanWithSubBean();
        bean2.BEAN1 = null;
        bean2.BEAN2 = null;

        doSerializerTest(
                "<BeanWithSubBean><option name=\"BEAN1\"/><option name=\"BEAN2\"/></BeanWithSubBean>",
                bean2
        );

    }

    public static class BeanWithList {
        public List<String> VALUES = new ArrayList<String>(Arrays.asList(new String[] {"a", "b", "c"}));
    }
    public void testListSerialization() throws Exception {
        BeanWithList bean = new BeanWithList();

        doSerializerTest(
                "<BeanWithList><option name=\"VALUES\"><collection><option value=\"a\"/><option value=\"b\"/><option value=\"c\"/></collection></option></BeanWithList>",
                bean
        );

        bean.VALUES = new ArrayList<String>(Arrays.asList(new String[] {"1", "2", "3"}));

        doSerializerTest(
                "<BeanWithList><option name=\"VALUES\"><collection><option value=\"1\"/><option value=\"2\"/><option value=\"3\"/></collection></option></BeanWithList>",
                bean
        );
    }

    public static class BeanWithSet{
        public Set<String> VALUES = new HashSet<String>(Arrays.asList(new String[] {"a", "b", "w"}));
    }
    public void testSetSerialization() throws Exception {
        BeanWithSet bean = new BeanWithSet();
        doSerializerTest(
                "<BeanWithSet><option name=\"VALUES\"><collection><option value=\"w\"/><option value=\"a\"/><option value=\"b\"/></collection></option></BeanWithSet>",
                bean
        );
        bean.VALUES = new HashSet<String>(Arrays.asList(new String[] {"1", "2", "3"}));

        doSerializerTest(
                "<BeanWithSet><option name=\"VALUES\"><collection><option value=\"3\"/><option value=\"2\"/><option value=\"1\"/></collection></option></BeanWithSet>",
                bean
        );
    }

    public static class BeanWithMap{
        public Map<String, String> VALUES = new HashMap<String, String>();
        {
            VALUES.put("a", "1");
            VALUES.put("b", "2");
            VALUES.put("c", "3");
        }
    }

    public void testMapSerialization() throws Exception {
        BeanWithMap bean = new BeanWithMap();
        doSerializerTest(
                "<BeanWithMap><option name=\"VALUES\"><map><entry key=\"a\" value=\"1\"/><entry key=\"c\" value=\"3\"/><entry key=\"b\" value=\"2\"/></map></option></BeanWithMap>",
                bean
        );
        bean.VALUES.clear();
        bean.VALUES.put("1", "a");
        bean.VALUES.put("2", "b");
        bean.VALUES.put("3", "c");

        doSerializerTest(
                "<BeanWithMap><option name=\"VALUES\"><map><entry key=\"3\" value=\"c\"/><entry key=\"2\" value=\"b\"/><entry key=\"1\" value=\"a\"/></map></option></BeanWithMap>",
                bean
        );
    }

    public static class BeanWithProperty {
        private String name = "James";

        public String getName() {
            return name;
        }


        public void setName(String name) {
            this.name = name;
        }
    }
    public void testPropertySerialization() throws Exception {
        BeanWithProperty bean = new BeanWithProperty();

        doSerializerTest(
                "<BeanWithProperty><option name=\"name\" value=\"James\"/></BeanWithProperty>",
                bean
        );

        bean.setName("Bond");

        doSerializerTest(
                "<BeanWithProperty><option name=\"name\" value=\"Bond\"/></BeanWithProperty>",
                bean
        );
    }

    public static class BeanWithFieldWithTagAnnotation {
        @Tag(name = "name")
        public String STRING_V = "hello";
    }

    public void testFieldWithTagAnnotation() throws Exception {
        BeanWithFieldWithTagAnnotation bean = new BeanWithFieldWithTagAnnotation();

        doSerializerTest(
                "<BeanWithFieldWithTagAnnotation><name>hello</name></BeanWithFieldWithTagAnnotation>",
                bean
        );

        bean.STRING_V = "bye";

        doSerializerTest(
                "<BeanWithFieldWithTagAnnotation><name>bye</name></BeanWithFieldWithTagAnnotation>",
                bean
        );
    }

    public void testShuffledDeserialize() throws Exception {
        BeanWithPublicFields bean = new BeanWithPublicFields();
        bean.INT_V = 987;
        bean.STRING_V = "1234";

        Element element = serialize(bean);

        Node node = element.getChildNodes().item(0);
        element.removeChild(node);
        element.appendChild(node);

        bean = XmlSerializer.deserialize(element, bean.getClass());
        assertEquals(987, bean.INT_V);
        assertEquals("1234", bean.STRING_V);
    }

    private void doSerializerTest(String expectedText, Object bean)
            throws ParserConfigurationException, TransformerException, XmlSerializationException {
        Element element = assertSerializer(bean, expectedText, "Serialization failure");

        //test deserializer

        Object o = XmlSerializer.deserialize(element, bean.getClass());
        assertSerializer(o, expectedText, "Deserialization failure");
    }

    private Element assertSerializer(Object bean, String expectedText, String message) throws ParserConfigurationException, XmlSerializationException, TransformerException {
        Element element = serialize(bean);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(element);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(byteArrayOutputStream);
        transformer.transform(source, result);

        String actualString = new String(byteArrayOutputStream.toByteArray()).trim();

        if (!expectedText.startsWith(XML_PREFIX)) {
            if (actualString.startsWith(XML_PREFIX)) actualString = actualString.substring(XML_PREFIX.length()).trim();
        }

        assertEquals(message, expectedText, actualString);
        return element;
    }

    private Element serialize(Object bean) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();

        Element element = XmlSerializer.serialize(bean, document);
        document.appendChild(element);
        return element;
    }
}
