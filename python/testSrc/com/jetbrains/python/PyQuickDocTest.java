// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.TestDataFile;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;

import java.io.IOException;
import java.util.Map;

/**
 * @author dcheryasov
 */
public class PyQuickDocTest extends LightMarkedTestCase {
  private PythonDocumentationProvider myProvider;
  private DocStringFormat myFormat;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // the provider is stateless, can be reused, as in real life
    myProvider = new PythonDocumentationProvider();
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    myFormat = documentationSettings.getFormat();
    documentationSettings.setFormat(DocStringFormat.PLAIN);
  }

  @Override
  public void tearDown() throws Exception {
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(myFormat);
    super.tearDown();
  }

  private void checkByHTML(String text) {
    assertNotNull(text);
    checkByHTML(text, "/quickdoc/" + getTestName(false) + ".html");
  }

  private void checkByHTML(String text, @TestDataFile String filePath) {
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile virtualFile = PyTestCase.getVirtualFileByName(fullPath);
    assertNotNull("file " + fullPath + " not found", virtualFile);

    String loadedText;
    try {
      loadedText = VfsUtilCore.loadText(virtualFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    String fileText = StringUtil.convertLineSeparators(loadedText, "\n");
    assertEquals(fileText.trim(), text.trim());
  }

  @Override
  protected Map<String, PsiElement> loadTest() {
    return configureByFile("/quickdoc/" + getTestName(false) + ".py");
  }

  private void checkRefDocPair() {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    final PsiElement originalElement = marks.get("<the_doc>");
    PsiElement docElement = originalElement.getParent(); // ident -> expr
    assertTrue(docElement instanceof PyStringLiteralExpression);
    String stringValue = ((PyStringLiteralExpression)docElement).getStringValue();
    assertNotNull(stringValue);

    PsiElement referenceElement = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner docOwner = (PyDocStringOwner)((PyReferenceExpression)referenceElement).getReference().resolve();
    assertNotNull(docOwner);
    assertEquals(docElement, docOwner.getDocStringExpression());

    checkByHTML(myProvider.generateDoc(docOwner, originalElement));
  }

  private void checkHTMLOnly() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    PsiElement referenceElement = originalElement.getParent(); // ident -> expr
    final PsiElement docOwner = ((PyReferenceExpression)referenceElement).getReference().resolve();
    checkByHTML(myProvider.generateDoc(docOwner, originalElement));
  }

  private void checkHover() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    PsiElement referenceElement = originalElement.getParent(); // ident -> expr
    final PsiElement docOwner = ((PyReferenceExpression)referenceElement).getReference().resolve();
    checkByHTML(myProvider.getQuickNavigateInfo(docOwner, referenceElement));
  }

  public void testDirectFunc() {
    checkRefDocPair();
  }

  public void testIndented() {
    checkRefDocPair();
  }

  public void testDirectClass() {
    checkRefDocPair();
  }

  public void testClassConstructor() {
    checkRefDocPair();
  }

  public void testClassUndocumentedConstructor() {
    checkHTMLOnly();
  }

  public void testClassUndocumentedEmptyConstructor() {
    checkHTMLOnly();
  }

  public void testCallFunc() {
    checkRefDocPair();
  }

  public void testModule() {
    checkRefDocPair();
  }

  public void testMethod() {
    checkRefDocPair();
  }

  // PY-3496
  public void testVariable() {
    checkHTMLOnly();
  }

  public void testInheritedMethod() {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    PsiElement docElement = marks.get("<the_doc>").getParent(); // ident -> expr
    assertTrue(docElement instanceof PyStringLiteralExpression);
    String docText = ((PyStringLiteralExpression)docElement).getStringValue();
    assertNotNull(docText);

    PsiElement ref_elt = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner docOwner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).getReference().resolve();
    assertNotNull(docOwner);
    assertNull(docOwner.getDocStringExpression()); // no direct doc!

    checkByHTML(myProvider.generateDoc(docOwner, null));
  }

  public void testPropNewGetter() {
    checkHTMLOnly();
  }

  public void testPropNewSetter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> {
        Map<String, PsiElement> marks = loadTest();
        PsiElement referenceElement = marks.get("<the_ref>");
        final PyDocStringOwner docStringOwner = (PyDocStringOwner)referenceElement.getParent().getReference().resolve();
        checkByHTML(myProvider.generateDoc(docStringOwner, referenceElement));
      }
    );
  }

  public void testPropNewDeleter() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> {
        Map<String, PsiElement> marks = loadTest();
        PsiElement referenceElement = marks.get("<the_ref>");
        final PyDocStringOwner docStringOwner = (PyDocStringOwner)((PyReferenceExpression)(referenceElement.getParent())).getReference().resolve();
        checkByHTML(myProvider.generateDoc(docStringOwner, referenceElement));
      }
    );
  }

  public void testPropOldGetter() {
    checkHTMLOnly();
  }


  public void testPropOldSetter() {
    Map<String, PsiElement> marks = loadTest();
    PsiElement referenceElement = marks.get("<the_ref>");
    final PyDocStringOwner docStringOwner = (PyDocStringOwner)referenceElement.getParent().getReference().resolve();
    checkByHTML(myProvider.generateDoc(docStringOwner, referenceElement));
  }

  public void testPropOldDeleter() {
    checkHTMLOnly();
  }

  public void testParam() {
    checkHTMLOnly();
  }

  public void testInstanceAttr() {
    checkHTMLOnly();
  }

  public void testClassAttr() {
    checkHTMLOnly();
  }

  public void testHoverOverClass() {
    checkHover();
  }

  public void testHoverOverFunction() {
    checkHover();
  }

  public void testHoverOverMethod() {
    checkHover();
  }

  public void testHoverOverParameter() {
    checkHover();
  }

  public void testHoverOverControlFlowUnion() {
    checkHover();
  }

  public void testReturnKeyword() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");
    checkByHTML(myProvider.generateDoc(originalElement, originalElement));
  }

  // PY-13422
  public void testNumPyOnesDoc() {
    myFixture.copyDirectoryToProject("/quickdoc/" + getTestName(false), "");
    checkHover();
  }

  // PY-17705
  public void testOptionalParameterType() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testHomogeneousTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testHeterogeneousTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testUnknownTuple() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  public void testTypeVars() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }
  
  // PY-22730
  public void testOptionalAndUnionTypesContainingTypeVars() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, this::checkHTMLOnly);
  }

  // PY-22685
  public void testBuiltinLen() {
    checkHTMLOnly();
  }

  public void testArgumentList() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement);
    checkByHTML(myProvider.generateDoc(element, originalElement));
  }

  public void testNotArgumentList() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement);
    assertNull(element);
  }

  public void testDocstring() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement originalElement = marks.get("<the_ref>");

    final PsiElement element = myProvider.getCustomDocumentationElement(myFixture.getEditor(), myFile, originalElement);
    checkByHTML(myProvider.generateDoc(element, originalElement));
  }

  public void testReferenceToMethodQualifiedWithInstance() {
    checkHTMLOnly();
  }

  public void testOneDecoratorFunction() {
    checkHTMLOnly();
  }

  public void testHoverOverOneDecoratorFunction() {
    checkHover();
  }

  public void testManyDecoratorsFunction() {
    checkHTMLOnly();
  }

  public void testHoverOverManyDecoratorsFunction() {
    checkHover();
  }

  public void testOneDecoratorClass() {
    checkHTMLOnly();
  }

  public void testHoverOverOneDecoratorClass() {
    checkHover();
  }

  public void testManyDecoratorsClass() {
    checkHTMLOnly();
  }

  public void testHoverOverManyDecoratorsClass() {
    checkHover();
  }

  public void testClassWithAllKindSuperClassExpressions() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::checkHTMLOnly);
  }

  public void testHoverOverClassWithAllKindSuperClassExpressions() {
    runWithLanguageLevel(LanguageLevel.PYTHON30, this::checkHover);
  }

  // PY-23247
  public void testOverloads() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-23247
  public void testHoverOverOverloads() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHover);
  }

  // PY-23247
  public void testOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHTMLOnly);
  }

  // PY-23247
  public void testHoverOverOverloadsAndImplementation() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, this::checkHover);
  }

  // PY-23247
  public void testDocOnImplementationWithOverloads() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PsiElement originalElement = loadTest().get("<the_ref>");
        checkByHTML(myProvider.generateDoc(originalElement.getParent(), originalElement));
      }
    );
  }
}
