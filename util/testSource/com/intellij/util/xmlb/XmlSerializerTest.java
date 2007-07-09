package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.annotations.*;
import junit.framework.TestCase;
import org.jdom.Element;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.*;
import java.util.Collection;

/**
 * @author mike
 */
public class XmlSerializerTest extends TestCase {
  private static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

  public static class EmptyBean {
  }

  public void testEmptyBeanSerialization() throws Exception {
    doSerializerTest("<EmptyBean />", new EmptyBean());
  }

  @Tag("Bean")
  public static class EmptyBeanWithCustomName {
  }

  public void testEmptyBeanSerializationWithCustomName() throws Exception {
    doSerializerTest("<Bean />", new EmptyBeanWithCustomName());
  }


  public static class BeanWithPublicFields implements Comparable<BeanWithPublicFields> {
    public int INT_V = 1;
    public String STRING_V = "hello";

    public BeanWithPublicFields(final int INT_V, final String STRING_V) {
      this.INT_V = INT_V;
      this.STRING_V = STRING_V;
    }

    public BeanWithPublicFields() {
    }

    public int compareTo(final BeanWithPublicFields o) {
      return STRING_V.compareTo(o.STRING_V);
    }
  }

  public void testPublicFieldSerialization() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPublicFields>", bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "  <option name=\"STRING_V\" value=\"bye\" />\n" +
      "</BeanWithPublicFields>", bean);
  }


  public static class BeanWithPublicFieldsDescendant extends BeanWithPublicFields {
    public String NEW_S = "foo";
  }

  public void testPublicFieldSerializationWithInheritance() throws Exception {
    BeanWithPublicFieldsDescendant bean = new BeanWithPublicFieldsDescendant();

    doSerializerTest(
      "<BeanWithPublicFieldsDescendant>\n" +
      "  <option name=\"NEW_S\" value=\"foo\" />\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPublicFieldsDescendant>",
      bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";
    bean.NEW_S = "bar";

    doSerializerTest(
      "<BeanWithPublicFieldsDescendant>\n" +
      "  <option name=\"NEW_S\" value=\"bar\" />\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "  <option name=\"STRING_V\" value=\"bye\" />\n" +
      "</BeanWithPublicFieldsDescendant>",
      bean);
  }

  public static class BeanWithSubBean {
    public EmptyBeanWithCustomName BEAN1 = new EmptyBeanWithCustomName();
    public BeanWithPublicFields BEAN2 = new BeanWithPublicFields();
  }

  public void testSubBeanSerialization() throws Exception {
    BeanWithSubBean bean = new BeanWithSubBean();
    doSerializerTest(
      "<BeanWithSubBean>\n" +
      "  <option name=\"BEAN1\">\n" +
      "    <Bean />\n" +
      "  </option>\n" +
      "  <option name=\"BEAN2\">\n" +
      "    <BeanWithPublicFields>\n" +
      "      <option name=\"INT_V\" value=\"1\" />\n" +
      "      <option name=\"STRING_V\" value=\"hello\" />\n" +
      "    </BeanWithPublicFields>\n" +
      "  </option>\n" +
      "</BeanWithSubBean>",
      bean);
    bean.BEAN2.INT_V = 2;
    bean.BEAN2.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithSubBean>\n" +
      "  <option name=\"BEAN1\">\n" +
      "    <Bean />\n" +
      "  </option>\n" +
      "  <option name=\"BEAN2\">\n" +
      "    <BeanWithPublicFields>\n" +
      "      <option name=\"INT_V\" value=\"2\" />\n" +
      "      <option name=\"STRING_V\" value=\"bye\" />\n" +
      "    </BeanWithPublicFields>\n" +
      "  </option>\n" +
      "</BeanWithSubBean>",
      bean);
  }

  public void testNullFieldValue() throws Exception {
    BeanWithPublicFields bean1 = new BeanWithPublicFields();

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPublicFields>",
      bean1);

    bean1.STRING_V = null;

    doSerializerTest(
      "<BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  <option name=\"STRING_V\" />\n" +
      "</BeanWithPublicFields>", bean1);

    BeanWithSubBean bean2 = new BeanWithSubBean();
    bean2.BEAN1 = null;
    bean2.BEAN2 = null;

    doSerializerTest(
      "<BeanWithSubBean>\n" +
      "  <option name=\"BEAN1\" />\n" +
      "  <option name=\"BEAN2\" />\n" +
      "</BeanWithSubBean>", bean2);

  }

  public static class BeanWithList {
    public List<String> VALUES = new ArrayList<String>(Arrays.asList("a", "b", "c"));
  }

  public void testListSerialization() throws Exception {
    BeanWithList bean = new BeanWithList();

    doSerializerTest(
      "<BeanWithList>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <list>\n" +
      "      <option value=\"a\" />\n" +
      "      <option value=\"b\" />\n" +
      "      <option value=\"c\" />\n" +
      "    </list>\n" +
      "  </option>\n" +
      "</BeanWithList>",
      bean);

    bean.VALUES = new ArrayList<String>(Arrays.asList("1", "2", "3"));

    doSerializerTest(
      "<BeanWithList>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <list>\n" +
      "      <option value=\"1\" />\n" +
      "      <option value=\"2\" />\n" +
      "      <option value=\"3\" />\n" +
      "    </list>\n" +
      "  </option>\n" +
      "</BeanWithList>",
      bean);
  }

  public static class BeanWithSet {
    public Set<String> VALUES = new LinkedHashSet<String>(Arrays.asList("a", "b", "w"));
  }

  public void testSetSerialization() throws Exception {
    BeanWithSet bean = new BeanWithSet();
    doSerializerTest(
      "<BeanWithSet>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <set>\n" +
      "      <option value=\"a\" />\n" +
      "      <option value=\"b\" />\n" +
      "      <option value=\"w\" />\n" +
      "    </set>\n" +
      "  </option>\n" +
      "</BeanWithSet>",
      bean);
    bean.VALUES = new LinkedHashSet<String>(Arrays.asList("1", "2", "3"));

    doSerializerTest(
      "<BeanWithSet>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <set>\n" +
      "      <option value=\"1\" />\n" +
      "      <option value=\"2\" />\n" +
      "      <option value=\"3\" />\n" +
      "    </set>\n" +
      "  </option>\n" +
      "</BeanWithSet>",
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
      "<BeanWithMap>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <map>\n" +
      "      <entry key=\"a\" value=\"1\" />\n" +
      "      <entry key=\"b\" value=\"2\" />\n" +
      "      <entry key=\"c\" value=\"3\" />\n" +
      "    </map>\n" +
      "  </option>\n" +
      "</BeanWithMap>",
      bean);
    bean.VALUES.clear();
    bean.VALUES.put("1", "a");
    bean.VALUES.put("2", "b");
    bean.VALUES.put("3", "c");

    doSerializerTest(
      "<BeanWithMap>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <map>\n" +
      "      <entry key=\"1\" value=\"a\" />\n" +
      "      <entry key=\"2\" value=\"b\" />\n" +
      "      <entry key=\"3\" value=\"c\" />\n" +
      "    </map>\n" + "  </option>\n" +
      "</BeanWithMap>",
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
      "<BeanWithMapWithAnnotations>\n" +
      "  <option name=\"a\" value=\"1\" />\n" +
      "  <option name=\"b\" value=\"2\" />\n" +
      "  <option name=\"c\" value=\"3\" />\n" +
      "</BeanWithMapWithAnnotations>",
      bean);
    bean.VALUES.clear();
    bean.VALUES.put("1", "a");
    bean.VALUES.put("2", "b");
    bean.VALUES.put("3", "c");

    doSerializerTest(
      "<BeanWithMapWithAnnotations>\n" +
      "  <option name=\"1\" value=\"a\" />\n" +
      "  <option name=\"2\" value=\"b\" />\n" +
      "  <option name=\"3\" value=\"c\" />\n" +
      "</BeanWithMapWithAnnotations>",
      bean);
  }


  public static class BeanWithMapWithBeanValue {
    public Map<String, BeanWithProperty> VALUES = new HashMap<String, BeanWithProperty>();

  }
  public void testMapWithBeanValue() throws Exception {
    BeanWithMapWithBeanValue bean = new BeanWithMapWithBeanValue();

    bean.VALUES.put("a", new BeanWithProperty("James"));
    bean.VALUES.put("b", new BeanWithProperty("Bond"));
    bean.VALUES.put("c", new BeanWithProperty("Bill"));

    doSerializerTest(
      "<BeanWithMapWithBeanValue>\n" +
      "  <option name=\"VALUES\">\n" +
      "    <map>\n" +
      "      <entry key=\"a\">\n" +
      "        <value>\n" +
      "          <BeanWithProperty>\n" +
      "            <option name=\"name\" value=\"James\" />\n" +
      "          </BeanWithProperty>\n" +
      "        </value>\n" +
      "      </entry>\n" +
      "      <entry key=\"b\">\n" +
      "        <value>\n" +
      "          <BeanWithProperty>\n" +
      "            <option name=\"name\" value=\"Bond\" />\n" +
      "          </BeanWithProperty>\n" +
      "        </value>\n" +
      "      </entry>\n" +
      "      <entry key=\"c\">\n" +
      "        <value>\n" +
      "          <BeanWithProperty>\n" +
      "            <option name=\"name\" value=\"Bill\" />\n" +
      "          </BeanWithProperty>\n" +
      "        </value>\n" +
      "      </entry>\n" +
      "    </map>\n" +
      "  </option>\n" +
      "</BeanWithMapWithBeanValue>",
      bean);
  }
  public static class BeanWithProperty {                                
    private String name = "James";

    public BeanWithProperty() {
    }

    public BeanWithProperty(final String name) {
      this.name = name;
    }

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
      "<BeanWithProperty>\n" +
      "  <option name=\"name\" value=\"James\" />\n" +
      "</BeanWithProperty>",
      bean);

    bean.setName("Bond");

    doSerializerTest(
      "<BeanWithProperty>\n" +
      "  <option name=\"name\" value=\"Bond\" />\n" +
      "</BeanWithProperty>", bean);
  }

  public static class BeanWithFieldWithTagAnnotation {
    @Tag("name")
    public String STRING_V = "hello";
  }

  public void testFieldWithTagAnnotation() throws Exception {
    BeanWithFieldWithTagAnnotation bean = new BeanWithFieldWithTagAnnotation();

    doSerializerTest(
      "<BeanWithFieldWithTagAnnotation>\n" +
      "  <name>hello</name>\n" +
      "</BeanWithFieldWithTagAnnotation>",
      bean);

    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithFieldWithTagAnnotation>\n" +
      "  <name>bye</name>\n" +
      "</BeanWithFieldWithTagAnnotation>", bean);
  }

  public void testShuffledDeserialize() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    bean.INT_V = 987;
    bean.STRING_V = "1234";

    Element element = serialize(bean, null);

    Element node = (Element)element.getChildren().get(0);
    element.removeContent(node);
    element.addContent(node);

    bean = XmlSerializer.deserialize(element, bean.getClass());
    assert bean != null;
    assertEquals(987, bean.INT_V);
    assertEquals("1234", bean.STRING_V);
  }

  public void testFilterSerializer() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    assertSerializer(bean,
                     "<BeanWithPublicFields>\n" +
                     "  <option name=\"INT_V\" value=\"1\" />\n" +
                     "</BeanWithPublicFields>",
                     new SerializationFilter() {
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
    doSerializerTest(
      "<BeanWithArray>\n" +
      "  <option name=\"ARRAY_V\">\n" +
      "    <array>\n" +
      "      <option value=\"a\" />\n" +
      "      <option value=\"b\" />\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithArray>", bean);

    bean.ARRAY_V = new String[] {"1", "2", "3"};
    doSerializerTest(
      "<BeanWithArray>\n" +
      "  <option name=\"ARRAY_V\">\n" +
      "    <array>\n" +
      "      <option value=\"1\" />\n" +
      "      <option value=\"2\" />\n" +
      "      <option value=\"3\" />\n" +
      "    </array>\n" + "  </option>\n" +
      "</BeanWithArray>", bean);
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
    doSerializerTest("<BeanWithTransient />", bean);
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

    doSerializerTest(
      "<BeanWithArrayWithElementTagName>\n" +
      "  <option name=\"V\">\n" +
      "    <array>\n" +
      "      <vvalue v=\"a\" />\n" +
      "      <vvalue v=\"b\" />\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithArrayWithElementTagName>",
      bean);

    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithElementTagName>\n" +
      "  <option name=\"V\">\n" +
      "    <array>\n" +
      "      <vvalue v=\"1\" />\n" +
      "      <vvalue v=\"2\" />\n" +
      "      <vvalue v=\"3\" />\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithArrayWithElementTagName>", bean);
  }

  public static class BeanWithArrayWithoutTag {
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutTag() throws Exception {
    final BeanWithArrayWithoutTag bean = new BeanWithArrayWithoutTag();

    doSerializerTest(
      "<BeanWithArrayWithoutTag>\n" +
      "  <option name=\"V\">\n" +
      "    <vvalue v=\"a\" />\n" +
      "    <vvalue v=\"b\" />\n" +
      "  </option>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutTag>", bean);

    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithoutTag>\n" +
      "  <option name=\"V\">\n" +
      "    <vvalue v=\"1\" />\n" +
      "    <vvalue v=\"2\" />\n" +
      "    <vvalue v=\"3\" />\n" +
      "  </option>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutTag>", bean);
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

    doSerializerTest(
      "<BeanWithPropertyWithoutTag>\n" +
      "  <BeanWithPublicFields>\n" +
      "    <option name=\"INT_V\" value=\"1\" />\n" +
      "    <option name=\"STRING_V\" value=\"hello\" />\n" +
      "  </BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithPropertyWithoutTag>",
      bean);

    bean.INT_V = 2;
    bean.BEAN1.STRING_V = "junk";

    doSerializerTest(
      "<BeanWithPropertyWithoutTag>\n" +
      "  <BeanWithPublicFields>\n" +
      "    <option name=\"INT_V\" value=\"1\" />\n" +
      "    <option name=\"STRING_V\" value=\"junk\" />\n" +
      "  </BeanWithPublicFields>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "</BeanWithPropertyWithoutTag>", bean);
  }


  public static class BeanWithArrayWithoutAllsTag {
    @Property(surroundWithTag = false)
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutAllTags() throws Exception {
    final BeanWithArrayWithoutAllsTag bean = new BeanWithArrayWithoutAllsTag();

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag>\n" +
      "  <vvalue v=\"a\" />\n" +
      "  <vvalue v=\"b\" />\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutAllsTag>", bean);

    bean.INT_V = 2;
    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag>\n" +
      "  <vvalue v=\"1\" />\n" +
      "  <vvalue v=\"2\" />\n" +
      "  <vvalue v=\"3\" />\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "</BeanWithArrayWithoutAllsTag>", bean);
  }

  public static class BeanWithArrayWithoutAllsTag2 {
    @Property(surroundWithTag = false)
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vvalue", elementValueAttribute = "", surroundWithTag = false)
    public String[] V = new String[]{"a", "b"};
    public int INT_V = 1;
  }
  public void testArrayWithoutAllTags2() throws Exception {
    final BeanWithArrayWithoutAllsTag2 bean = new BeanWithArrayWithoutAllsTag2();

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag2>\n" +
      "  <vvalue>a</vvalue>\n" +
      "  <vvalue>b</vvalue>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "</BeanWithArrayWithoutAllsTag2>", bean);

    bean.INT_V = 2;
    bean.V = new String[] {"1", "2", "3"};

    doSerializerTest(
      "<BeanWithArrayWithoutAllsTag2>\n" +
      "  <vvalue>1</vvalue>\n" +
      "  <vvalue>2</vvalue>\n" +
      "  <vvalue>3</vvalue>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "</BeanWithArrayWithoutAllsTag2>", bean);
  }

  public void testDeserializeFromFormattedXML() throws Exception {
    String xml = "<BeanWithArrayWithoutAllsTag>\n" + "  <option name=\"INT_V\" value=\"2\"/>\n" + "  <vvalue v=\"1\"/>\n" +
                 "  <vvalue v=\"2\"/>\n" + "  <vvalue v=\"3\"/>\n" + "</BeanWithArrayWithoutAllsTag>";

    final BeanWithArrayWithoutAllsTag bean =
      XmlSerializer.deserialize(JDOMUtil.loadDocument(xml).getRootElement(), BeanWithArrayWithoutAllsTag.class);


    assertEquals(2, bean.INT_V);
    assertEquals("[1, 2, 3]", Arrays.asList(bean.V).toString());
  }


  public static class BeanWithPolymorphicArray {
    @com.intellij.util.xmlb.annotations.AbstractCollection(elementTypes = {BeanWithPublicFields.class, BeanWithPublicFieldsDescendant.class})
    public BeanWithPublicFields[] V = new BeanWithPublicFields[] {};
  }

  public void testPolymorphicArray() throws Exception {
    final BeanWithPolymorphicArray bean = new BeanWithPolymorphicArray();

    doSerializerTest(
      "<BeanWithPolymorphicArray>\n" +
      "  <option name=\"V\">\n" +
      "    <array />\n" +
      "  </option>\n" +
      "</BeanWithPolymorphicArray>", bean);

    bean.V = new BeanWithPublicFields[] {new BeanWithPublicFields(), new BeanWithPublicFieldsDescendant(), new BeanWithPublicFields()};

    doSerializerTest(
      "<BeanWithPolymorphicArray>\n" +
      "  <option name=\"V\">\n" +
      "    <array>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"hello\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithPublicFieldsDescendant>\n" +
      "        <option name=\"NEW_S\" value=\"foo\" />\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"hello\" />\n" +
      "      </BeanWithPublicFieldsDescendant>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"hello\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "    </array>\n" +
      "  </option>\n" +
      "</BeanWithPolymorphicArray>", bean);
  }


  public static class BeanWithPropertiesBoundToAttribute {
    @Attribute( "count")
    public int COUNT = 3;
    @Attribute("name")
    public String name = "James";
  }
  public void testBeanWithPrimitivePropertyBoundToAttribute() throws Exception {
    final BeanWithPropertiesBoundToAttribute bean = new BeanWithPropertiesBoundToAttribute();

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"James\" />", bean);

    bean.COUNT = 10;
    bean.name = "Bond";

    doSerializerTest("<BeanWithPropertiesBoundToAttribute count=\"10\" name=\"Bond\" />", bean);
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
      "<BeanWithPropertyFilter>\n" +
      "  <option name=\"STRING_V\" value=\"hello\" />\n" +
      "</BeanWithPropertyFilter>", bean);

    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithPropertyFilter>\n" +
      "  <option name=\"STRING_V\" value=\"bye\" />\n" +
      "</BeanWithPropertyFilter>", bean);

    bean.STRING_V = "skip";

    assertSerializer(bean, "<BeanWithPropertyFilter />", "Serialization failure", null);
  }

  public static class BeanWithJDOMElement {
    public String STRING_V = "hello";
    @Tag("actions")
    public org.jdom.Element actions;
  }
  public void testJDOMElementField() throws Exception {


    final BeanWithJDOMElement bean = XmlSerializer.deserialize(JDOMUtil.loadDocument(
      "<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>"
    ).getRootElement(), BeanWithJDOMElement.class);


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


    final BeanWithJDOMElementArray bean = XmlSerializer.deserialize(JDOMUtil.loadDocument(
      "<BeanWithJDOMElementArray><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions><actions><action/></actions></BeanWithJDOMElementArray>"
    ).getRootElement(), BeanWithJDOMElementArray.class);


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

    public BeanWithTextAnnotation(final int INT_V, final String STRING_V) {
      this.INT_V = INT_V;
      this.STRING_V = STRING_V;
    }

    public BeanWithTextAnnotation() {
    }
  }

  public void testTextAnnotation() throws Exception {
    BeanWithTextAnnotation bean = new BeanWithTextAnnotation();

    doSerializerTest(
      "<BeanWithTextAnnotation>\n" +
      "  <option name=\"INT_V\" value=\"1\" />\n" +
      "  hello\n" +
      "</BeanWithTextAnnotation>", bean);

    bean.INT_V = 2;
    bean.STRING_V = "bye";

    doSerializerTest(
      "<BeanWithTextAnnotation>\n" +
      "  <option name=\"INT_V\" value=\"2\" />\n" +
      "  bye\n" +
      "</BeanWithTextAnnotation>", bean);
  }


  public static class BeanWithSetKeysInMap {
    public Map<Collection<String>, String> myMap = new HashMap<Collection<String>, String>();
  }

  public void testSetKeysInMap() throws Exception {
    final BeanWithSetKeysInMap bean = new BeanWithSetKeysInMap();
    bean.myMap.put(new HashSet<String>(Arrays.asList("1", "2", "3")), "numbers");
    bean.myMap.put(new HashSet<String>(Arrays.asList("a", "b", "c")), "letters");

    BeanWithSetKeysInMap bb = (BeanWithSetKeysInMap)doSerializerTest(
      "<BeanWithSetKeysInMap>\n" +
      "  <option name=\"myMap\">\n" +
      "    <map>\n" +
      "      <entry value=\"letters\">\n" +
      "        <key>\n" +
      "          <set>\n" +
      "            <option value=\"a\" />\n" +
      "            <option value=\"b\" />\n" +
      "            <option value=\"c\" />\n" +
      "          </set>\n" +
      "        </key>\n" +
      "      </entry>\n" +
      "      <entry value=\"numbers\">\n" +
      "        <key>\n" +
      "          <set>\n" +
      "            <option value=\"1\" />\n" +
      "            <option value=\"2\" />\n" +
      "            <option value=\"3\" />\n" +
      "          </set>\n" +
      "        </key>\n" +
      "      </entry>\n" +
      "    </map>\n" +
      "  </option>\n" +
      "</BeanWithSetKeysInMap>",
      bean);

    for (Collection<String> collection : bb.myMap.keySet()) {
      assertTrue(collection instanceof Set);
    }
  }


  public void testDeserializeInto() throws Exception {
    BeanWithPublicFields bean = new BeanWithPublicFields();
    bean.STRING_V = "zzz";

    String xml = "<BeanWithPublicFields><option name=\"INT_V\" value=\"999\"/></BeanWithPublicFields>";
    XmlSerializer.deserializeInto(bean, JDOMUtil.loadDocument(xml).getRootElement());

    assertEquals(999, bean.INT_V);
    assertEquals("zzz", bean.STRING_V);
  }

  public static class BeanWithMapWithoutSurround {
    @Tag("map")
    @MapAnnotation(surroundWithTag = false, entryTagName = "pair", surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<BeanWithPublicFields, BeanWithTextAnnotation> MAP = new HashMap<BeanWithPublicFields, BeanWithTextAnnotation>();
  }

  public void testMapWithNotSurroundingKeyAndValue() throws Exception {
    BeanWithMapWithoutSurround bean = new BeanWithMapWithoutSurround();

    bean.MAP.put(new BeanWithPublicFields(1, "a"), new BeanWithTextAnnotation(2, "b"));
    bean.MAP.put(new BeanWithPublicFields(3, "c"), new BeanWithTextAnnotation(4, "d"));
    bean.MAP.put(new BeanWithPublicFields(5, "e"), new BeanWithTextAnnotation(6, "f"));

    doSerializerTest(
      "<BeanWithMapWithoutSurround>\n" +
      "  <map>\n" +
      "    <pair>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"1\" />\n" +
      "        <option name=\"STRING_V\" value=\"a\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithTextAnnotation>\n" +
      "        <option name=\"INT_V\" value=\"2\" />\n" +
      "        b\n" +
      "      </BeanWithTextAnnotation>\n" +
      "    </pair>\n" + "    <pair>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"3\" />\n" +
      "        <option name=\"STRING_V\" value=\"c\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithTextAnnotation>\n" +
      "        <option name=\"INT_V\" value=\"4\" />\n" +
      "        d\n" +
      "      </BeanWithTextAnnotation>\n" +
      "    </pair>\n" +
      "    <pair>\n" +
      "      <BeanWithPublicFields>\n" +
      "        <option name=\"INT_V\" value=\"5\" />\n" +
      "        <option name=\"STRING_V\" value=\"e\" />\n" +
      "      </BeanWithPublicFields>\n" +
      "      <BeanWithTextAnnotation>\n" +
      "        <option name=\"INT_V\" value=\"6\" />\n" +
      "        f\n" +
      "      </BeanWithTextAnnotation>\n" +
      "    </pair>\n" +
      "  </map>\n" +
      "</BeanWithMapWithoutSurround>",
      bean);
  }


  //---------------------------------------------------------------------------------------------------
  private void assertSerializer(Object bean, String expected, SerializationFilter filter)
    throws TransformerException, ParserConfigurationException, IOException {
    assertSerializer(bean, expected, "Serialization failure", filter);
  }

  private Object doSerializerTest(String expectedText, Object bean)
    throws ParserConfigurationException, TransformerException, XmlSerializationException, IOException {
    Element element = assertSerializer(bean, expectedText, "Serialization failure", null);

    //test deserializer

    Object o = XmlSerializer.deserialize(element, bean.getClass());
    assertSerializer(o, expectedText, "Deserialization failure", null);
    return o;
  }

  private Element assertSerializer(Object bean, String expectedText, String message, SerializationFilter filter)
    throws ParserConfigurationException, XmlSerializationException, TransformerException, IOException {
    Element element = serialize(bean, filter);


    String actualString = JDOMUtil.writeElement(element, "\n").trim();

    if (!expectedText.startsWith(XML_PREFIX)) {
      if (actualString.startsWith(XML_PREFIX)) actualString = actualString.substring(XML_PREFIX.length()).trim();
    }

    assertEquals(message, expectedText, actualString);

    return element;
  }

  private Element serialize(Object bean, SerializationFilter filter) throws ParserConfigurationException {
    return XmlSerializer.serialize(bean, filter);
  }
}
