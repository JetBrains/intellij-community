package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;

import java.io.File;
import java.util.Map;

/**
 * TODO: Add description
 * User: dcheryasov
 * Date: Jun 7, 2009 12:31:07 PM
 */
public class PyQuickDocTest extends MarkedTestCase {
  private PythonDocumentationProvider myProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // the provider is stateless, can be reused, as in real life
    myProvider = new PythonDocumentationProvider();
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/quickdoc/";
  }

  private void checkByHTML(String text) throws Exception {
    assertNotNull(text);
    String filePath = getTestName(false) + ".html";
    final String fullPath = getTestDataPath() + filePath;
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    assertNotNull("file " + filePath + " not found", vFile);

    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");
    assertEquals(fileText.trim(), text.trim());
  }

  private void processRefDocPair() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    PsiElement doc_elt = marks.get("<the_doc>").getParent(); // ident -> expr
    assertTrue(doc_elt instanceof PyStringLiteralExpression);
    String doc_text = ((PyStringLiteralExpression)doc_elt).getStringValue();
    assertNotNull(doc_text);

    PsiElement ref_elt = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).resolve();
    assertEquals(doc_owner.getDocStringExpression(), doc_elt);

    checkByHTML(myProvider.generateDoc(doc_owner, null));
  }

  public void testDirectFunc() throws Exception {
    processRefDocPair();
  }

  public void testDirectClass() throws Exception {
    processRefDocPair();
  }

  public void testModule() throws Exception {
    processRefDocPair();
  }

  public void testMethod() throws Exception {
    processRefDocPair();
  }

  public void testInheritedMethod() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    PsiElement doc_elt = marks.get("<the_doc>").getParent(); // ident -> expr
    assertTrue(doc_elt instanceof PyStringLiteralExpression);
    String doc_text = ((PyStringLiteralExpression)doc_elt).getStringValue();
    assertNotNull(doc_text);

    PsiElement ref_elt = marks.get("<the_ref>").getParent(); // ident -> expr
    final PyDocStringOwner doc_owner = (PyDocStringOwner)((PyReferenceExpression)ref_elt).resolve();
    assertNull(doc_owner.getDocStringExpression()); // no direct doc!

    checkByHTML(myProvider.generateDoc(doc_owner, null));
  }
}
