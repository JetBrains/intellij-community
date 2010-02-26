package com.intellij.lang.properties;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

public class TrailingSpacesInPropertyInspectionTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder();

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setTestDataPath(PluginPathManager.getPluginHomePath("properties") + "/testData");
    myFixture.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  public void testSimple() throws Exception{
    myFixture.enableInspections(new TrailingSpacesInPropertyInspection());
    VirtualFile file = myFixture.copyFileToProject("/propertiesFile/highlighting/trailingSpaces.properties");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, true);
  }
}