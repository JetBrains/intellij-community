/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.actions;

import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public class GenerateXmlTagTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testGenerate() {
    myFixture.configureByFile("web-app_2_5.xsd");
    doTest("generate.xml", "security-constraint");
  }

  public void testGenerateEmpty() {
    myFixture.configureByFile("javaee_5.xsd");
    myFixture.configureByFile("web-app_2_5.xsd");
    doTest("generateEmpty.xml", "distributable");
  }

  public void testGenerateDTD() {
    doTest("generateDTD.xml", "b");
  }

  public void testGenerateDTDComplex() {
    doTest("generateDTDComplex.xml", "b");
  }

  public void testSpring() {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("spring.xml", "bean");
  }

  public void testSpringAtCaret() {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("springAtCaret.xml", "bean");
  }

  public void testSpringAfterBean() {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("springAfterBean.xml", "bean");
  }

  public void testSpringAlias() {
    myFixture.configureByFile("spring-beans_3.0.xsd");
    doTest("springAlias.xml", "alias");
  }

  public void testInitParam() {
    doTest("initParam.xml", "context-param");
  }

  public void testInTagName() {
    doTest("try_to_generate_in_tag_name.xml", "context-param");
  }

  private void doTest(String file, String tagName) {
    GenerateXmlTagAction.TEST_THREAD_LOCAL.set(tagName);
    CodeInsightTestUtil.doActionTest(new GenerateXmlTagAction(), file, myFixture);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/j2ee/dtds/web-app_2_3.dtd",
                                                              getTestDataPath() + "/web-app_2_3.dtd", myFixture.getTestRootDisposable());
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
