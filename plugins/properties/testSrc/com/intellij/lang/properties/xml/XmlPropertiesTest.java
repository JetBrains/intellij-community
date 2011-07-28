package com.intellij.lang.properties.xml;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestCase;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 7/26/11
 */
public class XmlPropertiesTest extends LightPlatformCodeInsightTestCase {

  public void testXmlProperties() throws Exception {
    configureByFile("foo.xml");
    List<PropertiesFile> files = PropertiesReferenceManager.getInstance(getProject()).findPropertiesFiles(getModule(), "foo");
    assertEquals(1, files.size());
    PropertiesFile file = files.get(0);
    assertEquals(1, file.findPropertiesByKey("foo").size());

    List<IProperty> properties = PropertiesUtil.findPropertiesByKey(getProject(), "foo");
    assertEquals(1, properties.size());
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/xml/";
  }

  public XmlPropertiesTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }
}
