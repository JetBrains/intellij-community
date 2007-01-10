package com.intellij.util.xmlb;

import com.intellij.util.DOMUtil;
import com.intellij.util.xmlb.annotations.*;
import junit.framework.TestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

  public static class EmptyBean {
  }

  public void testEmptyBeanSerialization() throws Exception {
    doSerializerTest("<EmptyBean/>", new EmptyBean());
  }

  @Tag("Bean")
  public static class EmptyBeanWithCustomName {
  }

  public void testEmptyBeanSerializationWithCustomName() throws Exception {
    doSerializerTest("<Bean/>", new EmptyBeanWithCustomName());
  }


  public static class BeanWithPublicFields {
    public int INT_V = 1;
    public String STRING_V = "hello";
  }

  public void testPublicFieldSerialization() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();

    doSerializerTest(
      "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>", bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFields>", bean);
  }


  public static class BeanWithPublicFieldsDescendant extends BeanWithPublicFields {
    public String NEW_S = "foo";
  }

  public void testPublicFieldSerializationWithInheritance() throws Exception {
    BeanWithPublicFieldsDescendant bean = new BeanWithPublicFieldsDescendant();

    doSerializerTest(
      "<BeanWithPublicFieldsDescendant><option name=\"INT_V\" value=\"1\"/><option name=\"NEW_S\" value=\"foo\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFieldsDescendant>",
      bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";
    bean.NEW_S = "bar";

    doSerializerTest(
      "<BeanWithPublicFieldsDescendant><option name=\"INT_V\" value=\"2\"/><option name=\"NEW_S\" value=\"bar\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFieldsDescendant>",
      bean);
  }

  public static class BeanWithSubBean {
    public EmptyBeanWithCustomName BEAN1 = new EmptyBeanWithCustomName();
    public BeanWithPublicFields BEAN2 = new BeanWithPublicFields();
  }

  public void testSubBeanSerialization() throws Exception {
    BeanWithSubBean bean = new BeanWithSubBean();
    doSerializerTest(
      "<BeanWithSubBean><option name=\"BEAN1\"><Bean/></option><option name=\"BEAN2\"><BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields></option></BeanWithSubBean>",
      bean);
    bean.BEAN2.INT_V = 2;
    bean.BEAN2.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithSubBean><option name=\"BEAN1\"><Bean/></option><option name=\"BEAN2\"><BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/><option name=\"STRING_V\" value=\"bye\"/></BeanWithPublicFields></option></BeanWithSubBean>",
      bean);
  }

  public void testNullFieldValue() throws Exception {
    BeanWithPublicFields bean1 = new BeanWithPublicFields();

    doSerializerTest(
      "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>",
      bean1);

    bean1.STRING_V = null;

    doSerializerTest("<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\"/></BeanWithPublicFields>", bean1);

    BeanWithSubBean bean2 = new BeanWithSubBean();
    bean2.BEAN1 = null;
    bean2.BEAN2 = null;

    doSerializerTest("<BeanWithSubBean><option name=\"BEAN1\"/><option name=\"BEAN2\"/></BeanWithSubBean>", bean2);

  }

  public static class BeanWithList {
    public List<String> VALUES = new ArrayList<String>(Arrays.asList("a", "b", "c"));
  }

  public void testListSerialization() throws Exception {
    BeanWithList bean = new BeanWithList();

    doSerializerTest(
      "<BeanWithList><option name=\"VALUES\"><collection><option value=\"a\"/><option value=\"b\"/><option value=\"c\"/></collection></option></BeanWithList>",
      bean);

    bean.VALUES = new ArrayList<String>(Arrays.asList("1", "2", "3"));

    doSerializerTest(
      "<BeanWithList><option name=\"VALUES\"><collection><option value=\"1\"/><option value=\"2\"/><option value=\"3\"/></collection></option></BeanWithList>",
      bean);
  }

  public static class BeanWithSet {
    public Set<String> VALUES = new HashSet<String>(Arrays.asList("a", "b", "w"));
  }

  public void testSetSerialization() throws Exception {
    BeanWithSet bean = new BeanWithSet();
    doSerializerTest(
      "<BeanWithSet><option name=\"VALUES\"><collection><option value=\"w\"/><option value=\"a\"/><option value=\"b\"/></collection></option></BeanWithSet>",
      bean);
    bean.VALUES = new HashSet<String>(Arrays.asList("1", "2", "3"));

    doSerializerTest(
      "<BeanWithSet><option name=\"VALUES\"><collection><option value=\"3\"/><option value=\"2\"/><option value=\"1\"/></collection></option></BeanWithSet>",
      bean);
  }

  public static class BeanWithMap {
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
      bean);
    bean.VALUES.clear();
    bean.VALUES.put("1", "a");
    bean.VALUES.put("2", "b");
    bean.VALUES.put("3", "c");

    doSerializerTest(
      "<BeanWithMap><option name=\"VALUES\"><map><entry key=\"3\" value=\"c\"/><entry key=\"2\" value=\"b\"/><entry key=\"1\" value=\"a\"/></map></option></BeanWithMap>",
      bean);
  }


  public static class BeanWithMapWithAnnotations {
    @Property(surroundWithTag = false)
    @MapAnnotation(
      surroundWithTag = false,
      entryTagName = "option",
      keyAttributeName = "name",
      valueAttributeName = "value"
    )
    public Map<String, String> VALUES = new HashMap<String, String>();

    {
      VALUES.put("a", "1");
      VALUES.put("b", "2");
      VALUES.put("c", "3");
    }
  }

  public void testMapSerializationWithAnnotations() throws Exception {
    BeanWithMapWithAnnotations bean = new BeanWithMapWithAnnotations();
    doSerializerTest(
      "<BeanWithMapWithAnnotations><option name=\"a\" value=\"1\"/><option name=\"c\" value=\"3\"/><option name=\"b\" value=\"2\"/></BeanWithMapWithAnnotations>",
      bean);
    bean.VALUES.clear();
    bean.VALUES.put("1", "a");
    bean.VALUES.put("2", "b");
    bean.VALUES.put("3", "c");

    doSerializerTest(
      "<BeanWithMapWithAnnotations><option name=\"3\" value=\"c\"/><option name=\"2\" value=\"b\"/><option name=\"1\" value=\"a\"/></BeanWithMapWithAnnotations>",
      bean);
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

    doSerializerTest("<BeanWithProperty><option name=\"name\" value=\"James\"/></BeanWithProperty>", bean);

    bean.setName("Bond");

    doSerializerTest("<BeanWithProperty><option name=\"name\" value=\"Bond\"/></BeanWithProperty>", bean);
  }

  public static class BeanWithFieldWithTagAnnotation {
    @Tag("name")
    public String STRING_V = "hello";
  }

  public void testFieldWithTagAnnotation() throws Exception {
    BeanWithFieldWithTagAnnotation bean = new BeanWithFieldWithTagAnnotation();

    doSerializerTest("<BeanWithFieldWithTagAnnotation><name>hello</name></BeanWithFieldWithTagAnnotation>", bean);

    bean.STRING_V = "bye";

    doSerializerTest("<BeanWithFieldWithTagAnnotation><name>bye</name></BeanWithFieldWithTagAnnotation>", bean);
  }

  public void testShuffledDeserialize() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    bean.INT_V = 987;
    bean.STRING_V = "1234";

    Element element = serialize(bean, null);

    Node node = element.getChildNodes().item(0);
    element.removeChild(node);
    element.appendChild(node);

    bean = XmlSerializer.deserialize(element, bean.getClass());
    assert bean != null;
    assertEquals(987, bean.INT_V);
    assertEquals("1234", bean.STRING_V);
  }

  public void testFilterSerializer() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    assertSerializer(bean, "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/></BeanWithPublicFields>", new SerializationFilter() {
      public boolean accepts(Accessor accessor, Object bean) {
        return accessor.getName().startsWith("I");
      }
    });
  }

  public static class BeanWithArray {
    public String[] ARRAY_V = new String[] {"a", "b"};
  }
  public void testArray() throws Exception {
    final BeanWithArray bean = new BeanWithArray();
    doSerializerTest("<BeanWithArray><option name=\"ARRAY_V\"><array><option value=\"a\"/><option value=\"b\"/></array></option></BeanWithArray>", bean);

    bean.ARRAY_V = new String[] {"1", "2", "3"};
    doSerializerTest("<BeanWithArray><option name=\"ARRAY_V\"><array><option value=\"1\"/><option value=\"2\"/><option value=\"3\"/></array></option></BeanWithArray>", bean);
  }

  public static class BeanWithTransient {
    @Transient
    public int INT_V = 1;

    @Transient
    public String getValue() {
      return "foo";
    }
  }
  public void testTransient() throws Exception {
    final BeanWithTransient bean = new BeanWithTransient();
    doSerializerTest("<BeanWithTransient/>", bean);
  }

  public static class BeanWithArrayWithoutTagName {
    @com.intellij.util.xmlb.annotations.AbstractCollection(surroundWithTag = false)
    public String[] V = new String[]{"a"};
  }
  public void testArrayAnnotationWithoutTagNAmeGivesError() throws Exception {
    final BeanWithArrayWithoutTagName bean = new BeanWithArrayWithoutTagName();

    try {
      doSerializerTest("<BeanWithArrayWithoutTagName><option name=\"V\"><option value=\"a\"/></option></BeanWithArrayWithoutTagName>", bean);
    }
    catch (XmlSerializationException e) {
      return;
    }

    fail("No Exception");
  }

  public static class BeanWithArrayWithElementTagName {
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v")
    public String[] V = new String[]{"a", "b"};
  }
  public void testArrayAnnotationWithElementTag() throws Exception {
    final BeanWithArrayWithElementTagName bean = new BeanWithArrayWithElementTagName();

    doSerializerTest("<BeanWithArrayWithElementTagName><option name=\"V\"><array><vvalue v=\"a\"/><vvalue v=\"b\"/></array></option></BeanWithArrayWithElementTagName>", bean);

    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest("<BeanWithArrayWithElementTagName><option name=\"V\"><array><vvalue v=\"1\"/><vvalue v=\"2\"/><vvalue v=\"3\"/></array></option></BeanWithArrayWithElementTagName>", bean);
  }

  public static class BeanWithArrayWithoutTag {
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutTag() throws Exception {
    final BeanWithArrayWithoutTag bean = new BeanWithArrayWithoutTag();

    doSerializerTest("<BeanWithArrayWithoutTag><option name=\"INT_V\" value=\"1\"/><option name=\"V\"><vvalue v=\"a\"/><vvalue v=\"b\"/></option></BeanWithArrayWithoutTag>", bean);

    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest("<BeanWithArrayWithoutTag><option name=\"INT_V\" value=\"1\"/><option name=\"V\"><vvalue v=\"1\"/><vvalue v=\"2\"/><vvalue v=\"3\"/></option></BeanWithArrayWithoutTag>", bean);
  }


  public static class BeanWithPropertyWithoutTagOnPrimitiveValue {
    @Property(surroundWithTag = false)
    public int INT_V = 1;
  }
  public void testPropertyWithoutTagWithPrimitiveType() throws Exception {
    final BeanWithPropertyWithoutTagOnPrimitiveValue bean = new BeanWithPropertyWithoutTagOnPrimitiveValue();

    try {
      doSerializerTest("<BeanWithFieldWithTagAnnotation><name>hello</name></BeanWithFieldWithTagAnnotation>", bean);
    }
    catch (XmlSerializationException e) {
      return;
    }

    fail("No Exception");
  }

  public static class BeanWithPropertyWithoutTag {
    @Property(surroundWithTag = false)
    public BeanWithPublicFields BEAN1 = new BeanWithPublicFields();
    public int INT_V = 1;
  }
  public void testPropertyWithoutTag() throws Exception {
    final BeanWithPropertyWithoutTag bean = new BeanWithPropertyWithoutTag();

    doSerializerTest("<BeanWithPropertyWithoutTag><BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/></BeanWithPropertyWithoutTag>", bean);

    bean.INT_V = 2;
    bean.BEAN1.STRING_V = "junk";

    doSerializerTest("<BeanWithPropertyWithoutTag><BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"junk\"/></BeanWithPublicFields><option name=\"INT_V\" value=\"2\"/></BeanWithPropertyWithoutTag>", bean);
  }


  public static class BeanWithArrayWithoutAllsTag {
    @Property(surroundWithTag = false)
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutAllTags() throws Exception {
    final BeanWithArrayWithoutAllsTag bean = new BeanWithArrayWithoutAllsTag();

    doSerializerTest("<BeanWithArrayWithoutAllsTag><option name=\"INT_V\" value=\"1\"/><vvalue v=\"a\"/><vvalue v=\"b\"/></BeanWithArrayWithoutAllsTag>", bean);

    bean.INT_V = 2;
    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest("<BeanWithArrayWithoutAllsTag><option name=\"INT_V\" value=\"2\"/><vvalue v=\"1\"/><vvalue v=\"2\"/><vvalue v=\"3\"/></BeanWithArrayWithoutAllsTag>", bean);
  }

  public static class BeanWithArrayWithoutAllsTag2 {
    @Property(surroundWithTag = false)
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutAllTags2() throws Exception {
    final BeanWithArrayWithoutAllsTag2 bean = new BeanWithArrayWithoutAllsTag2();

    doSerializerTest("<BeanWithArrayWithoutAllsTag2><option name=\"INT_V\" value=\"1\"/><vvalue>a</vvalue><vvalue>b</vvalue></BeanWithArrayWithoutAllsTag2>", bean);

    bean.INT_V = 2;
    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest("<BeanWithArrayWithoutAllsTag2><option name=\"INT_V\" value=\"2\"/><vvalue>1</vvalue><vvalue>2</vvalue><vvalue>3</vvalue></BeanWithArrayWithoutAllsTag2>", bean);
  }

  public void testDeserializeFromFormattedXML() throws Exception {
    String xml = "<BeanWithArrayWithoutAllsTag>\n" + "  <option name=\"INT_V\" value=\"2\"/>\n" + "  <vvalue v=\"1\"/>\n" +
                 "  <vvalue v=\"2\"/>\n" + "  <vvalue v=\"3\"/>\n" + "</BeanWithArrayWithoutAllsTag>";

    final BeanWithArrayWithoutAllsTag bean =
      XmlSerializer.deserialize(DOMUtil.loadText(xml).getDocumentElement(), BeanWithArrayWithoutAllsTag.class);


    assertEquals(2, bean.INT_V);
    assertEquals("[1, 2, 3]", Arrays.asList(bean.V).toString());
  }


  public static class BeanWithPolymorphicArray {
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTypes = {BeanWithPublicFields.class, BeanWithPublicFieldsDescendant.class})
    public BeanWithPublicFields[] V = new BeanWithPublicFields[] {};
  }

  public void testPolymorphicArray() throws Exception {
    final BeanWithPolymorphicArray bean = new BeanWithPolymorphicArray();

    doSerializerTest("<BeanWithPolymorphicArray><option name=\"V\"><array/></option></BeanWithPolymorphicArray>", bean);

    bean.V = new BeanWithPublicFields[] {new BeanWithPublicFields(), new BeanWithPublicFieldsDescendant(), new BeanWithPublicFields()};

    doSerializerTest("<BeanWithPolymorphicArray><option name=\"V\"><array>" +
                     "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>" +
                     "<BeanWithPublicFieldsDescendant><option name=\"INT_V\" value=\"1\"/><option name=\"NEW_S\" value=\"foo\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFieldsDescendant>" +
                     "<BeanWithPublicFields><option name=\"INT_V\" value=\"1\"/><option name=\"STRING_V\" value=\"hello\"/></BeanWithPublicFields>" +
                     "</array></option></BeanWithPolymorphicArray>", bean);
  }


  public static class BeanWithPropertiesBoundToAttribute {
    @Attribute( "count")
    public int COUNT = 3;
    @Attribute("name")
    public String name = "James";
  }
  public void testBeanWithPrimitivePropertyBoundToAttribute() throws Exception {
    final BeanWithPropertiesBoundToAttribute bean = new BeanWithPropertiesBoundToAttribute();

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"James\"/>", bean);

    bean.COUNT = 10;
    bean.name = "Bond";

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"10\" name=\"Bond\"/>", bean);
  }


  public static class BeanWithPropertyFilter {
    @Property(
      filter = PropertyFilterTest.class
    )
    public String STRING_V = "hello";
  }
  public static class PropertyFilterTest implements SerializationFilter {
    public boolean accepts(Accessor accessor, Object bean) {
      return !accessor.read(bean).equals("skip");
    }
  }
  public void testPropertyFilter() throws Exception {
    BeanWithPropertyFilter bean = new BeanWithPropertyFilter();

    doSerializerTest(
      "<BeanWithPropertyFilter><option name=\"STRING_V\" value=\"hello\"/></BeanWithPropertyFilter>", bean);

    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithPropertyFilter><option name=\"STRING_V\" value=\"bye\"/></BeanWithPropertyFilter>", bean);

    bean.STRING_V = "skip";

    assertSerializer(bean, "<BeanWithPropertyFilter/>", "Serialization failure", null);
  }

  public static class BeanWithJDOMElement {
    public String STRING_V = "hello";
    @Tag("actions")
    public org.jdom.Element actions;
  }
  public void testJDOMElementField() throws Exception {


    final BeanWithJDOMElement bean = XmlSerializer.deserialize(DOMUtil.loadText(
      "<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>"
    ).getDocumentElement(), BeanWithJDOMElement.class);


    assertEquals("bye", bean.STRING_V);
    assertNotNull(bean.actions);
    assertEquals(2, bean.actions.getChildren("action").size());
  }

  public static class BeanWithJDOMElementArray {
    public String STRING_V = "hello";
    @Tag("actions")
    public org.jdom.Element[] actions;
  }
  public void testJDOMElementArrayField() throws Exception {


    final BeanWithJDOMElementArray bean = XmlSerializer.deserialize(DOMUtil.loadText(
      "<BeanWithJDOMElementArray><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions><actions><action/></actions></BeanWithJDOMElementArray>"
    ).getDocumentElement(), BeanWithJDOMElementArray.class);


    assertEquals("bye", bean.STRING_V);
    assertNotNull(bean.actions);
    assertEquals(2, bean.actions.length);
    assertEquals(2, bean.actions[0].getChildren().size());
    assertEquals(1, bean.actions[1].getChildren().size());
  }

  public static class BeanWithTextAnnotation {
    public int INT_V = 1;
    @Text
    public String STRING_V = "hello";
  }

  public void testTextAnnotation() throws Exception {
    BeanWithTextAnnotation bean = new BeanWithTextAnnotation();

    doSerializerTest(
      "<BeanWithTextAnnotation><option name=\"INT_V\" value=\"1\"/>hello</BeanWithTextAnnotation>", bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithTextAnnotation><option name=\"INT_V\" value=\"2\"/>bye</BeanWithTextAnnotation>", bean);
  }

  //---------------------------------------------------------------------------------------------------
  private void assertSerializer(Object bean, String expected, SerializationFilter filter)
    throws TransformerException, ParserConfigurationException {
    assertSerializer(bean, expected, "Serialization failure", filter);
  }

  private void doSerializerTest(String expectedText, Object bean)
    throws ParserConfigurationException, TransformerException, XmlSerializationException {
    Element element = assertSerializer(bean, expectedText, "Serialization failure", null);

    //test deserializer

    Object o = XmlSerializer.deserialize(element, bean.getClass());
    assertSerializer(o, expectedText, "Deserialization failure", null);
  }

  private Element assertSerializer(Object bean, String expectedText, String message, SerializationFilter filter)
    throws ParserConfigurationException, XmlSerializationException, TransformerException {
    Element element = serialize(bean, filter);

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

  private Element serialize(Object bean, SerializationFilter filter) throws ParserConfigurationException {
    Document document = DOMUtil.createDocument();

    Element element = XmlSerializer.serialize(bean, document, filter);
    document.appendChild(element);
    return element;
  }
}
