package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.TestDataFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author dcheryasov
 */
public class PyQuickDocTest extends LightMarkedTestCase {
  private PythonDocumentationProvider myProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // the provider is stateless, can be reused, as in real life
    myProvider = new PythonDocumentationProvider();
  }

  private void checkByHTML(String text) {
    assertNotNull(text);
    checkByHTML(text, "/quickdoc/" + getTestName(false) + ".html");
  }

  private void checkByHTML(String text, @TestDataFile String filePath) {
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);

    String loadedText;
    try {
      loadedText = VfsUtil.loadText(vFile);
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
    final PsiElement original_elt = marks.get("<the_doc>");
    PsiElement doc_elt = original_elt.getParent(); // ident -> expr
    assertTrue(doc_elt instanceof PyStringLiteralExpression);
    String doc_text = ((PyStringLiteralExpression)doc_elt).getStringValue();
    assertNotNull(doc_text);

    PsiElement ref_elt = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).getReference().resolve();
    assertEquals(doc_elt, doc_owner.getDocStringExpression());

    checkByHTML(myProvider.generateDoc(doc_owner, original_elt));
  }

  private void checkHTMLOnly() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement original_elt = marks.get("<the_ref>");
    PsiElement ref_elt = original_elt.getParent(); // ident -> expr
    final PsiElement doc_owner = ((PyReferenceExpression)ref_elt).getReference().resolve();
    checkByHTML(myProvider.generateDoc(doc_owner, original_elt));
  }

  private void checkHover() {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement original_elt = marks.get("<the_ref>");
    PsiElement ref_elt = original_elt.getParent(); // ident -> expr
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).getReference().resolve();
    checkByHTML(myProvider.getQuickNavigateInfo(doc_owner, original_elt));
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

  public void testInheritedMethod() {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    PsiElement doc_elt = marks.get("<the_doc>").getParent(); // ident -> expr
    assertTrue(doc_elt instanceof PyStringLiteralExpression);
    String doc_text = ((PyStringLiteralExpression)doc_elt).getStringValue();
    assertNotNull(doc_text);

    PsiElement ref_elt = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).getReference().resolve();
    assertNull(doc_owner.getDocStringExpression()); // no direct doc!

    checkByHTML(myProvider.generateDoc(doc_owner, null));
  }

  public void testPropNewGetter() {
    checkHTMLOnly();
  }

  public void testPropNewSetter() {
    Map<String, PsiElement> marks = loadTest();
    PsiElement ref_elt = marks.get("<the_ref>");
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON26);
    try {
      final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyTargetExpression)(ref_elt.getParent())).getReference().resolve();
      checkByHTML(myProvider.generateDoc(doc_owner, ref_elt));
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testPropNewDeleter() {
    Map<String, PsiElement> marks = loadTest();
    PsiElement ref_elt = marks.get("<the_ref>");
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON26);
    try {
      final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)(ref_elt.getParent())).getReference().resolve();
      checkByHTML(myProvider.generateDoc(doc_owner, ref_elt));
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testPropOldGetter() {
    checkHTMLOnly();
  }


  public void testPropOldSetter() {
    Map<String, PsiElement> marks = loadTest();
    PsiElement ref_elt = marks.get("<the_ref>");
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyTargetExpression)(ref_elt.getParent())).getReference().resolve();
    checkByHTML(myProvider.generateDoc(doc_owner, ref_elt));
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
}
