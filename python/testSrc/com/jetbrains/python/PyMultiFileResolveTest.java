package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class PyMultiFileResolveTest extends CodeInsightTestCase {
  public void testSimple() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFile);
    assertEquals("ImportedFile.py", ((PyFile) element).getName());
  }

  public void testFromImport() throws Exception {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import stmt
    PsiElement func_elt = results[0].getElement();
    assertTrue("is PyFunction?", func_elt instanceof PyFunction);
    assertEquals("named 'func'?", "func", ((PyFunction) func_elt).getName());
    PsiElement import_elt = results[1].getElement();
    assertTrue("is import?", import_elt instanceof PyImportElement);
  }

  public void testFromImportStar() throws Exception {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import-* stmt
    PsiElement func_elt = results[0].getElement();
    assertTrue("is PyFunction?", func_elt instanceof PyFunction);
    assertEquals("named 'func'?", "func", ((PyFunction) func_elt).getName());
    PsiElement import_elt = results[1].getElement();
    assertTrue("is import?", import_elt instanceof PyStarImportElement);
  }

  protected void _checkInitPyDir(PsiElement elt, String dirname) throws Exception {
    assertTrue(elt instanceof PyFile);
    PyFile f = (PyFile)elt;
    assertEquals(f.getName(), "__init__.py");
    assertEquals(f.getContainingDirectory().getName(), dirname);
  }

  public void testFromPackageImport() throws Exception {
    PsiElement element = doResolve();
    _checkInitPyDir(element, "mypackage");
  }

  public void testFromPackageImportFile() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PyFile) element).getName());
  }

  public void testFromQualifiedPackageImport() throws Exception {
    PsiElement element = doResolve();
    _checkInitPyDir(element, "mypackage");
  }

  public void testFromQualifiedFileImportClass() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PsiFile) element).getName());
    assertEquals("mypackage", ((PsiFile) element).getContainingDirectory().getName());
  }

  public void testImportAs() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
    assertEquals("func", ((PyFunction) element).getName());
  }

  public void testFromQualifiedPackageImportFile() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("testfile.py", ((PsiFile) element).getName());
  }

  public void testFromInitPyImportFunction() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
  }

  public void testTransitiveImport() throws Exception {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import stmt
    PsiElement elt = results[0].getElement();
    assertTrue("is target?", elt instanceof PyTargetExpression);
  }

  public void testResolveInPkg() throws Exception {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import stmt
    PsiElement func_elt = results[0].getElement();
    assertTrue("is PyFunction?", func_elt instanceof PyFunction);
    assertEquals("named 'token'?", "token", ((PyFunction) func_elt).getName());
    PsiElement import_elt = results[1].getElement();
    assertTrue("is import?", import_elt instanceof PyImportElement);
  }

  // Currently fails due to inadequate stubs
  public void testCircularImport() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
  }


  private PsiFile prepareFile() throws Exception {
    String testName = getTestName(true);
    String fileName = getTestName(false) + ".py";
    String root = PythonTestUtil.getTestDataPath() + "/resolve/multiFile/" + testName;
    VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete, false);
    PsiTestUtil.addSourceContentToRoots(myModule, rootDir);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    VirtualFile sourceFile = rootDir.findChild(fileName);
    assert sourceFile != null;
    PsiFile psiFile = myPsiManager.findFile(sourceFile);
    return psiFile;
  }

  private PsiElement doResolve() throws Exception {
    PsiFile psiFile = prepareFile();
    int offset = findMarkerOffset(psiFile);
    final PsiReference ref = psiFile.findReferenceAt(offset);
    return ref.resolve();
  }

  private ResolveResult[] doMultiResolve() throws Exception {
    PsiFile psiFile = prepareFile();
    int offset = findMarkerOffset(psiFile);
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)psiFile.findReferenceAt(offset);
    return ref.multiResolve(false);
  }

  private int findMarkerOffset(final PsiFile psiFile) {
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    assert document != null;
    int offset = -1;
    for (int i=1; i<document.getLineCount(); i++) {
      int lineStart = document.getLineStartOffset(i);
      int lineEnd = document.getLineEndOffset(i);
      final int index=document.getCharsSequence().subSequence(lineStart, lineEnd).toString().indexOf("<ref>");
      if (index>0) {
        offset = document.getLineStartOffset(i-1) + index;
      }
    }
    assert offset != -1;
    return offset;
  }
}
