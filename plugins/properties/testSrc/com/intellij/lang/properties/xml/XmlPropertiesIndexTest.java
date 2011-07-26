package com.intellij.lang.properties.xml;

import com.intellij.util.indexing.FileContent;
import junit.framework.TestCase;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 7/25/11
 */
public class XmlPropertiesIndexTest extends TestCase {

  public void testIndex() throws Exception {
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(new FileContent(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                                            "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n" +
                                                                                            "<properties>\n" +
                                                                                            "<comment>Hi</comment>\n" +
                                                                                            "<entry key=\"foo\">bar</entry>\n" +
                                                                                            "<entry key=\"fu\">baz</entry>\n" +
                                                                                            "</properties>").getBytes()));

    assertEquals(3, map.size());
    assertEquals("bar", map.get(new XmlPropertiesIndex.Key("foo")));
    assertEquals("baz", map.get(new XmlPropertiesIndex.Key("fu")));
    assertTrue(map.containsKey(XmlPropertiesIndex.MARKER_KEY));
  }

  public void testSystemId() throws Exception {
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(new FileContent(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                                                            "<!DOCTYPE properties SYSTEM \"unknown\">\n" +
                                                                                            "<properties>\n" +
                                                                                            "<comment>Hi</comment>\n" +
                                                                                            "<entry key=\"foo\">bar</entry>\n" +
                                                                                            "<entry key=\"fu\">baz</entry>\n" +
                                                                                            "</properties>").getBytes()));

    assertEquals(0, map.size());
  }
}
