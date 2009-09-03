package com.intellij.lang.properties;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.ParsingTestCase;

/**
 * @author max
 */
public class PropertiesParsingTest extends ParsingTestCase {
  public PropertiesParsingTest() {
    super("", "properties");
  }

  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/propertiesFile";
  }

  public void testProp1() throws Exception { doTest(true); }
  public void testComments() throws Exception { doTest(true); }
}
