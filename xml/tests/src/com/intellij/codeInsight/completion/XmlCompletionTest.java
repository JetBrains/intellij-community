// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.util.XmlUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Mossienko
 */
@SuppressWarnings("ConstantConditions")
@TestApplication
public class XmlCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/xml/tests/testData/completion/");

  private String myOldDoctype;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    addResource("http://www.springframework.org/schema/beans",
                myFixture.getTestDataPath() + "/spring-beans-2.0.xsd");
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(myFixture.getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, myFixture.getProject());
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, myFixture.getProject());
  }

  private void addResource(final String url, final String location) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final ExternalResourceManager manager = ExternalResourceManager.getInstance();
    final String old = manager.getResourceLocation(url, (String)null);
    if (old != null &&
        !old.equals(url) //strange hack: ERM returns url as location sometimes
      ) {
      return;
    }

    ExternalResourceManagerExBase.registerResourceTemporarily(url, location, myFixture.getTestRootDisposable());
  }

  @Test
  public void testCompleteWithAnyInSchema() {
    String location = "29.xsd";
    addResource("aaa",location);

    configureByFiles("29.xml", location);
    complete();
    checkResultByFile("29_after.xml");
  }

  private void checkResultByFile(String s) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.checkResultByFile(s);
  }

  private LookupElement[] complete() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.completeBasic();
  }

  private void configureByFiles(String... files) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(files);
  }

  @Test
  public void testCompleteWithAnyInSchema2() {
    configureByFiles("32.xml", "32.xsd", "32_2.xsd");
    complete();
    checkResultByFile("/32_after.xml");

    configureByFiles("32_2.xml", "32.xsd", "32_2.xsd");
    complete();
    checkResultByFile("32_2_after.xml");
  }

  @Test
  public void testCompleteWithAnyInSchema3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addResource("http://www.springframework.org/schema/tx",
                myFixture.getTestDataPath() + "/spring-tx-2.0.xsd");
    addResource("http://www.springframework.org/schema/util",
                myFixture.getTestDataPath() + "/spring-util-2.0.xsd");

    configureByFiles("36.xml");
    complete();
    checkResultByFile("36_after.xml");

    configureByFiles("36_2.xml");
    complete();
    checkResultByFile("36_2_after.xml");

    configureByFiles("36_3.xml");
    complete();
    myFixture.type('\n');
    checkResultByFile("36_3_after.xml");
  }

  @Test
  public void testXmlNonQualifiedElementCompletion() {
    String location = "25.xsd";
    String url = "http://www.dummy-temp-address";
    addResource(url,location);

    configureByFiles("25.xml",
                     location);
    complete();
    checkResultByFile("25_after.xml");
  }

  @Test
  public void testXmlCompletion() {
    String location = "xslt.xsd";
    String location2 = "xhtml1-strict.xsd";

    String url = "http://www.w3.org/1999/XSL/Transform";
    addResource(url, location);
    String url2 = "http://www.w3.org/1999/xhtml";
    addResource(url2, location2);

    configureByFiles("10.xml",
                     location,
                     location2);
    complete();
    checkResultByFile("/10_after.xml");

    configureByFiles("11.xml",
                     location,
                     location2);
    complete();
    checkResultByFile("/11_after.xml");
  }

  @Test
  public void testXmlCompletionWhenTagsWithSemicolon() {
    configureByFiles("XmlCompletionWhenTagsWithSemicolon.xml", "XmlCompletionWhenTagsWithSemicolon.dtd");
    complete();
    checkResultByFile("XmlCompletionWhenTagsWithSemicolon_after.xml");
  }

  @Test
  public void testCompleteTagWithXsiTypeInParent() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    String url = "urn:test";
    doCompletionTest("xml", url, testName + ".xsd");
  }

  @Test
  public void testAttributesTemplateFinishWithSpace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());

    configureByFile(myFixture.getTestName(false) + ".xml");
    type('b');
    type('e');
    type('a');
    type('n');
    type(' ');
    checkResultByFile(myFixture.getTestName(false) + "_after.xml");
  }

  @Test
  public void testCloseTagWithRequiredAndOptionalAttributes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    String dtd = """
      <!DOCTYPE methods [
        <!ELEMENT methods (include|exclude|single)*>
        <!ELEMENT include ANY>
        <!ATTLIST include name CDATA #REQUIRED description CDATA #IMPLIED>
        <!ELEMENT exclude ANY>
        <!ATTLIST exclude name CDATA #REQUIRED>
        <!ELEMENT single EMPTY>
        <!ATTLIST single name CDATA #REQUIRED description CDATA #IMPLIED>
      ]>
      """;
    doTestTagWithRequiredAttribute(dtd, "inc", "<include name=\"foo\"<caret>></include>");
    doTestTagWithRequiredAttribute(dtd, "exc", "<exclude name=\"foo\"><caret></exclude>");
    doTestTagWithRequiredAttribute(dtd, "sin", "<single name=\"foo\"<caret>/>");
  }

  private void doTestTagWithRequiredAttribute(String dtd, String prefix, String expectedTag) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", dtd + "<methods><" + prefix + "<caret></methods>");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.completeBasic();
      myFixture.type("foo");
      TemplateManagerImpl.getTemplateState(myFixture.getEditor()).nextTab();
      myFixture.checkResult(dtd + "<methods>" + expectedTag + "</methods>");
    });
  }

  private void configureByFile(String s) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(s);
    myFixture.completeBasic();
  }

  @Test
  public void testNoAttributesTemplateFinishWithSpace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());

    configureByFile(myFixture.getTestName(false) + ".xml");
    type('d');
    type('e');
    type(' ');
    checkResultByFile(myFixture.getTestName(false) + "_after.xml");
  }

  private void type(char c) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.type(c);
  }

  private void doCompletionTest(final String ext, final String url, final String location) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    addResource(url, location);

    configureByFiles(testName + "." + ext,
                     location);
    complete();
    checkResultByFile(testName + "_after." + ext);
  }

  @Test
  public void testSingleCompletionVariantAtRootTag() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addResource("http://www.springframework.org/dtd/spring-beans.dtd", myFixture.getTestDataPath() + "/spring-beans-2.0.dtd");

    basicDoTest("");
  }

  @Test
  public void testDtdCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String baseTestFileName = myFixture.getTestName(false);
    configureByFile(baseTestFileName + ".dtd");
    selectItem(myFixture.getLookupElements()[0], (char)0);
    checkResultByFile(baseTestFileName + "_after.dtd");

    doCompletionTest(baseTestFileName + "2");

    configureByFile(baseTestFileName + "3.dtd");
    checkResultByFile(baseTestFileName + "3_after.dtd");

    configureByFile(baseTestFileName + "4.dtd");
    checkResultByFile(baseTestFileName + "4_after.dtd");

    configureByFile(baseTestFileName + "5.dtd");
    checkResultByFile(baseTestFileName + "5_after.dtd");

    // todo uncomment
//    doCompletionTest("DtdElementCompletion");
  }

  private void doCompletionTest(String name) {
    configureByFile(name + ".dtd");
    checkResultByFile(name + "_after.dtd");
  }

  @Test
  public void testSchemaEnumerationCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addResource("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd", myFixture.getTestDataPath() + "/web-app_2_4.xsd");
    configureByFile("12.xml");
    complete();
    checkResultByFile("12_after.xml");

    configureByFile("12_3.xml");
    complete();
    checkResultByFile("12_3_after.xml");

    configureByFiles("12_4.xml", "12_4_sample.xsd", "12_4_included-sample.xsd");
    complete();
    checkResultByFile("12_4_after.xml");
  }

  @Test
  public void testEntityRefCompletion() {
    configureByFile("13.xml");
    checkResultByFile("13_after.xml");
  }

  @Test
  public void testEntityRefCompletion_2() {
    configureByFile("13_2.xml");
    checkResultByFile("13_2_after.xml");
  }

  @Test
  public void testEntityRefCompletion2() {
    configureByFiles("28.xml", "28.ent");
    complete();
    type('\n');
    checkResultByFile("28_after.xml");
  }

  @Test
  public void testEntityRefCompletion3() {
    configureByFile("13_3.xml");
    checkResultByFile("13_3_after.xml");
  }

  @Test
  public void testElementRefCompletionInSchema() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      configureByFile("27.xsd");
      selectItem(myFixture.getLookupElements()[0], '\'');
      checkResultByFile("27_after.xsd");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  @Test
  public void testFilePathCompletionInSystem() {
    configureByFile("14.xml");
    checkResultByFile("14_after.xml");
  }

  @Test
  public void testFilePathCompletionInSchema() {
    configureByFile("15.xml");
    checkResultByFile("15_after.xml");
  }

  @Test
  public void testUrlCompletionInSchema() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String url = "http://www.w3.org/1999/xhtml";
    String location = "xhtml1-strict.xsd";
    addResource(url,location);

    configureByFile("16.xml");
    assertThat(myFixture.getLookupElementStrings()).isNullOrEmpty();
    checkResultByFile("16_after.xml");

    configureByFile("17.xml");
    assertThat(myFixture.getLookupElementStrings()).isNullOrEmpty();
    checkResultByFile("17_after.xml");

    configureByFile("31.xml");
    assertThat(myFixture.getLookupElementStrings()).isNullOrEmpty();
    checkResultByFile("31_after.xml");
  }

  @Test
  public void testInsertExtraRequiredAttribute() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile("37.xml");
    selectItem(myFixture.getLookupElements()[0], '\t');
    checkResultByFile("37_after.xml");
  }

  @Test
  public void testInsertExtraRequiredAttributeSingleQuote() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    final CodeStyleSettings.QuoteStyle quote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Single;
      configureByFile(myFixture.getTestName(true) + ".html");
      checkResultByFile(myFixture.getTestName(true) + "_after.html");
    } finally {
      settings .HTML_QUOTE_STYLE = quote;
    }
  }

  @Test
  public void testInsertExtraRequiredAttributeNoneQuote() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final HtmlCodeStyleSettings settings = getHtmlSettings();
    final CodeStyleSettings.QuoteStyle quote = settings.HTML_QUOTE_STYLE;
    try {
      settings.HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.None;
      configureByFile(myFixture.getTestName(true) + ".html");
      checkResultByFile(myFixture.getTestName(true) + "_after.html");
    } finally {
      settings.HTML_QUOTE_STYLE = quote;
    }
  }

  @Test
  public void testBeforeAttributeValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile(myFixture.getTestName(true) + ".xml");
    assertThat(myFixture.getLookupElements()).isEmpty();
    checkResultByFile(myFixture.getTestName(true) + ".xml");
  }

  @Test
  public void testAttributeNoQuotes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    boolean oldInsertQuotes = WebEditorOptions.getInstance().isInsertQuotesForAttributeValue();
    WebEditorOptions.getInstance().setInsertQuotesForAttributeValue(false);
    try {
      configureByFile(myFixture.getTestName(false) + ".xml");
      selectItem(myFixture.getLookupElements()[0], '\t');
      checkResultByFile(myFixture.getTestName(false) + "_after.xml");
    } finally {
      WebEditorOptions.getInstance().setInsertQuotesForAttributeValue(oldInsertQuotes);
    }
  }

  @Test
  public void testBeforeAttributeNameWithPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile(myFixture.getTestName(true) + ".xml");
    selectItem(myFixture.getLookupElements()[0], '\t');
    checkResultByFile(myFixture.getTestName(true) + "_after.xml");
  }

  @Test
  public void testUrlCompletionInDtd() {
    configureByFile("20.xml");
    complete();
    checkResultByFile("20_after.xml");
  }

  @Test
  public void testElementFromSchemaIncludeCompletion() {
    String location = "21.xsd";
    String location2 = "21_2.xsd";

    addResource(location, location);
    addResource(location2, location2);
    configureByFiles("21.xml",
                     location,
                     location2);
    complete();
    checkResultByFile("21_after.xml");
  }

  @Test
  public void testSchemaTypeReferenceCompletion() {
    configureByFile("22.xsd");
    checkResultByFile("22_after.xsd");
  }

  @Test
  public void testSchemaTypeReferenceCompletion2() {
    configureByFile("23.xsd");
    checkResultByFile("23_after.xsd");
  }

  @Test
  public void testSchemaTypeReferenceCompletion3() {
    configureByFile("34.xsd");
    checkResultByFile("34_after.xsd");
  }

  @Test
  public void testSchemaTypeReferenceCompletion4() {
    configureByFile("35.xsd");
    checkResultByFile("35_after.xsd");
  }

  @Test
  public void testSchemaNonAllowedElementCompletion() {
    configureByFiles("33.xml",
                     "33.xsd",
                     "33_2.xsd");
    complete();
    checkResultByFile("33_after.xml");

    configureByFiles("33_2.xml",
                     "33.xsd",
                     "33_2.xsd");
    complete();
    checkResultByFile("33_2_after.xml");
  }

  @Test
  public void testSchemaBooleanCompletion() {
    configureByFile("SchemaBooleanCompletion.xsd");
    checkResultByFile("SchemaBooleanCompletion_after.xsd");
  }

  @Test
  public void testXIncludeCompletion() {
    configureByFile("XIncludeCompletion.xsd");
    checkResultByFile("XIncludeCompletion_after.xsd");
  }

  @Test
  public void testCorrectPrefixInsertion() {
    configureByFile("CorrectPrefixInsertion.xml");
    checkResultByFile("CorrectPrefixInsertion_after.xml");
  }

  @Test
  public void testDoNotSuggestAbstractElementsFromSchema() {
    basicDoTest("");
  }

  private void basicDoTest(String ext) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false) + ext;
    configureByFile(testName + ".xml");
    checkResultByFile(testName + "_after.xml");
  }

  @Test
  public void testDoNotSuggestTagsFromOtherNsInXslt() {
    basicDoTest("");
  }

  @Test
  public void testDoNotSuggestTagsFromOtherNsInXslt_2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    configureByFile(testName + ".xml");
    myFixture.type('\n');
    checkResultByFile(testName + "_after.xml");
  }
  
  @Test
  public void testDoNotSuggestTagsFromOtherNsInXslt_3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    configureByFile(testName + ".xml");
    myFixture.type('\n');
    checkResultByFile(testName + "_after.xml");
  }

  @Test
  public void testDoNotSuggestTagsInXHtml() {
    basicDoTest("");
  }

  @Test
  public void testSuggestTagsInXHtml() {
    basicDoTest("");
    basicDoTest("_2");
  }

  @Test
  public void testCompleteWithSubstitutionGroup() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFiles(myFixture.getTestName(false) + ".xml", "mule.xsd", "mule-management.xsd");
    complete();
    myFixture.type('\n');
    checkResultByFile(myFixture.getTestName(false) + "_after.xml");
  }

  @Test
  public void testCorrectSelectionInsertion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
    addResource("http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd",
                myFixture.getTestDataPath() + "/hibernate-mapping-3.0.dtd");

    configureByFile("CorrectSelectionInsertion.xml");
    selectItem(myFixture.getLookupElements()[4], '\t');
    checkResultByFile("CorrectSelectionInsertion_after.xml");
    
    StatisticsUpdate.applyLastCompletionStatisticsUpdate();

    configureByFile("CorrectSelectionInsertion2.xml");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.getEditor().getSelectionModel().removeSelection());
    selectItem(myFixture.getLookupElements()[0], '\t');
    checkResultByFile("CorrectSelectionInsertion2_after.xml");
  }

  @Test
  public void testCasePreference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final int old = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;

    String location = "30.dtd";
    try {

      addResource(location, location);
      configureByFiles("30.xml",
                       location);
      complete();

      assertIterableEquals(List.of("map", "Map"), myFixture.getLookupElementStrings());
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = old;
    }
  }

  @Test
  public void testCompleteXmlLang() {
    basicDoTest("");
    basicDoTest("_2");
  }

  @Test
  public void testCompleteWhenNoNsSchemaLocation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      final String testName = myFixture.getTestName(false);

      configureByFiles(testName + ".xml",
                       testName + ".xsd");
      complete();
      selectItem(myFixture.getLookupElements()[0], '\"');
      checkResultByFile(testName + "_after.xml");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  @Test
  public void testCompleteWhenUnqualifiedElements() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);

    configureByFiles(testName + ".xml",
                     testName + ".xsd");
    complete();
    checkResultByFile(testName + "_after.xml");
  }

  public void _testIDEADEV_32773() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);

    configureByFiles(testName + ".xml",
                     testName + ".xsd",
                     testName + "-vm.xsd",
                     testName + "-jms.xsd",
                     testName + "-stdio.xsd",
                     testName + "-schemadoc.xsd");
    complete();
    checkResultByFile(testName + "_after.xml");
  }

  @Test
  public void testCompleteEnumeration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);

    configureByFiles(testName + ".xml",
                     testName + "_importedSchema.xsd",
                     testName + "_outerSchema.xsd");
    complete();
    checkResultByFile(testName + "_after.xml");
  }

  @Test
  public void testAttributeNameInAttributeValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile(myFixture.getTestName(false) + ".xml");
    complete();
    assertThat(myFixture.getLookupElements()).isEmpty();
    checkResultByFile(myFixture.getTestName(false) + "_after.xml");
  }

  @Test
  public void testNoFixedAttrComplete() {
    basicDoTest("");
  }

  @Test
  public void testCompleteWords() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class ABxxZ {}");

    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile(testName + ".xml");
    myFixture.complete(CompletionType.BASIC, 2);
    assertEquals("ABxxZ", myFixture.getLookupElements()[0].getLookupString());
    assertEquals("ABxxCDEF", myFixture.getLookupElements()[1].getLookupString());
    selectItem(myFixture.getLookupElements()[1], Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(testName + "_after.xml");
  }

  @Test
  public void testClassNamesOutrankWords() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class SomeClass {}");

    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile(testName + ".xml");
    myFixture.complete(CompletionType.BASIC, 2);
    selectItem(assertThat(myFixture.getLookupElements()).singleElement().actual(), Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(testName + "_after.xml");
  }

  @Test
  public void testColonInTagName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile(myFixture.getTestName(false) + ".xml");
    type('f');
    type('o');
    type('o');
    type(':');
    assertNotNull(LookupManager.getActiveLookup(myFixture.getEditor()));
  }

  @Test
  public void testDoNotInsertClosingTagB4Text() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile("doNotInsertClosingTagB4Text.xml");
    selectItem(myFixture.getLookupElements()[0], '>');
    checkResultByFile("doNotInsertClosingTagB4Text_after.xml");
  }

  private void selectItem(LookupElement element, char ch) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
      lookup.setCurrentItem(element);
      lookup.finishLookup(ch);
    });
  }

  @Test
  public void testDoNotInsertClosingTagWithoutTagName() {
    configureByFile("doNotInsertClosingTagWithoutTagName.xml");
    type('>');
    //assertNotNull(myItems);
    //selectItem(myItems[0], '>');
    checkResultByFile("doNotInsertClosingTagWithoutTagName_after.xml");
  }

  @Test
  public void testCompleteXmlTag() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addResource("http://maven.apache.org/xsd/archetype-descriptor-1.0.0.xsd", myFixture.getTestDataPath() + "archetype-descriptor-1.0.0.xsd");
    basicDoTest("");
  }

  @Test
  public void testCompleteXsl() {
    basicDoTest("");
  }

  @Test
  public void testAttributeWildcardFromAnotherNamespace() {
    configureByFiles("foo.xsd", "bar.xsd");
    basicDoTest("");
  }

  @Test
  public void testCompleteQualifiedTopLevelTags() {
    configureByFiles("foo.xsd", "bar.xsd");
    basicDoTest("");
  }

  @Test
  public void testUnqualifiedElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("UnqualifiedElement.xsd");
    myFixture.testCompletionVariants("UnqualifiedElement.xml", "code");
  }

  @Test
  public void testDoNotSuggestExistingAttributes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("DoNotSuggestExistingAttributes.xml");
    myFixture.completeBasic();
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertFalse(strings.contains("xsi:schemaLocation"));
    assertThat(strings).hasSize(14).containsOnly("attributeFormDefault", "blockDefault", "elementFormDefault", "finalDefault", "id", "targetNamespace", "version", "xml:base", "xml:id", "xml:lang", "xml:space", "xsi:nil", "xsi:noNamespaceSchemaLocation", "xsi:type");
  }

  @Test
  public void testRequiredAttributesOnTop() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.html", "<img <caret>");
    myFixture.completeBasic();
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertEquals("alt", strings.get(0));
    assertEquals("src", strings.get(1));
    assertEquals("align", strings.get(2));
  }

  @Test
  public void testDoNotProcessAnyInRestrictions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xsd", """
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <<caret>
      </xs:schema>""");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).hasSize(11).containsOnly("xs:annotation", "xs:attribute", "xs:attributeGroup", "xs:complexType", "xs:element", "xs:group", "xs:import", "xs:include", "xs:notation", "xs:redefine", "xs:simpleType");
  }

  @Test
  public void testSubstitute() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("Substitute/schema-a.xsd", "Substitute/schema-b.xsd");
    myFixture.testCompletionVariants("Substitute/test.xml", "b:instance", "instance");
  }

  @Test
  public void testAfterPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testCompletion("Substitute/testAfterPrefix.xml", "Substitute/testAfterPrefix_after.xml", "Substitute/schema-a.xsd", "Substitute/schema-b.xsd");
  }

  @Test
  public void testEnumeratedTagValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("tagValue/enumerated.xsd");
    myFixture.testCompletionVariants("tagValue/completeEnum.xml", "none", "standard");
    myFixture.testCompletionVariants("tagValue/completeBoolean.xml", "false", "true");
  }

  @Test
  public void testInheritedAttribute() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("InheritedAttr/test.xsd", "InheritedAttr/library.xsd");
    myFixture.testCompletionVariants("InheritedAttr/test.xml", "buz",
    "library:boo",
    "xml:base",
    "xml:id",
    "xml:lang",
    "xml:space");
  }

  @Test
  public void testSchemaLocation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("spring-beans.xsd");
    myFixture.testCompletionVariants("SchemaLocation.xml", "http://www.springframework.org/schema/beans ",
                                     "http://www.w3.org/2001/XMLSchema ", "http://www.w3.org/2001/XMLSchema-instance ");
    myFixture.testCompletionVariants("SchemaLocation2.xml", "http://www.w3.org/2001/XMLSchema.xsd");
  }

  @Test
  public void testNamespaceCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", "<schema xmlns=\"<caret>\"/>");
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals("http://www.w3.org/2001/XMLSchema", elements[0].getLookupString());

    myFixture.configureByText("unknown.xml", "<unknown_tag_name xmlns=\"<caret>\"/>");
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().size() > 3); // all standard schemas actually
  }

  @Test
  public void testCustomNamespaceCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("main.xsd", "sub.xsd");
    LookupElement[] elements = myFixture.completeBasic();
    assertTrue(Arrays.stream(elements).anyMatch(element -> "http://www.test.com/sub".equals(element.getLookupString())));
  }

  @Test
  public void testRootTagCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      myFixture.configureByText("foo.xml", "<schem<caret>");
      myFixture.completeBasic();
      myFixture.type('\n');
      myFixture.checkResult("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"<caret>");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  @Test
  public void testPi() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", "<<caret>");
    myFixture.completeBasic();
    myFixture.type('?');
    myFixture.type('\n');
    myFixture.checkResult("<?xml version=\"1.0\" encoding=\"<caret>\" ?>");
  }

  @Test
  public void testAttributeValueToken() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("foo.xml", """
      <schema xmlns="http://www.w3.org/2001/XMLSchema">
          <element name="a" abstract=<caret>""/>
      </schema>""");
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals(0, elements.length);
  }

  @Test
  public void testMultipleImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<String> variants =
      myFixture.getCompletionVariants("MultipleImports/agg.xsd", "MultipleImports/toimport1.xsd", "MultipleImports/toimport2.xsd");
    assertThat(variants).hasSize(3).containsOnly("int", "integer", "invisibleType");
  }

  @Test
  public void testEnumeratedTypeUnion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<String> variants = myFixture.getCompletionVariants("enumerations.xml", "enumerations.xsd");
    assertThat(variants).hasSize(2).containsOnly("A", "B");
  }

  private HtmlCodeStyleSettings getHtmlSettings() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return CodeStyle.getSettings(myFixture.getProject())
                    .getCustomSettings(HtmlCodeStyleSettings.class);
  }
}

