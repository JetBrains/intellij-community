/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.List;

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
    setLanguageLevel(LanguageLevel.PYTHON33);
    try {
      assertResolvesTo(PsiDirectory.class, "p1");
    }
    finally {
      setLanguageLevel(null);
    }
  }

  // PY-7156
  public void testFromPython33NamespacePackageImport() {
    setLanguageLevel(LanguageLevel.PYTHON33);
    try {
      assertResolvesTo(PyFunction.class, "foo");
    }
    finally {
      setLanguageLevel(null);
    }
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
    PyTestCase.assertNotParsed((PyFile)psiManager.findFile(myFixture.findFileInTempDir("p1/__init__.py")));
    PyTestCase.assertNotParsed((PyFile)psiManager.findFile(myFixture.findFileInTempDir("p1/foo.py")));
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, new Runnable() {
      @Override
      public void run() {
        assertResolvesTo(PyFile.class, "m1.py");
      }
    });
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
}