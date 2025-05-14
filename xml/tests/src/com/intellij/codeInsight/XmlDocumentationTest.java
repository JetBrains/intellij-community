// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.webSymbols.testFramework.WebTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class XmlDocumentationTest extends BasePlatformTestCase {
  public void testXmlDoc() {
    doOneTest("1.xml", "display-name", "web-app_2_3.dtd");
    doOneTest("2.xml", null, "web-app_2_4.xsd");
    doOneTest("3.xml", null, "web-app_2_4.xsd", "j2ee_1_4.xsd");
    doOneTest("3_2.xml", null, "web-app_2_4.xsd", "j2ee_1_4.xsd");
    doOneTest("3_3.xml", null, "web-app_2_4.xsd", "j2ee_1_4.xsd");

    doOneTest("4.xml", "context-param", false, false, "web-app_2_4.xsd");
    doOneTest("5.xml", "aaa:context-param", false, false, "web-app_2_4.xsd");
    doOneTest("6.xsd", "xs:complexType", true, true);
    doOneTest("7.xml", "bbb");
    doOneTest("8.xml", "bbb");
    doOneTest("9.xml", "laquo");
  }

  public void testXmlDocWithCData() {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml", "spring-beans.xsd");
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + "2.xml", "spring-beans.xsd");
  }

  public void testXmlDoc2() {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml", "web-app_2_4.xsd");
  }

  public void testXmlDoc3() {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml", "hibernate-mapping-3.0.dtd");
  }

  public void testXmlDoc4() {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult(testName + ".xml", testName + ".xsd");
  }

  public void testSchemaPrefix() {
    DocumentationTestContext context = new DocumentationTestContext("SchemaPrefix.xml");
    assertEquals("XML Namespace Prefix \"xs\" (http://www.w3.org/2001/XMLSchema)", context.getQuickNavigateInfo());
  }

  public void testEntityValue() {
    DocumentationTestContext context = new DocumentationTestContext("9.xml");
    assertEquals("\"&#171;\"", context.getQuickNavigateInfo());
  }

  public void testXmlDoc6() {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"car", testName + ".xml", testName + ".xsd");
  }

  public void testXmlDoc7() {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"$Paste", testName + ".xml", testName + ".xsd");
  }

  public void testSvgDoc() {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"rect", testName + ".svg");
  }

  public void testSvgDoc2() {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"stroke-width", testName + ".svg");
  }

  public void testSvgDoc3() {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".svg");
  }

  public void testScopeAttribute() {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml", "spring-beans.xsd");
  }

  public void testClassAttribute() {
    doQuickDocGenerationTestWithNullExpectedResult("class.xml");
  }

  public void testXslCompletion() {
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"apply-imports", "xslCompletion.xsl");
  }

  public void testNoHtmlDocInXml() {
    doQuickDocGenerationTestWithNullExpectedResult("component.xml");
  }

  private void doQuickDocGenerationTestWithCheckExpectedResult(final String... baseFileNames) {
    doQuickDocGenerationTestWithCheckExpectedResult(null, baseFileNames);
  }

  private void doQuickDocGenerationTestWithNullExpectedResult(final String... baseFileNames) {
    final DocumentationTestContext context = new DocumentationTestContext(baseFileNames);
    String text = context.generateDoc();
    assertThat(text).isNull();
  }

  private void doQuickDocGenerationTestWithCheckExpectedResult(Object completionVariant, final String... baseFileNames) {
    final DocumentationTestContext context = new DocumentationTestContext(baseFileNames);

    String text = context.generateDoc();
    assertThat(text).isNotNull();
    WebTestUtil.checkTextByFile(myFixture, cleanupHtmlDoc(text), baseFileNames[0] + ".expected.html");

    if (completionVariant != null) {
      String completionText = context.generateDocForCompletion(completionVariant);
      assertThat(completionText).isNotNull();
      WebTestUtil.checkTextByFile(myFixture, cleanupHtmlDoc(completionText),
                                  baseFileNames[0] + ".expected.completion.html");
    }
  }

  private static String cleanupHtmlDoc(String doc) {
    return StringUtil.convertLineSeparators(doc.replaceAll(" +\n", "\n"));
  }

  public void testDtdDoc() {
    doOneTest("dtd.dtd", "foo", false, true, "web-app_2_4.xsd");
    doOneTest("dtd.xml", "foo", false, true, "web-app_2_4.xsd");
  }

  private void doOneTest(String fileName, String lookupObject, String... additional) {
    copyAdditionalFiles(additional);
    doOneTest(fileName, lookupObject, false, true, "web-app_2_4.xsd");
  }

  private void copyAdditionalFiles(String[] additional) {
    for (String file : additional) {
      myFixture.copyFileToProject(file);
    }
  }

  private final class DocumentationTestContext {
    private final DocumentationProvider documentationProvider;
    private final PsiElement originalElement;
    private PsiElement element;
    private final PsiFile psiFile;

    private DocumentationTestContext(String... fileNames) {
      copyAdditionalFiles(fileNames);
      psiFile = myFixture.configureByFile(fileNames[0]);
      originalElement = psiFile.findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      element = DocumentationManager.getInstance(getProject()).findTargetElement(myFixture.getEditor(), psiFile, originalElement);

      if (element == null) {
        element = originalElement;
      }

      documentationProvider = DocumentationManager.getProviderFromElement(element);
    }

    private @Nullable String generateDoc() {
      return documentationProvider.generateDoc(element, originalElement);
    }

    private @Nullable String getQuickNavigateInfo() {
      return documentationProvider.getQuickNavigateInfo(element, originalElement);
    }

    private @Nullable String generateDocForCompletion(Object completionVariant) {
      PsiElement lookupItem = documentationProvider.getDocumentationElementForLookupItem(getPsiManager(), completionVariant,
                                                                                         originalElement);
      if (lookupItem == null && completionVariant instanceof String) {
        myFixture.completeBasic();
        lookupItem = (PsiElement)Arrays.stream(myFixture.getLookupElements())
          .filter(el -> el.getLookupString().equals(completionVariant))
          .map(el -> el.getObject())
          .filter(el -> el instanceof PsiElement)
          .findFirst()
          .orElse(null);
      }
      assert lookupItem != null;
      return documentationProvider.generateDoc(lookupItem, originalElement);
    }
  }

  private void doOneTest(String fileName,
                         String lookupObject,
                         boolean testExternal,
                         boolean testForElementUnderCaret,
                         String... additional) {
    copyAdditionalFiles(additional);
    final DocumentationTestContext context = new DocumentationTestContext(fileName);

    if (testForElementUnderCaret) {
      assertNotNull("inline help for " + fileName, context.generateDoc());
      if (testExternal) {
        assertNotNull("external help", context.documentationProvider.getUrlFor(context.element, context.originalElement));
      }
    }

    if (lookupObject != null) {
      PsiElement docElement = context.documentationProvider.getDocumentationElementForLookupItem(
        context.psiFile.getManager(), lookupObject, context.originalElement);
      assertNotNull("no element for " + fileName, docElement);
      assertNotNull("inline help for lookup", context.documentationProvider.generateDoc(docElement, context.originalElement));
      if (testExternal) {
        assertNotNull("external help for lookup", context.documentationProvider.getUrlFor(docElement, context.originalElement));
      }
    }
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/xml/tests/testData/documentation/";
  }
}