package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.io.File;
import java.util.Map;

/**
 * TODO: Add description
 * User: dcheryasov
 * Date: Jun 7, 2009 12:31:07 PM
 */
public class PyQuickDocTest extends LightMarkedTestCase {
  private PythonDocumentationProvider myProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // the provider is stateless, can be reused, as in real life
    myProvider = new PythonDocumentationProvider();
  }

  private void checkByHTML(String text) throws Exception {
    assertNotNull(text);
    String filePath = "/quickdoc/" + getTestName(false) + ".html";
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + fullPath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    assertEquals(fileText.trim(), text.trim());
  }

  @Override
  protected Map<String, PsiElement> loadTest() throws Exception {
    return configureByFile("/quickdoc/" + getTestName(false) + ".py");
  }

  private void checkRefDocPair() throws Exception {
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

  private void checkHTMLOnly() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    final PsiElement original_elt = marks.get("<the_ref>");
    PsiElement ref_elt = original_elt.getParent(); // ident -> expr
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).getReference().resolve();
    checkByHTML(myProvider.generateDoc(doc_owner, original_elt));
  }

  public void testDirectFunc() throws Exception {
    checkRefDocPair();
  }

  public void testDirectClass() throws Exception {
    checkRefDocPair();
  }

  public void testClassConstructor() throws Exception {
    checkRefDocPair();
  }

  public void testClassUndocumentedConstructor() throws Exception {
    checkHTMLOnly();
  }

  public void testClassUndocumentedEmptyConstructor() throws Exception {
    checkHTMLOnly();
  }

  public void testCallFunc() throws Exception {
    checkRefDocPair();
  }

  public void testModule() throws Exception {
    checkRefDocPair();
  }

  public void testMethod() throws Exception {
    checkRefDocPair();
  }

  public void testInheritedMethod() throws Exception {
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

  public void testPropNewGetter() throws Exception {
    checkHTMLOnly();
  }

  public void testPropNewSetter() throws Exception {
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

  public void testPropNewDeleter() throws Exception {
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

  public void testPropOldGetter() throws Exception {
    checkHTMLOnly();
  }


  public void testPropOldSetter() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    PsiElement ref_elt = marks.get("<the_ref>");
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyTargetExpression)(ref_elt.getParent())).getReference().resolve();
    checkByHTML(myProvider.generateDoc(doc_owner, ref_elt));
  }

  public void testPropOldDeleter() throws Exception {
    checkHTMLOnly();
  }
}
