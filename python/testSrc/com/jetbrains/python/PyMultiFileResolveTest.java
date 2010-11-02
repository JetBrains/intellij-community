package com.jetbrains.python;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class PyMultiFileResolveTest extends PyResolveTestCase {
  
  private static void checkInitPyDir(PsiElement elt, String dirname) throws Exception {
    assertTrue(elt instanceof PyFile);
    PyFile f = (PyFile)elt;
    assertEquals(f.getName(), "__init__.py");
    assertEquals(f.getContainingDirectory().getName(), dirname);
  }

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

  public void testFromPackageImport() throws Exception {
    PsiElement element = doResolve();
    checkInitPyDir(element, "mypackage");
  }

  public void testFromPackageImportFile() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PyFile) element).getName());
  }

  public void testFromQualifiedPackageImport() throws Exception {
    PsiElement element = doResolve();
    checkInitPyDir(element, "mypackage");
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

  public void testCircularImport() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element == null ? "resolve failed" : element.toString(), element instanceof PyTargetExpression);
  }


  public void testRelativeSimple() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("local", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testRelativeFromInit() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("unimaginable", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testRelativeDotsOnly() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("silicate", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testModuleValueCollision() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("only kidding", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testModuleClassCollision() throws Exception {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyClass);
    assertEquals("boo", ((PyClass)element).getName());
  }

  public void testDirectoryVsClass() throws Exception {
    assertResolvesTo(PyClass.class, "Context");
  }

  public void testReimportStar() throws Exception {
    assertResolvesTo(PyClass.class, "CharField");
  }

  public void testStackOverflowOnEmptyFile() throws Exception {
    assertNull(doResolve());  // make sure we don't have a SOE here
  }

  public void testResolveQualifiedSuperClass() throws Exception {
    assertResolvesTo(PyFunction.class, "copy");
  }

  public void testResolveQualifiedSuperClassInPackage() throws Exception {
    assertResolvesTo(PyFunction.class, "copy");
  }

  public void testNestedPackage() throws Exception {
    assertResolvesTo(PyFile.class, "__init__.py");
  }

  public void testNestedPackageElement() throws Exception {
    PsiElement element = doResolve();
    element = element.getNavigationElement();
    assertInstanceOf(element, PyFile.class);
    assertEquals("__init__.py", ((PyFile) element).getName());
  }

  public void testImportOsPath() throws Exception {
    assertResolvesTo(PyFunction.class, "makedir");
  }

  public void testImportOsPath2() throws Exception {
    assertResolvesTo(PyFunction.class, "do_stuff");
  }

  public void testReimportExported() throws Exception {
    assertResolvesTo(PyFunction.class, "dostuff");
  }

  public void testFromImportExplicit() throws Exception {
    assertResolvesTo(PyFunction.class, "dostuff");
  }

  public void testLocalImport() throws Exception {
    assertResolvesTo(PyFunction.class, "dostuff");
  }

  public void testNameConflict() throws Exception {
    assertResolvesTo(PyFunction.class, "do_stuff", "/src/pack2.py");
  }

  public void testDunderAll() {
    assertResolvesTo(PyTargetExpression.class, "__all__");
  }

  public void testDunderAllImport() {
    assertResolvesTo(PyTargetExpression.class, "__all__");
  }

  public void testDunderAllImportResolve() {
    assertResolvesTo(PyTargetExpression.class, "__all__");
  }

  public void testDunderAllConflict() {
    assertResolvesTo(PyFunction.class, "do_stuff", "/src/mypackage1.py");
  }

  public void testImportPackageIntoSelf() {
    assertResolvesTo(PyFunction.class, "foo", "/src/mygame/display.py");
  }

  private PsiFile prepareFile() throws Exception {
    String testName = getTestName(true);
    String fileName = getTestName(false) + ".py";
    myFixture.copyDirectoryToProject(testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

    VirtualFile sourceFile = myFixture.findFileInTempDir(fileName);
    assert sourceFile != null: "Could not find file in temp dir: " + fileName;
    PsiFile psiFile = myFixture.getPsiManager().findFile(sourceFile);
    return psiFile;
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/multiFile/";
  }

  @Override
  protected PsiElement doResolve() throws Exception {
    PsiFile psiFile = prepareFile();
    int offset = findMarkerOffset(psiFile);
    final PsiPolyVariantReference ref = (PsiPolyVariantReference) psiFile.findReferenceAt(offset);
    final PsiManagerImpl psiManager = (PsiManagerImpl)myFixture.getPsiManager();
    psiManager.setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == PythonFileType.INSTANCE;
      }
    });
    try {
      final ResolveResult[] resolveResults = ref.multiResolve(false);
      if (resolveResults.length == 0) {
        return null;
      }
      return resolveResults[0].isValidResult() ? resolveResults [0].getElement() : null;
    }
    finally {
      psiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
    }
  }

  private ResolveResult[] doMultiResolve() throws Exception {
    PsiFile psiFile = prepareFile();
    int offset = findMarkerOffset(psiFile);
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)psiFile.findReferenceAt(offset);
    return ref.multiResolve(false);
  }
}
