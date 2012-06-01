package com.intellij.xml;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlSmartCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public XmlSmartCompletionTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  public void testCompletion() throws Exception {
    doTest(new String[]{"testCompletion.xml", "test.xsd"}, "b");
  }

  public void testCompletionNext() throws Exception {
    doTest(new String[]{"testCompletionNext.xml", "test.xsd"}, "c");
  }

  public void testCompletion3() throws Exception {
    doTest(new String[]{"testCompletion3.xml", "test.xsd"}, "c", "d");
  }

  public void testServlet() throws Exception {
    doTest(new String[]{"Servlet.xml"}, "icon", "servlet-name");
  }

  public void testServletName() throws Exception {

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

  public void testPrefix() throws Exception {
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
    List<String> strings = ContainerUtil.map(elements, new Function<LookupElement, String>() {
      @Override
      public String fun(LookupElement lookupElement) {
        return lookupElement.getLookupString();
      }
    });
    assertEquals(Arrays.asList(items), strings);
  }


  @Override
  public void setUp() throws Exception {
    super.setUp();
    ExternalResourceManagerImpl.registerResourceTemporarily("http://java.sun.com/j2ee/dtds/web-app_2_3.dtd",
                                                            getTestDataPath() + "/web-app_2_3.dtd", getTestRootDisposable());
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
