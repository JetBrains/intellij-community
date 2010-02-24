package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.lang.properties.TrailingSpacesInPropertyInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;

public class TrailingSpacesInPropertyInspectionTest extends LightDaemonAnalyzerTestCase {

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new TrailingSpacesInPropertyInspection()};
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData";
  }

  public void testSimple() throws Exception{
    doTest("/propertiesFile/highlighting/trailingSpaces.properties",true,false);
  }
}
