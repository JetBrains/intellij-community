package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @by maxim
 */
@SuppressWarnings("ConstantConditions")
public class XmlDocumentationTest extends DaemonAnalyzerTestCase {

  public void testXmlDoc() throws Exception {
    doOneTest("1.xml", "display-name", false, "web-app_2_3.dtd");
    doOneTest("2.xml", null, false, "web-app_2_4.xsd");
    doOneTest("3.xml", null, false, "web-app_2_4.xsd", "j2ee_1_4.xsd");
    doOneTest("3_2.xml", null, false, "web-app_2_4.xsd", "j2ee_1_4.xsd");
    doOneTest("3_3.xml", null, false, "web-app_2_4.xsd", "j2ee_1_4.xsd");

    doOneTest("4.xml", "context-param", false, false, "web-app_2_4.xsd");
    doOneTest("5.xml", "aaa:context-param", false, false, "web-app_2_4.xsd");
    doOneTest("6.xsd", "xs:complexType", true, true);
    doOneTest("7.xml", "bbb", false);
    doOneTest("8.xml", "bbb", false);
    doOneTest("9.xml", "laquo", false);
  }

  public void testXmlDocWithCData() throws Exception {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml","spring-beans.xsd");
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + "2.xml","spring-beans.xsd");
  }

  public void testXmlDoc2() throws Exception {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml", "web-app_2_4.xsd");
  }

  public void testXmlDoc3() throws Exception {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml", "hibernate-mapping-3.0.dtd");
  }

  public void testXmlDoc4() throws Exception {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult(testName + ".xml", testName + ".xsd");
  }

  public void testSchemaPrefix() throws Exception {
    DocumentationTestContext context = new DocumentationTestContext("SchemaPrefix.xml");
    assertEquals("XML Namespace Prefix \"xs\" (http://www.w3.org/2001/XMLSchema)", context.getQuickNavigateInfo());
  }

  public void testXmlDoc6() throws Exception {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"car",testName + ".xml", testName + ".xsd");
  }

  public void testXmlDoc7() throws Exception {
    final String testName = getTestName(false);
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"$Paste",testName + ".xml", testName + ".xsd");
  }

  private void doQuickDocGenerationTestWithCheckExpectedResult(final String... baseFileNames) throws Exception {
    doQuickDocGenerationTestWithCheckExpectedResult(null, baseFileNames);
  }

  private void doQuickDocGenerationTestWithCheckExpectedResult(Object completionVariant, final String... baseFileNames) throws Exception {
    final DocumentationTestContext context = new DocumentationTestContext(baseFileNames);
    String pathname = getTestDataPath() + baseFileNames[0] + ".expected.html";
    VirtualFile vfile = LocalFileSystem.getInstance().findFileByIoFile(new File(pathname));
    assertNotNull(pathname + " not found", vfile);
    String expectedText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vfile));
    String text = context.generateDoc();
    assertNotNull(text);
    assertEquals(expectedText, StringUtil.convertLineSeparators(text));

    if (completionVariant != null) {
      vfile = LocalFileSystem.getInstance().findFileByIoFile(new File(getTestDataPath() +baseFileNames[0] + ".expected.completion.html"));
      expectedText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vfile), "\n");
      assertEquals(expectedText, StringUtil.convertLineSeparators(context.generateDocForCompletion(completionVariant), "\n"));
    }
  }

  public void testDtdDoc() throws Exception {
    doOneTest("dtd.dtd", "foo", false, true, "web-app_2_4.xsd");
    doOneTest("dtd.xml", "foo", false, true, "web-app_2_4.xsd");
  }

  private void doOneTest(String fileName, String lookupObject, boolean testExternal, String... additional) throws Exception {
    configureByFiles(null, additional);
    doOneTest(fileName, lookupObject, testExternal, true, "web-app_2_4.xsd");
  }

  @SuppressWarnings("ConstantConditions")
  public class DocumentationTestContext {
    final DocumentationProvider documentationProvider;
    final PsiElement originalElement;
    PsiElement element;
    final PsiFile psiFile;

    DocumentationTestContext(String... fileNames) throws Exception {
      configureByFiles(null,fileNames);
      psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
      originalElement = psiFile.findElementAt(myEditor.getCaretModel().getOffset());
      element = DocumentationManager.getInstance(myProject).findTargetElement(myEditor, myFile, originalElement);

      if (element == null) {
        element = originalElement;
      }

      documentationProvider = DocumentationManager.getProviderFromElement(element);
    }

    @Nullable
    String generateDoc() {
      return documentationProvider.generateDoc(element, originalElement);
    }

    @Nullable
    String getQuickNavigateInfo() {
      return documentationProvider.getQuickNavigateInfo(element, originalElement);
    }

    @Nullable
    public String generateDocForCompletion(Object completionVariant) {
      PsiElement lookupItem = documentationProvider.getDocumentationElementForLookupItem(myPsiManager, completionVariant, originalElement);
      assert lookupItem != null;
      return documentationProvider.generateDoc(lookupItem, originalElement);
    }
  }

  private void doOneTest(String fileName, String lookupObject, boolean testExternal, boolean testForElementUnderCaret, String... additional) throws Exception {

    configureByFiles(null, additional);
    final DocumentationTestContext context = new DocumentationTestContext(fileName);

    if (testForElementUnderCaret) {
      assertNotNull( "inline help for " + fileName, context.generateDoc() );
      if (testExternal) {
        assertNotNull( "external help", context.documentationProvider.getUrlFor(context.element, context.originalElement) );
      }
    }

    if(lookupObject!=null) {
      PsiElement docElement = context.documentationProvider.getDocumentationElementForLookupItem(
        context.psiFile.getManager(), lookupObject,context.originalElement);
      assertNotNull("no element for " + fileName, docElement);
      assertNotNull( "inline help for lookup", context.documentationProvider.generateDoc(docElement, context.originalElement) );
      if (testExternal) {
        assertNotNull( "external help for lookup", context.documentationProvider.getUrlFor(docElement, context.originalElement) );
      }
    }
  }

  public void testScopeAttribute() throws Exception {
    doQuickDocGenerationTestWithCheckExpectedResult(getTestName(false) + ".xml","spring-beans.xsd");
  }

  public void testXslCompletion() throws Exception {
    doQuickDocGenerationTestWithCheckExpectedResult((Object)"apply-imports", "xslCompletion.xsl");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/xml/tests/testData/documentation/";
  }
}