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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlSmartCompletionTest extends BasePlatformTestCase {

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

    doForText("""
                <!DOCTYPE web-app
                        PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
                        "http://java.sun.com/j2ee/dtds/web-app_2_3.dtd">
                <web-app>

                    <servlet>
                        <s<caret>
                    </servlet>
                </web-app>""",

              """
                <!DOCTYPE web-app
                        PUBLIC "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
                        "http://java.sun.com/j2ee/dtds/web-app_2_3.dtd">
                <web-app>

                    <servlet>
                        <servlet-name
                    </servlet>
                </web-app>""");
  }

  public void testPrefix() {
    doForText("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <ann<caret>
                </xs:schema>""",

              """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:annotation
                </xs:schema>""");
  }

  public void testInvalidXmlException() {
    doForText("""
                <schema xmlns="http://www.w3.org/2001/XMLSchema">
                  <element name="foo"><<caret></element>>
                  </element>
                </schema>""",

              """
                <schema xmlns="http://www.w3.org/2001/XMLSchema">
                  <element name="foo"><</element>>
                  </element>
                </schema>""");
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
