// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.Lists;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class PyMultiFileResolveTest extends PyMultiFileResolveTestCase {

  private static void checkInitPyDir(PsiElement elt, String dirname) {
    assertTrue(elt instanceof PyFile);
    PyFile f = (PyFile)elt;
    assertEquals("__init__.py", f.getName());
    assertEquals(dirname, f.getContainingDirectory().getName());
  }

  public void testSimple() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFile);
    assertEquals("ImportedFile.py", ((PyFile)element).getName());
  }

  public void testFromImport() {
    List<PsiElement> results = doMultiResolve();
    assertSize(2, results); // func and import stmt
    PsiElement funcElt = results.get(0);
    assertTrue("is PyFunction?", funcElt instanceof PyFunction);
    assertEquals("named 'func'?", "func", ((PyFunction)funcElt).getName());
    PsiElement importElt = results.get(1);
    assertTrue("is import?", importElt instanceof PyImportElement);
  }

  public void testFromImportStar() {
    List<PsiElement> results = doMultiResolve();
    assertSize(2, results); // func and import-* stmt
    PsiElement funcElt = results.get(0);
    assertTrue("is PyFunction?", funcElt instanceof PyFunction);
    assertEquals("named 'func'?", "func", ((PyFunction)funcElt).getName());
    PsiElement importElt = results.get(1);
    assertTrue("is import?", importElt instanceof PyStarImportElement);
  }

  public void testFromPackageImport() {
    PsiElement element = doResolve();
    checkInitPyDir(element, "mypackage");
  }

  public void testFromPackageImportFile() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PyFile)element).getName());
  }

  public void testFromQualifiedPackageImport() {
    PsiElement element = doResolve();
    checkInitPyDir(element, "mypackage");
  }

  public void testFromQualifiedFileImportClass() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PsiFile)element).getName());
    assertEquals("mypackage", ((PsiFile)element).getContainingDirectory().getName());
  }

  public void testCustomPackageIdentifier() {
    PyCustomPackageIdentifier.EP_NAME.getPoint().registerExtension(new PyCustomPackageIdentifier() {
      @Override
      public boolean isPackage(PsiDirectory directory) {
        return true;
      }

      @Override
      public boolean isPackageFile(PsiFile file) {
        return false;
      }
    }, getTestRootDisposable());
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("myfile.py", ((PyFile)element).getName());
  }

  public void testImportAs() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
    assertEquals("func", ((PyFunction)element).getName());
  }

  public void testFromQualifiedPackageImportFile() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PsiFile);
    assertEquals("testfile.py", ((PsiFile)element).getName());
  }

  public void testFromInitPyImportFunction() {
    PsiElement element = doResolve();
    assertTrue(element instanceof PyFunction);
  }

  public void testTransitiveImport() {
    List<PsiElement> results = doMultiResolve();
    assertSize(2, results); // func and import stmt
    PsiElement elt = results.get(0);
    assertTrue("is target?", elt instanceof PyTargetExpression);
  }

  public void testFromPackageImportIntoInit() {  // PY-6305
    myFixture.copyDirectoryToProject("fromPackageImportIntoInit/pack", "pack");
    final PsiFile psiFile = myFixture.configureByFile("pack/__init__.py");
    final PsiElement result = doResolve(psiFile);
    assertInstanceOf(result, PyFile.class);
    assertEquals("mod.py", ((PyFile)result).getName());
  }

  public void testResolveInPkg() {
    List<PsiElement> results = doMultiResolve();
    assertSize(2, results); // func and import stmt
    final PsiElement funcElt = results.get(0);
    assertTrue("is PyFunction?", funcElt instanceof PyFunction);
    assertEquals("named 'token'?", "token", ((PyFunction)funcElt).getName());
    PsiElement importElt = results.get(1);
    assertTrue("is import?", importElt instanceof PyImportElement);
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
    assertEquals("__init__.py", ((PyFile)element).getName());
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
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> assertResolvesTo(PyFunction.class, "foo"));
  }

  public void testRelativePackageStarImport() {   // PY-7204
    myTestFileName = "b/c/__init__.py";
    try {
      assertResolvesTo(PyFunction.class, "foo", "/src/b/__init__.py");
    }
    finally {
      myTestFileName = null;
    }
  }

  // PY-2813
  public void testFromNamespacePackageImport() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-2813
  public void testNamespacePackage() {
    assertResolvesTo(PsiDirectory.class, "p1");
  }

  // PY-2813
  public void testNamespacePackageImport() {
    assertResolvesTo(PsiDirectory.class, "p1");
  }

  // PY-2813
  public void testFromNamespacePackageImportModule() {
    assertResolvesTo(PyFile.class, "m1.py");
  }

  public void testImportSubmodule() {
    assertResolvesTo(PyFile.class, "m1.py");
  }

  // PY-6575
  public void testRelativeFromSubmodule() {
    assertResolvesTo(PyFile.class, "m2.py");
  }

  public void _testImplicitFromImport() {   // PY-7929
    assertResolvesTo(PyClass.class, "Database");
  }

  // PY-6866
  public void testFunctionAndSubmoduleNamedIdentically() {
    assertResolvesTo(PyFunction.class, "m1");
  }

  // PY-7026
  public void testFromImportStarReassignment() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-7156
  public void testPython33NamespacePackage() {
    assertResolvesTo(PsiDirectory.class, "p1");
  }

  // PY-7156
  public void testFromPython33NamespacePackageImport() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-7775
  public void testProjectSourcesFirst() {
    myTestFileName = "mod/" + getTestName(false) + ".py";
    assertResolvesTo(PyFunction.class, "foobar");
  }

  // PY-6805
  public void testAttributeDefinedInNew() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-10819
  public void testFromPackageModuleImportElementNamedAsModule() {
    assertResolvesTo(PyFunction.class, "foo");
    final PsiManager psiManager = myFixture.getPsiManager();
    PyTestCase.assertNotParsed(psiManager.findFile(myFixture.findFileInTempDir("p1/__init__.py")));
    PyTestCase.assertNotParsed(psiManager.findFile(myFixture.findFileInTempDir("p1/foo.py")));
  }

  // PY-10819
  public void testFromPackageModuleImportStarElementNamedAsModule() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-13140
  public void testModulePrivateName() {
    assertNull(doResolve());
  }

  // PY-13140
  public void testModulePrivateNameInDunderAll() {
    assertResolvesTo(PyTargetExpression.class, "_private_name");
  }

  // PY-7378
  public void testModuleInDeeplyNestedNamespacePackage() {
    assertResolvesTo(PyFile.class, "m1.py");
  }

  public void testKeywordArgument() {
    final PsiFile file = prepareFile();
    final PsiManager psiManager = myFixture.getPsiManager();
    final VirtualFile dir = myFixture.findFileInTempDir("a.py");
    final PsiFile psiFile = psiManager.findFile(dir);
    //noinspection ConstantConditions   we need to unstub a.py here
    ((PsiFileImpl)psiFile).calcTreeElement();
    final PsiElement element;
    try {
      element = doResolve(file);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertResolveResult(element, PyClass.class, "A");
  }

  public void testRelativeImport() {
    assertResolvesTo(PyFile.class, "z.py");
  }

  // PY-11454
  public void testImportSubModuleDunderAll() {
    assertResolvesTo(PyFile.class, "m1.py");
  }

  // PY-11454
  public void testFromImportSubModuleDunderAll() {
    assertResolvesTo(PyFile.class, "m1.py");
  }

  // PY-17941
  public void testEmptyModuleNamesake() {
    final PsiElement module = doResolve();
    assertNotNull(module);
    final Sdk moduleSdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    assertFalse(PythonSdkUtil.isStdLib(module.getContainingFile().getVirtualFile(), moduleSdk));
  }

  // PY-18626
  public void testManySourceRoots() {
    myFixture.copyDirectoryToProject("manySourceRoots", "");
    runWithSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root1"), myFixture.findFileInTempDir("root2")), () -> {
      final PsiFile psiFile = myFixture.configureByFile("a.py");
      final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
      assertInstanceOf(ref, PsiPolyVariantReference.class);
      final List<PsiElement> elements = PyUtil.multiResolveTopPriority((PsiPolyVariantReference)ref);
      assertEquals(1, elements.size());
      final PsiElement element = elements.get(0);
      assertInstanceOf(element, PyFile.class);
      final VirtualFile file = ((PyFile)element).getVirtualFile();
      assertEquals("root1", file.getParent().getName());
    });
  }

  // PY-28321
  public void testImportManySourceRoots() {
    myFixture.copyDirectoryToProject("importManySourceRoots", "");
    runWithSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root2"), myFixture.findFileInTempDir("root1")), () -> {
      final PsiFile psiFile = myFixture.configureByFile("root1/pkg/a.py");
      final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
      assertInstanceOf(ref, PsiPolyVariantReference.class);
      final List<PsiElement> elements = PyUtil.multiResolveTopPriority((PsiPolyVariantReference)ref);
      assertEquals(1, elements.size());
      final PsiElement element = elements.get(0);
      assertInstanceOf(element, PyFile.class);
      final VirtualFile file = ((PyFile)element).getVirtualFile();
      assertEquals("m1.py", file.getName());
    });
  }

  // PY-28321
  public void testImportManySourceRootsReverseRootOrder() {
    myFixture.copyDirectoryToProject("importManySourceRoots", "");
    runWithSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root1"), myFixture.findFileInTempDir("root2")), () -> {
      final PsiFile psiFile = myFixture.configureByFile("root1/pkg/a.py");
      final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
      assertInstanceOf(ref, PsiPolyVariantReference.class);
      final List<PsiElement> elements = PyUtil.multiResolveTopPriority((PsiPolyVariantReference)ref);
      assertEquals(0, elements.size());
    });
  }

  // PY-16688
  public void testPkgResourcesNamespace() {
    doTestResolveInNamespacePackage(getTestName(true));
  }

  // PY-23087
  public void testPkgutilNamespace() {
    doTestResolveInNamespacePackage(getTestName(true));
  }

  // PY-38434
  public void testPkgutilNamespaceWithComments() {
    doTestResolveInNamespacePackage(getTestName(true));
  }

  // PY-39748
  public void testPkgResourcesNamespaceWithDocstring() {
    doTestResolveInNamespacePackage(getTestName(true));
  }

  // PY-39748
  public void testTryExceptNamespace() {
    doTestResolveInNamespacePackage(getTestName(true));
  }

  // PY-39748
  public void testTryExceptMultilineNamespace() {
    doTestResolveInNamespacePackage(getTestName(true));
  }

  private void doTestResolveInNamespacePackage(String namespace) {
    myFixture.copyDirectoryToProject(namespace, "");
    runWithSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root1"), myFixture.findFileInTempDir("root2")), () -> {
      final PsiFile psiFile = myFixture.configureByFile("root1/pkg/a.py");
      final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
      assertInstanceOf(ref, PsiPolyVariantReference.class);
      final List<PsiElement> elements = PyUtil.multiResolveTopPriority((PsiPolyVariantReference)ref);
      assertEquals(1, elements.size());
      PsiFile root1 = myFixture.getPsiManager().findFile(myFixture.findFileInTempDir("root1/pkg/__init__.py"));
      PsiFile root2 = myFixture.getPsiManager().findFile(myFixture.findFileInTempDir("root2/pkg/__init__.py"));
      assertNotParsed(root1);
      assertNotParsed(root2);
    });
  }

  // PY-22522
  public void testBothForeignAndSourceRootImportResultsReturned() {
    myFixture.copyDirectoryToProject("bothForeignAndSourceRootImportResultsReturned", "");

    VirtualFile vf = myFixture.findFileInTempDir("ext/m1.py");

    runWithSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root")), () -> {
      final PsiFile extSource = myFixture.getPsiManager().findFile(vf);
      PyImportResolver foreignResolver = (name, context, withRoots) -> name.toString().equals("m1") ? extSource : null;
      PyImportResolver.EP_NAME.getPoint().registerExtension(foreignResolver, getTestRootDisposable());

      final PsiFile psiFile = myFixture.configureByFile("a.py");
      final PsiReference ref = PyResolveTestCase.findReferenceByMarker(psiFile);
      assertInstanceOf(ref, PsiPolyVariantReference.class);
      final List<PsiElement> elements = PyUtil.multiResolveTopPriority((PsiPolyVariantReference)ref);
      assertEquals(2, elements.size());
      final Set<String> parentNames = elements.stream()
        .filter(e -> e instanceof PyFile)
        .map(e -> ((PyFile)e).getVirtualFile().getParent().getName()).collect(Collectors.toSet());
      assertContainsElements(parentNames, "root", "ext");
    });
  }

  // PY-19989
  public void testAmbiguousImplicitRelativeImport() {
    prepareTestDirectory();
    assertSameElements(doMultiResolveAndGetFileUrls("pkg2/__init__.py"), "pkg2/mod.py");
    assertSameElements(doMultiResolveAndGetFileUrls("pkg/__init__.py"), "pkg/mod.py");
  }

  // PY-21088
  public void testDontResolveToMissingNameInDynamicDunderAll() {
    assertNull(doResolve());
  }

  @NotNull
  private List<String> doMultiResolveAndGetFileUrls(@NotNull String currentFilePath) {
    myFixture.configureByFile(currentFilePath);
    final PsiReference reference = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    final VirtualFile root = ModuleRootManager.getInstance(myFixture.getModule()).getSourceRoots()[0];

    final Stream<PsiFileSystemItem> fileSystemItems;
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      fileSystemItems = Arrays.stream(results).map(r -> PyPsiUtils.getFileSystemItem(r.getElement()));
    }
    else {
      fileSystemItems = Stream.of(PyPsiUtils.getFileSystemItem(reference.resolve()));
    }
    return fileSystemItems.map(f -> VfsUtilCore.getRelativeLocation(f.getVirtualFile(), root)).collect(Collectors.toList());
  }

  public void testCustomMemberTargetClass() {
    prepareTestDirectory();

    final PyCustomMember customMember = new PyCustomMember("Clazz").resolvesToClass("pkg.mod1.Clazz");
    final PsiFile context = myFixture.configureByText("a.py", "");

    final TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(myFixture.getProject(), context);
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);

    final PsiElement resolved = customMember.resolve(context, resolveContext);
    assertInstanceOf(resolved, PyTypedElement.class);

    final PyType type = typeEvalContext.getType((PyTypedElement)resolved);
    assertInstanceOf(type, PyClassType.class);
  }

  public void testImportAliasTargetReference() {
    assertResolvesTo(PyTargetExpression.class, "bar");
  }

  //PY-28685
  public void testImplicitImportSelf() {
    prepareTestDirectory();
    assertSameElements(doMultiResolveAndGetFileUrls("pkg1/pkg2/mod1.py"), "pkg1/pkg2/mod1.py");
  }

  public void testImportResolvesToPkgInit() {
    prepareTestDirectory();
    assertSameElements(doMultiResolveAndGetFileUrls("pkg1/pkg2/mod1.py"), "pkg1/pkg2/__init__.py");
  }

  // EA-121262
  public void testIncompleteFromImport() {
    assertUnresolved();
  }

  // PY-38322
  public void testDunderAllDynamicallyBuiltInHelperFunction() {
    assertResolvesTo(PyTargetExpression.class, "bar");
  }

  // PY-38322 PY-39171
  public void testImportOfNestedBinarySubModule() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      runWithAdditionalClassEntryInSdkRoots(testDir + "/python_stubs", () -> {
        assertResolvesTo(PyFunction.class, "func");
      });
    });
  }

  // PY-45541
  public void testCanonicalNameOfNumpyNdarray() {
    final String testDir = getTestName(true);
    runWithAdditionalClassEntryInSdkRoots(testDir + "/site-packages", () -> {
      runWithAdditionalClassEntryInSdkRoots(testDir + "/python_stubs", () -> {
        PyClass ndarrayClass = PyClassNameIndex.findClass("numpy.core._multiarray_umath.ndarray", myFixture.getProject());
        QualifiedName canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(ndarrayClass, null);
        assertEquals(QualifiedName.fromDottedString("numpy"), canonicalImportPath);
      });
    });
  }

  // PY-49156
  public void testFromPackageImportIntoInitConflictWithAssignment() {
    myFixture.copyDirectoryToProject("fromPackageImportIntoInitConflictWithAssignment/pack", "pack");
    final PsiFile psiFile = myFixture.configureByFile("pack/__init__.py");
    final PsiElement result = doResolve(psiFile);
    assertInstanceOf(result, PyFile.class);
    assertEquals("mod.py", ((PyFile)result).getName());
  }
}
