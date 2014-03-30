package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiReference;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.xml.util.XmlUtil;

import java.util.List;

/**
 * @by Maxim.Mossienko
 */
@SuppressWarnings("ConstantConditions")
public class XmlCompletionTest extends LightCodeInsightFixtureTestCase {

  private String myOldDoctype;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    addResource("http://www.springframework.org/schema/beans",
                getTestDataPath() + "/spring-beans-2.0.xsd");
    final ExternalResourceManagerEx manager = ExternalResourceManagerEx.getInstanceEx();
    myOldDoctype = manager.getDefaultHtmlDoctype(getProject());
    manager.setDefaultHtmlDoctype(XmlUtil.XHTML_URI, getProject());
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    ExternalResourceManagerEx.getInstanceEx().setDefaultHtmlDoctype(myOldDoctype, getProject());
    super.tearDown();
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/completion/";
  }

  private void addResource(final String url, final String location) {
    final ExternalResourceManager manager = ExternalResourceManager.getInstance();
    final String old = manager.getResourceLocation(url, (String)null);
    if (old != null &&
        old != url //strange hack: ERM returns url as location sometimes
      ) {
      return;
    }

    ExternalResourceManagerExImpl.addTestResource(url, location, myTestRootDisposable);
  }

  @Override
  protected void runTest() throws Throwable {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        XmlCompletionTest.super.runTest();
      }
    }.execute();
  }

  public void testCompleteWithAnyInSchema() throws Exception {
    String location = "29.xsd";
    addResource("aaa",location);

    configureByFiles("29.xml", location);
    complete();
    checkResultByFile("29_after.xml");
  }

  private void checkResultByFile(String s) {
    myFixture.checkResultByFile(s);
  }

  private void complete() {
    myFixture.completeBasic();
  }

  private void configureByFiles(String... files) {
    myFixture.configureByFiles(files);
  }

  public void testCompleteWithAnyInSchema2() throws Exception {
    configureByFiles("32.xml", "32.xsd", "32_2.xsd");
    complete();
    checkResultByFile("/32_after.xml");

    configureByFiles("32_2.xml", "32.xsd", "32_2.xsd");
    complete();
    checkResultByFile("32_2_after.xml");
  }

  public void testCompleteWithAnyInSchema3() throws Exception {
    addResource("http://www.springframework.org/schema/tx",
                getTestDataPath() + "/spring-tx-2.0.xsd");
    addResource("http://www.springframework.org/schema/util",
                getTestDataPath() + "/spring-util-2.0.xsd");

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

  public void testXmlNonQualifiedElementCompletion() throws Exception {
    String location = "25.xsd";
    String url = "http://www.dummy-temp-address";
    addResource(url,location);

    configureByFiles("25.xml",
                     location);
    complete();
    checkResultByFile("25_after.xml");
  }

  public void testXmlCompletion() throws Exception {
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

  public void testXmlCompletionWhenTagsWithSemicolon() throws Exception {
    configureByFiles("XmlCompletionWhenTagsWithSemicolon.xml", "XmlCompletionWhenTagsWithSemicolon.dtd");
    complete();
    checkResultByFile("XmlCompletionWhenTagsWithSemicolon_after.xml");
  }

  public void testCompleteTagWithXsiTypeInParent() throws Exception {
    final String testName = getTestName(false);
    String url = "urn:test";
    doCompletionTest("xml", url, testName + ".xsd");
  }

  public void testAttributesTemplateFinishWithSpace() throws Throwable {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());

    configureByFile(getTestName(false) + ".xml");
    type('b');
    type('e');
    type('a');
    type('n');
    type(' ');
    checkResultByFile(getTestName(false) + "_after.xml");
  }

  private void configureByFile(String s) {
    myFixture.configureByFile(s);
    myFixture.completeBasic();
  }

  public void testNoAttributesTemplateFinishWithSpace() throws Throwable {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());

    configureByFile(getTestName(false) + ".xml");
    type('d');
    type('e');
    type(' ');
    checkResultByFile(getTestName(false) + "_after.xml");
  }

  private void type(char c) {
    myFixture.type(c);
  }

  private void doCompletionTest(final String ext, final String url, final String location) throws Exception {
    final String testName = getTestName(false);
    addResource(url, location);

    configureByFiles(testName + "." + ext,
                     location);
    complete();
    checkResultByFile(testName + "_after." + ext);
  }

  public void testSingleCompletionVariantAtRootTag() throws Exception {
    addResource("http://www.springframework.org/dtd/spring-beans.dtd", getTestDataPath() + "/spring-beans-2.0.dtd");

    basicDoTest("");
  }

  public void testDtdCompletion() throws Exception {
    final String baseTestFileName = getTestName(false);
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

  public void testSchemaEnumerationCompletion() throws Exception {
    addResource("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd", getTestDataPath() + "/web-app_2_4.xsd");
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

  public void testEntityRefCompletion() throws Exception {
    configureByFile("13.xml");
    checkResultByFile("13_after.xml");
  }

  public void testEntityRefCompletion_2() throws Exception {
    configureByFile("13_2.xml");
    checkResultByFile("13_2_after.xml");
  }

  public void testEntityRefCompletion2() throws Exception {
    configureByFiles("28.xml", "28.ent");
    complete();
    type('\n');
    checkResultByFile("28_after.xml");
  }

  public void testEntityRefCompletion3() throws Exception {
    configureByFile("13_3.xml");
    checkResultByFile("13_3_after.xml");
  }

  public void testElementRefCompletionInSchema() throws Exception {
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

  public void testFilePathCompletionInSystem() throws Exception {
    configureByFile("14.xml");
    checkResultByFile("14_after.xml");
  }

  public void testFilePathCompletionInSchema() throws Exception {
    configureByFile("15.xml");
    checkResultByFile("15_after.xml");
  }

  public void testUrlCompletionInSchema() throws Exception {
    String url = "http://www.w3.org/1999/xhtml";
    String location = "xhtml1-strict.xsd";
    addResource(url,location);

    configureByFile("16.xml");
    assertNullOrEmpty(myFixture.getLookupElementStrings());
    checkResultByFile("16_after.xml");

    configureByFile("17.xml");
    assertNullOrEmpty(myFixture.getLookupElementStrings());
    checkResultByFile("17_after.xml");

    configureByFile("31.xml");
    assertNullOrEmpty(myFixture.getLookupElementStrings());
    checkResultByFile("31_after.xml");
  }

  public void testInsertExtraRequiredAttribute() throws Exception {
    configureByFile("37.xml");
    selectItem(myFixture.getLookupElements()[0], '\t');
    checkResultByFile("37_after.xml");
  }

  public void testBeforeAttributeValue() throws Throwable {
    configureByFile(getTestName(true) + ".xml");
    assertEmpty(myFixture.getLookupElements());
    checkResultByFile(getTestName(true) + ".xml");
  }

  public void testBeforeAttributeNameWithPrefix() throws Exception {
    configureByFile(getTestName(true) + ".xml");
    selectItem(myFixture.getLookupElements()[0], '\t');
    checkResultByFile(getTestName(true) + "_after.xml");
  }

  public void testUrlCompletionInDtd() throws Exception {
    configureByFile("20.xml");
    final PsiReference referenceAt = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset() - 1);
    assertNotNull(referenceAt);
    assertTrue(referenceAt.getVariants().length > 0);
  }

  public void testElementFromSchemaIncludeCompletion() throws Exception {
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

  public void testSchemaTypeReferenceCompletion() throws Exception {
    configureByFile("22.xsd");
    checkResultByFile("22_after.xsd");
  }

  public void testSchemaTypeReferenceCompletion2() throws Exception {
    configureByFile("23.xsd");
    checkResultByFile("23_after.xsd");
  }

  public void testSchemaTypeReferenceCompletion3() throws Exception {
    configureByFile("34.xsd");
    checkResultByFile("34_after.xsd");
  }

  public void testSchemaTypeReferenceCompletion4() throws Exception {
    configureByFile("35.xsd");
    checkResultByFile("35_after.xsd");
  }

  public void testSchemaNonAllowedElementCompletion() throws Exception {
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

  public void testSchemaBooleanCompletion() throws Exception {
    configureByFile("SchemaBooleanCompletion.xsd");
    checkResultByFile("SchemaBooleanCompletion_after.xsd");
  }

  public void testXIncludeCompletion() throws Exception {
    configureByFile("XIncludeCompletion.xsd");
    checkResultByFile("XIncludeCompletion_after.xsd");
  }

  public void testCorrectPrefixInsertion() throws Exception {
    configureByFile("CorrectPrefixInsertion.xml");
    checkResultByFile("CorrectPrefixInsertion_after.xml");
  }

  public void testDoNotSuggestAbstractElementsFromSchema() throws Exception {
    basicDoTest("");
  }

  private void basicDoTest(String ext) throws Exception {
    final String testName = getTestName(false) + ext;
    configureByFile(testName + ".xml");
    checkResultByFile(testName + "_after.xml");
  }

  public void testDoNotSuggestTagsFromOtherNsInXslt() throws Exception {
    basicDoTest("");
  }

  public void testDoNotSuggestTagsFromOtherNsInXslt_2() throws Exception {
    final String testName = getTestName(false);
    configureByFile(testName + ".xml");
    myFixture.type('\n');
    checkResultByFile(testName + "_after.xml");
  }
  
  public void testDoNotSuggestTagsFromOtherNsInXslt_3() throws Exception {
    final String testName = getTestName(false);
    configureByFile(testName + ".xml");
    myFixture.type('\n');
    checkResultByFile(testName + "_after.xml");
  }

  public void testDoNotSuggestTagsInXHtml() throws Exception {
    basicDoTest("");
  }

  public void testSuggestTagsInXHtml() throws Exception {
    basicDoTest("");
    basicDoTest("_2");
  }

  public void testCompleteWithSubstitutionGroup() throws Exception {
    configureByFiles(getTestName(false) + ".xml", "mule.xsd", "mule-management.xsd");
    complete();
    myFixture.type('\n');
    checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testCorrectSelectionInsertion() throws Exception {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(getTestRootDisposable());
    addResource("http://hibernate.sourceforge.net/hibernate-mapping-3.0.dtd",
                getTestDataPath() + "/hibernate-mapping-3.0.dtd");

    configureByFile("CorrectSelectionInsertion.xml");
    selectItem(myFixture.getLookupElements()[4], '\t');
    checkResultByFile("CorrectSelectionInsertion_after.xml");
    
    CompletionLookupArranger.applyLastCompletionStatisticsUpdate();

    configureByFile("CorrectSelectionInsertion2.xml");
    myFixture.getEditor().getSelectionModel().removeSelection();
    selectItem(myFixture.getLookupElements()[0], '\t');
    checkResultByFile("CorrectSelectionInsertion2_after.xml");
  }

  public void testCasePreference() throws Exception {
    final int old = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;

    String location = "30.dtd";
    try {

      addResource(location, location);
      configureByFiles("30.xml",
                       location);
      complete();

      assertOrderedEquals(myFixture.getLookupElementStrings(), "map", "Map");
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = old;
    }
  }

  public void testCompleteXmlLang() throws Exception {
    basicDoTest("");
    basicDoTest("_2");
  }

  public void testCompleteWhenNoNsSchemaLocation() throws Exception {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      final String testName = getTestName(false);

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

  public void testCompleteWhenUnqualifiedElements() throws Exception {
    final String testName = getTestName(false);

    configureByFiles(testName + ".xml",
                     testName + ".xsd");
    complete();
    checkResultByFile(testName + "_after.xml");
  }

  public void _testIDEADEV_32773() throws Exception {
    final String testName = getTestName(false);

    configureByFiles(testName + ".xml",
                     testName + ".xsd",
                     testName + "-vm.xsd",
                     testName + "-jms.xsd",
                     testName + "-stdio.xsd",
                     testName + "-schemadoc.xsd");
    complete();
    checkResultByFile(testName + "_after.xml");
  }

  public void testCompleteEnumeration() throws Exception {
    final String testName = getTestName(false);

    configureByFiles(testName + ".xml",
                     testName + "_importedSchema.xsd",
                     testName + "_outerSchema.xsd");
    complete();
    checkResultByFile(testName + "_after.xml");
  }

  public void testAttributeNameInAttributeValue() throws Throwable {
    configureByFile(getTestName(false) + ".xml");
    complete();
    assertEmpty(myFixture.getLookupElements());
    checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testNoFixedAttrComplete() throws Exception {
    basicDoTest("");
  }

  public void testCompleteWords() throws Exception {
    myFixture.addClass("public class ABxxZ {}");

    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".xml");
    myFixture.complete(CompletionType.BASIC, 2);
    assertEquals("ABxxZ", myFixture.getLookupElements()[0].getLookupString());
    assertEquals("ABxxCDEF", myFixture.getLookupElements()[1].getLookupString());
    selectItem(myFixture.getLookupElements()[1], Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(testName + "_after.xml");
  }

  public void testClassNamesOutrankWords() throws Exception {
    myFixture.addClass("package foo; public class SomeClass {}");

    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".xml");
    myFixture.complete(CompletionType.BASIC, 2);
    selectItem(assertOneElement(myFixture.getLookupElements()), Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile(testName + "_after.xml");
  }

  public void testColonInTagName() throws Throwable {
    configureByFile(getTestName(false) + ".xml");
    type('f');
    type('o');
    type('o');
    type(':');
    assertNotNull(LookupManager.getActiveLookup(myFixture.getEditor()));
  }

  public void testDoNotInsertClosingTagB4Text() throws Exception {
    configureByFile("doNotInsertClosingTagB4Text.xml");
    selectItem(myFixture.getLookupElements()[0], '>');
    checkResultByFile("doNotInsertClosingTagB4Text_after.xml");
  }

  private void selectItem(LookupElement element, char ch) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
    lookup.setCurrentItem(element);
    lookup.finishLookup(ch);
  }

  public void testDoNotInsertClosingTagWithoutTagName() throws Exception {
    configureByFile("doNotInsertClosingTagWithoutTagName.xml");
    type('>');
    //assertNotNull(myItems);
    //selectItem(myItems[0], '>');
    checkResultByFile("doNotInsertClosingTagWithoutTagName_after.xml");
  }

  public void testCompleteXmlTag() throws Exception {
    addResource("http://maven.apache.org/xsd/archetype-descriptor-1.0.0.xsd", getTestDataPath() + "archetype-descriptor-1.0.0.xsd");
    basicDoTest("");
  }

  public void testCompleteXsl() throws Exception {
    basicDoTest("");
  }

  public void testAttributeWildcardFromAnotherNamespace() throws Exception {
    configureByFiles("foo.xsd", "bar.xsd");
    basicDoTest("");
  }

  public void testCompleteQualifiedTopLevelTags() throws Exception {
    configureByFiles("foo.xsd", "bar.xsd");
    basicDoTest("");
  }

  public void testDoNotSuggestExistingAttributes() throws Exception {
    myFixture.configureByFile("DoNotSuggestExistingAttributes.xml");
    myFixture.completeBasic();
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertFalse(strings.contains("xsi:schemaLocation"));
    assertSameElements(strings, "attributeFormDefault",
                       "blockDefault",
                       "elementFormDefault",
                       "finalDefault",
                       "id",
                       "targetNamespace",
                       "version",
                       "xml:base",
                       "xml:id",
                       "xml:lang",
                       "xml:space",
                       "xsi:nill",
                       "xsi:noNamespaceSchemaLocation",
                       "xsi:type");
  }

  public void testRequiredAttributesOnTop() throws Exception {
    myFixture.configureByText("foo.html", "<img <caret>");
    myFixture.completeBasic();
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertEquals("alt", strings.get(0));
    assertEquals("src", strings.get(1));
    assertEquals("align", strings.get(2));
  }

  public void testDoNotProcessAnyInRestrictions() throws Exception {
    myFixture.configureByText("foo.xsd", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                                         "    <<caret>\n" +
                                         "</xs:schema>");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "xs:annotation",
                                                            "xs:attribute",
                                                            "xs:attributeGroup",
                                                            "xs:complexType",
                                                            "xs:element",
                                                            "xs:group",
                                                            "xs:import",
                                                            "xs:include",
                                                            "xs:notation",
                                                            "xs:redefine",
                                                            "xs:simpleType");
  }

  public void testSubstitute() throws Exception {
    myFixture.configureByFiles("Substitute/schema-a.xsd", "Substitute/schema-b.xsd");
    myFixture.testCompletionVariants("Substitute/test.xml", "b:instance", "instance");
  }

  public void testAfterPrefix() throws Exception {
    myFixture.testCompletion("Substitute/testAfterPrefix.xml", "Substitute/testAfterPrefix_after.xml", "Substitute/schema-a.xsd", "Substitute/schema-b.xsd");
  }

  public void testEnumeratedTagValue() throws Exception {
    myFixture.configureByFile("tagValue/enumerated.xsd");
    myFixture.testCompletionVariants("tagValue/completeEnum.xml", "none", "standard");
    myFixture.testCompletionVariants("tagValue/completeBoolean.xml", "false", "true");
  }
}

