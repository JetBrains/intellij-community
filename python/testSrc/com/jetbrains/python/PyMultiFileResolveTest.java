// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author yole
 */
public class PyMultiFileResolveTest extends PyMultiFileResolveTestCase {

  private static void checkInitPyDir(PsiElement elt, String dirname) {
    assertTrue(elt instanceof PyFile);
    PyFile f = (PyFile)elt;
    assertEquals(f.getName(), "__init__.py");
    assertEquals(f.getContainingDirectory().getName(), dirname);
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
    PlatformTestUtil.registerExtension(PyCustomPackageIdentifier.EP_NAME, new PyCustomPackageIdentifier() {
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
    assertResolvesTo(PyFunction.class, "foo");
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> assertResolvesTo(PsiDirectory.class, "p1"));
  }

  // PY-7156
  public void testFromPython33NamespacePackageImport() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> assertResolvesTo(PyFunction.class, "foo"));
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> assertResolvesTo(PyFile.class, "m1.py"));
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
    final Sdk moduleSdk = PythonSdkType.findPythonSdk(myFixture.getModule());
    assertFalse(PythonSdkType.isStdLib(module.getContainingFile().getVirtualFile(), moduleSdk));
  }

  // PY-18626
  public void testManySourceRoots() {
    myFixture.copyDirectoryToProject("manySourceRoots", "");
    withSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root1"), myFixture.findFileInTempDir("root2")), () -> {
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

  // PY-22522
  public void testBothForeignAndSourceRootImportResultsReturned() {
    myFixture.copyDirectoryToProject("bothForeignAndSourceRootImportResultsReturned", "");

    VirtualFile vf = myFixture.findFileInTempDir("ext/m1.py");

    withSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root")), () -> {
      final PsiFile extSource = myFixture.getPsiManager().findFile(vf);
      PyImportResolver foreignResolver = (name, context, withRoots) -> name.toString().equals("m1") ? extSource : null;
      PlatformTestUtil.registerExtension(PyImportResolver.EP_NAME, foreignResolver, getTestRootDisposable());

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

  private void withSourceRoots(@NotNull List<VirtualFile> sourceRoots, @NotNull Runnable f) {
    final Module module = myFixture.getModule();
    for (VirtualFile root : sourceRoots) {
      PsiTestUtil.addSourceRoot(module, root);
    }
    try {
      f.run();
    }
    finally {
      for (VirtualFile root : sourceRoots) {
        PsiTestUtil.removeSourceRoot(module, root);
      }
    }
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

  public void testCustomMemberTargetClass(){
    prepareTestDirectory();

    final PyCustomMember customMember = new PyCustomMember("Clazz").resolvesToClass("pkg.mod1.Clazz");
    final PsiFile context = myFixture.configureByText("a.py", "");

    final TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(myFixture.getProject(), context);
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(typeEvalContext);

    final PsiElement resolved = customMember.resolve(context, resolveContext);
    assertInstanceOf(resolved, PyTypedElement.class);

    final PyType type = typeEvalContext.getType((PyTypedElement) resolved);
    assertInstanceOf(type, PyClassType.class);
  }
}