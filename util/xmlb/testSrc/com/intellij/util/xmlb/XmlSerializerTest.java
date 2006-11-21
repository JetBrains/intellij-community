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
import javax.xml.transform.OutputKeys;
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
        BeanWithPublicFields bean = new BeanWithPublicFields();

        assertSerialization(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>",
                bean
        );

        bean.INT_V = 2;
        bean.STRING_V = "bye";

        assertSerialization(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFields>",
                bean
        );
    }


    public static class BeanWithPublicFieldsDescendant extends BeanWithPublicFields {
        public String NEW_S = "foo";
    }
    public void testPublicFieldSerializationWithInheritance() throws Exception {
        BeanWithPublicFieldsDescendant bean = new BeanWithPublicFieldsDescendant();

        assertSerialization(
                "<BeanWithPublicFieldsDescendant><option name=\"NEW_S\" value=\"foo\"/><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFieldsDescendant>",
                bean
        );

        bean.INT_V = 2;
        bean.STRING_V = "bye";
        bean.NEW_S = "bar";

        assertSerialization(
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
        assertSerialization(
                "<BeanWithSubBean><option name=\"BEAN1\"><Bean/></option><option name=\"BEAN2\"><BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields></option></BeanWithSubBean>",
                bean
        );
        bean.BEAN2.INT_V = 2;
        bean.BEAN2.STRING_V = "bye";

        assertSerialization(
                "<BeanWithSubBean><option name=\"BEAN1\"><Bean/></option><option name=\"BEAN2\"><BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFields></option></BeanWithSubBean>",
                bean
        );
    }

    public void testNullFieldValue() throws Exception {
        BeanWithPublicFields bean1 = new BeanWithPublicFields();

        assertSerialization(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>",
                bean1
        );

        bean1.STRING_V = null;

        assertSerialization(
                "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/></BeanWithPublicFields>",
                bean1
        );

        BeanWithSubBean bean2 = new BeanWithSubBean();
        bean2.BEAN1 = null;
        bean2.BEAN2 = null;

        assertSerialization(
                "<BeanWithSubBean/>",
                bean2
        );

    }

    public static class BeanWithList {
        public List VALUES = Arrays.asList(new Object[] {"a", "b", "c"});
    }
    public void testListSerialization() throws Exception {
        BeanWithList bean = new BeanWithList();

        assertSerialization(
                "<BeanWithList><option name=\"VALUES\"><collection><option value=\"a\"/><option value=\"b\"/><option value=\"c\"/></collection></option></BeanWithList>",
                bean
        );

        bean.VALUES = Arrays.asList(new Object[] {"1", "2", "3"});

        assertSerialization(
                "<BeanWithList><option name=\"VALUES\"><collection><option value=\"1\"/><option value=\"2\"/><option value=\"3\"/></collection></option></BeanWithList>",
                bean
        );
    }

    public static class BeanWithSet{
        public Set VALUES = new HashSet(Arrays.asList(new Object[] {"a", "b", "w"}));
    }
    public void testSetSerialization() throws Exception {
        BeanWithSet bean = new BeanWithSet();
        assertSerialization(
                "<BeanWithSet><option name=\"VALUES\"><collection><option value=\"w\"/><option value=\"a\"/><option value=\"b\"/></collection></option></BeanWithSet>",
                bean
        );
        bean.VALUES = new HashSet(Arrays.asList(new Object[] {"1", "2", "3"}));

        assertSerialization(
                "<BeanWithSet><option name=\"VALUES\"><collection><option value=\"3\"/><option value=\"2\"/><option value=\"1\"/></collection></option></BeanWithSet>",
                bean
        );
    }

    public static class BeanWithMap{
        public Map VALUES = new HashMap();
        {
            VALUES.put("a", "1");
            VALUES.put("b", "2");
            VALUES.put("c", "3");
        }
    }

    public void testMapSerialization() throws Exception {
        BeanWithMap bean = new BeanWithMap();
        assertSerialization(
                "<BeanWithMap><option name=\"VALUES\"><map><entry key=\"a\" value=\"1\"/><entry key=\"c\" value=\"3\"/><entry key=\"b\" value=\"2\"/></map></option></BeanWithMap>",
                bean
        );
        bean.VALUES.clear();
        bean.VALUES.put("1", "a");
        bean.VALUES.put("2", "b");
        bean.VALUES.put("3", "c");

        assertSerialization(
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

        assertSerialization(
                "<BeanWithProperty><option name=\"name\" value=\"James\"/></BeanWithProperty>",
                bean
        );

        bean.setName("Bond");

        assertSerialization(
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

        assertSerialization(
                "<BeanWithFieldWithTagAnnotation><name>hello</name></BeanWithFieldWithTagAnnotation>",
                bean
        );

        bean.STRING_V = "bye";

        assertSerialization(
                "<BeanWithFieldWithTagAnnotation><name>bye</name></BeanWithFieldWithTagAnnotation>",
                bean
        );
    }

    private void assertSerialization(String expectedText, Object bean)
            throws ParserConfigurationException, TransformerException, XmlSerializationException {
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

        String actualString = new String(byteArrayOutputStream.toByteArray()).trim();

        if (!expectedText.startsWith(XML_PREFIX)) {
            if (actualString.startsWith(XML_PREFIX)) actualString = actualString.substring(XML_PREFIX.length()).trim();
        }

        assertEquals(expectedText, actualString);
    }
}
