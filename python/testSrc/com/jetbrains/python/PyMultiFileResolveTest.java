package com.jetbrains.python;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.jetbrains.cython.psi.CythonFunction;
import com.jetbrains.cython.psi.CythonVariable;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class PyMultiFileResolveTest extends PyResolveTestCase {
  protected String myTestFileName;

  private static void checkInitPyDir(PsiElement elt, String dirname) {
    assertTrue(elt instanceof PyFile);
    PyFile f = (PyFile)elt;
    assertEquals(f.getName(), "__init__.py");
    assertEquals(f.getContainingDirectory().getName(), dirname);
  }

  public void testSimple() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFile);
    assertEquals("ImportedFile.py", ((PyFile) element).getName());
  }

  public void testFromImport() {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import stmt
    PsiElement func_elt = results[0].getElement();
    assertTrue("is PyFunction?", func_elt instanceof PyFunction);
    assertEquals("named 'func'?", "func", ((PyFunction) func_elt).getName());
    PsiElement import_elt = results[1].getElement();
    assertTrue("is import?", import_elt instanceof PyImportElement);
  }

  public void testFromImportStar() {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import-* stmt
    PsiElement func_elt = results[0].getElement();
    assertTrue("is PyFunction?", func_elt instanceof PyFunction);
    assertEquals("named 'func'?", "func", ((PyFunction) func_elt).getName());
    PsiElement import_elt = results[1].getElement();
    assertTrue("is import?", import_elt instanceof PyStarImportElement);
  }

  public void testFromPackageImport() {
    PsiElement element = doResolve();
    checkInitPyDir(element, "mypackage");
  }

  public void testFromPackageImportFile() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PyFile) element).getName());
  }

  public void testFromQualifiedPackageImport() {
    PsiElement element = doResolve();
    checkInitPyDir(element, "mypackage");
  }

  public void testFromQualifiedFileImportClass() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PsiFile) element).getName());
    assertEquals("mypackage", ((PsiFile) element).getContainingDirectory().getName());
  }

  public void testImportAs() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
    assertEquals("func", ((PyFunction) element).getName());
  }

  public void testFromQualifiedPackageImportFile() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("testfile.py", ((PsiFile) element).getName());
  }

  public void testFromInitPyImportFunction() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
  }

  public void testTransitiveImport() {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import stmt
    PsiElement elt = results[0].getElement();
    assertTrue("is target?", elt instanceof PyTargetExpression);
  }

  public void testResolveInPkg() {
    ResolveResult[] results = doMultiResolve();
    assertTrue(results.length == 2); // func and import stmt
    PsiElement func_elt = results[0].getElement();
    assertTrue("is PyFunction?", func_elt instanceof PyFunction);
    assertEquals("named 'token'?", "token", ((PyFunction) func_elt).getName());
    PsiElement import_elt = results[1].getElement();
    assertTrue("is import?", import_elt instanceof PyImportElement);
  }

  public void testCircularImport() {
    PsiElement element = doResolve();
    assertTrue(element == null ? "resolve failed" : element.toString(), element instanceof PyTargetExpression);
  }


  public void testRelativeSimple() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("local", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testRelativeFromInit() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("unimaginable", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testRelativeDotsOnly() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("silicate", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testModuleValueCollision() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyTargetExpression);
    PsiElement value = ((PyTargetExpression)element).findAssignedValue();
    assertEquals("only kidding", ((PyStringLiteralExpression)value).getStringValue());
  }

  public void testModuleClassCollision() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyClass);
    assertEquals("boo", ((PyClass)element).getName());
  }

  public void testModulePackageCollision() {  // PY-3194
    assertResolvesTo(PyFile.class, "module1.py");
  }

  public void testDirectoryVsClass() {
    assertResolvesTo(PyClass.class, "Context");
  }

  public void testReimportStar() {
    assertResolvesTo(PyClass.class, "CharField");
  }

  public void testStackOverflowOnEmptyFile() {
    assertNull(doResolve());  // make sure we don't have a SOE here
  }

  public void testResolveQualifiedSuperClass() {
    assertResolvesTo(PyFunction.class, "copy");
  }

  public void testResolveQualifiedSuperClassInPackage() {
    assertResolvesTo(PyFunction.class, "copy");
  }

  public void testNestedPackage() {
    assertResolvesTo(PyFile.class, "__init__.py");
  }

  public void testNestedPackageElement() {
    PsiElement element = doResolve();
    element = element.getNavigationElement();
    assertInstanceOf(element, PyFile.class);
    assertEquals("__init__.py", ((PyFile) element).getName());
  }

  public void testImportOsPath() {
    assertResolvesTo(PyFunction.class, "makedir");
  }

  public void testImportOsPath2() {
    assertResolvesTo(PyFunction.class, "do_stuff");
  }

  // PY-1153
  // TODO: This case requires collecting transitive imports in all imported submodules
  public void _testReimportExported() {
    assertResolvesTo(PyFunction.class, "dostuff");
  }

  public void testFromImportExplicit() {
    assertResolvesTo(PyFunction.class, "dostuff");
  }

  public void testLocalImport() {
    assertResolvesTo(PyFunction.class, "dostuff");
  }

  public void testNameConflict() {
    assertResolvesTo(PyFunction.class, "do_stuff", "/src/pack2.py");
  }

  public void testDunderAll() {
    assertResolvesTo(PyTargetExpression.class, "__all__");
  }

  public void testDunderAllConflict() {
    assertResolvesTo(PyFunction.class, "do_stuff", "/src/mypackage1.py");
  }

  // TODO: Create package attributes for its imported submodules
  public void _testImportPackageIntoSelf() {
    assertResolvesTo(PyFunction.class, "foo", "/src/mygame/display.py");
  }

  public void testImportPackageIntoSelfInit() {
    myTestFileName = "mygame/__init__.py";
    try {
      assertResolvesTo(PyFile.class, "display.py");
    }
    finally {
      myTestFileName = null;
    }
  }

  public void testFromImportPackageIntoSelf() {
    myTestFileName = "mygame/__init__.py";
    try {
      assertResolvesTo(PyFile.class, "display.py");
    }
    finally {
      myTestFileName = null;
    }
  }

  public void testImportPrivateNameWithStar() { // PY-2717
    PsiElement psiElement = doResolve();
    assertNull(psiElement);
  }

  public void testModulePackageConflict() {  // PY-6011
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testCythonFromModuleCImport() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  public void testCythonFromModuleCImportAs() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  public void testCythonFromModuleCImportStar() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  public void testCythonFromPackageCImportAttribute() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  public void testCythonFromPackageCImportModule() {
    assertResolvesTo(PyFile.class, "m1.pxd");
  }

  public void testCythonFromPackageCImportPackage() {
    assertResolvesTo(PyFile.class, "__init__.pxd");
  }

  public void testCythonCImportAttribute() {
    assertResolvesTo(PyFile.class, "m1.pxd");
  }

  public void testCythonCImportPackage() {
    assertResolvesTo(PyFile.class, "__init__.pxd");
  }

  public void testCythonCImportModule() {
    assertResolvesTo(PyFile.class, "m1.pxd");
  }

  public void testCythonImplicitCImport() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  public void testCythonInclude() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  // PY-4843
  public void testCythonFromSubmoduleAbsoluteCImport() {
    prepareTestDirectory();
    final VirtualFile file = myFixture.findFileInTempDir("p1/m2.pyx");
    assertNotNull("Could not find test file", file);
    final PsiFile psiFile = myFixture.getPsiManager().findFile(file);
    PsiElement element = doResolve(psiFile);
    assertInstanceOf(element, CythonVariable.class);
    assertEquals("foo", ((PsiNamedElement)element).getName());
  }

  // PY-4844
  public void testCythonFromModuleCImportExternStar() {
    assertResolvesTo(CythonVariable.class, "foo");
  }

  public void _testCythonCdefClassForwardInclude() {
    // TODO: Currently we resolve named elements first and then ask name definers, so the result is the forward decl instead of the
    // includeded class
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "bar");
    final PyExpression value = target.findAssignedValue();
    assertNotNull(value);
    final PsiReference ref = value.getReference();
    assertNotNull(ref);
    final PsiElement field = ref.resolve();
    assertNotNull(field);
    assertInstanceOf(field, CythonVariable.class);
    assertEquals("foo", ((PsiNamedElement)field).getName());
  }

  public void testCythonImportFromPython() {
    assertResolvesTo(CythonFunction.class, "foo");
  }

  // PY-4946
  public void testCythonCdefClassAttributeInDefinition() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyExpression value = target.findAssignedValue();
    assertNotNull(value);
    final PsiReference ref = value.getReference();
    assertNotNull(ref);
    final PsiElement field = ref.resolve();
    assertNotNull(field);
    assertInstanceOf(field, CythonVariable.class);
    assertEquals("x", ((PsiNamedElement)field).getName());
  }

  private void prepareTestDirectory() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
  }

  private PsiFile prepareFile() {
    prepareTestDirectory();
    VirtualFile sourceFile = null;
    for (String ext : new String[] {".py", ".pyx"}) {
      final String fileName = myTestFileName != null ? myTestFileName : getTestName(false) + ext;
      sourceFile = myFixture.findFileInTempDir(fileName);
      if (sourceFile != null) {
        break;
      }
    }
    assertNotNull("Could not find test file", sourceFile);
    return myFixture.getPsiManager().findFile(sourceFile);
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/multiFile/";
  }

  protected PsiElement doResolve(PsiFile psiFile) {
    int offset = findMarkerOffset(psiFile);
    final PsiPolyVariantReference ref = (PsiPolyVariantReference) psiFile.findReferenceAt(offset);
    assertNotNull("<ref> in test file not found", ref);
    final PsiManagerImpl psiManager = (PsiManagerImpl)myFixture.getPsiManager();
    psiManager.setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        FileType fileType = file.getFileType();
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

  @Override
  protected PsiElement doResolve() {
    return doResolve(prepareFile());
  }

  private ResolveResult[] doMultiResolve() {
    PsiFile psiFile = prepareFile();
    int offset = findMarkerOffset(psiFile);
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)psiFile.findReferenceAt(offset);
    assertNotNull("<ref> in test file not found", ref);
    return ref.multiResolve(false);
  }
}
