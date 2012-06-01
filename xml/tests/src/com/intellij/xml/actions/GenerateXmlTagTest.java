package com.intellij.xml.actions;

import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public class GenerateXmlTagTest extends LightPlatformCodeInsightFixtureTestCase {

  public GenerateXmlTagTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  public void testGenerate() throws Exception {
    myFixture.configureByFile("web-app_2_5.xsd");
    doTest("generate.xml", "security-constraint");
  }

  public void testGenerateEmpty() throws Exception {
    myFixture.configureByFile("javaee_5.xsd");
    myFixture.configureByFile("web-app_2_5.xsd");
    doTest("generateEmpty.xml", "distributable");
  }

  public void testGenerateDTD() throws Exception {
    doTest("generateDTD.xml", "b");
  }

  public void testGenerateDTDComplex() throws Exception {
    doTest("generateDTDComplex.xml", "b");
  }

  public void testSpring() throws Exception {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("spring.xml", "bean");
  }

  public void testSpringAtCaret() throws Exception {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("springAtCaret.xml", "bean");
  }

  public void testSpringAfterBean() throws Exception {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("springAfterBean.xml", "bean");
  }

  public void testSpringAlias() throws Exception {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("springAlias.xml", "alias");
  }

  public void testInitParam() throws Exception {
    doTest("initParam.xml", "context-param");
  }

  private void doTest(String file, String tagName) {
    GenerateXmlTagAction.TEST_THREAD_LOCAL.set(tagName);
    CodeInsightTestUtil.doActionTest(new GenerateXmlTagAction(), file, myFixture);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ExternalResourceManagerImpl.registerResourceTemporarily("http://java.sun.com/j2ee/dtds/web-app_2_3.dtd",
                                                            getTestDataPath() + "/web-app_2_3.dtd", getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/generateTag";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + getBasePath();
  }
}
