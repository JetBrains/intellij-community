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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlSmartCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testCompletion() {
    doTest(new String[]{"testCompletion.xml", "test.xsd"}, "b");
  }

  public void testCompletionNext() {
    doTest(new String[]{"testCompletionNext.xml", "test.xsd"}, "c");
  }

  public void testCompletion3() {
    doTest(new String[]{"testCompletion3.xml", "test.xsd"}, "c", "d");
  }

  public void testServlet() {
    doTest(new String[]{"Servlet.xml"}, "icon", "servlet-name");
  }

  public void testServletName() {

    doForText("<!DOCTYPE web-app\n" +
              "        PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\"\n" +
              "        \"http://java.sun.com/j2ee/dtds/web-app_2_3.dtd\">\n" +
              "<web-app>\n" +
              "\n" +
              "    <servlet>\n" +
              "        <s<caret>\n" +
              "    </servlet>\n" +
              "</web-app>",

              "<!DOCTYPE web-app\n" +
              "        PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\"\n" +
              "        \"http://java.sun.com/j2ee/dtds/web-app_2_3.dtd\">\n" +
              "<web-app>\n" +
              "\n" +
              "    <servlet>\n" +
              "        <servlet-name\n" +
              "    </servlet>\n" +
              "</web-app>");
  }

  public void testPrefix() {
    doForText("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
              "    <ann<caret>\n" +
              "</xs:schema>",

              "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
              "    <xs:annotation\n" +
              "</xs:schema>");
  }

  private void doForText(String before, String after) {
    myFixture.configureByText("a.xml", before);
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResult(after);
  }

  private void doTest(String[] files, String... items) {
    myFixture.configureByFiles(files);
    LookupElement[] elements;
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false;
      elements = myFixture.complete(CompletionType.SMART);
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = true;
    }
    assert elements != null;
    List<String> strings = ContainerUtil.map(elements, LookupElement::getLookupString);
    assertEquals(Arrays.asList(items), strings);
  }


  @Override
  public void setUp() throws Exception {
    super.setUp();
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://java.sun.com/j2ee/dtds/web-app_2_3.dtd",
                                                              getTestDataPath() + "/web-app_2_3.dtd", myFixture.getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/smartCompletion";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + getBasePath();
  }

}
