package com.intellij.lang.properties.xml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.indexing.FileContentImpl;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 7/25/11
 */
public class XmlPropertiesIndexTest extends LightPlatformCodeInsightFixtureTestCase {
  public XmlPropertiesIndexTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void testIndex() {
    final VirtualFile file = myFixture.configureByFile("foo.xml").getVirtualFile();
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(FileContentImpl.createByFile(file));

    assertEquals(3, map.size());
    assertEquals("bar", map.get(new XmlPropertiesIndex.Key("foo")));
    assertEquals("baz", map.get(new XmlPropertiesIndex.Key("fu")));
    assertTrue(map.containsKey(XmlPropertiesIndex.MARKER_KEY));
  }

  public void testSystemId() throws Exception {
    final VirtualFile file = myFixture.configureByFile("wrong.xml").getVirtualFile();
    Map<XmlPropertiesIndex.Key, String> map = new XmlPropertiesIndex().map(FileContentImpl.createByFile(file));

    assertEquals(0, map.size());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/xml/";
  }

}
