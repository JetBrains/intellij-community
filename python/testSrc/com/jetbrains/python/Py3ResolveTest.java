/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;

/**
 * @author yole
 */
public class Py3ResolveTest extends PyResolveTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PyTestCase.ourPy3Descriptor;
  }

  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    return ref.resolve();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON34);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testObjectMethods() {  // PY-1494
    assertResolvesTo(PyFunction.class, "__repr__");
  }

  // PY-5499
  public void testTrueDiv() {
    assertResolvesTo(PyFunction.class, "__truediv__");
  }

  // PY-13734
  public void testImplicitDunderClass() {
    assertResolvesTo(PyClass.class, "A");
  }

  public void testImplicitDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOf() {
    assertUnresolved();
  }

  public void testImplicitUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithClassAttr() {
    assertResolvesTo(PyClass.class, "A");
  }

  public void testImplicitDunderDocWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfWithClassAttr() {
    assertUnresolved();
  }

  public void testImplicitClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithInheritedClassAttr() {
    assertResolvesTo(PyClass.class, "B");
  }

  public void testImplicitDunderDocWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testImplicitInheritedClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClass() {
    assertResolvesTo(PyClass.class, "A");
  }

  public void testInstanceDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testInstanceDunderSizeOf() {
    final PyFunction expression = assertResolvesTo(PyFunction.class, PyNames.SIZEOF);
    assertIsBuiltin(expression);
  }

  public void testInstanceUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClassWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderDocWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderSizeOfWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testInstanceDunderClassWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderDocWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testInstanceDunderSizeOfWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testLocalDunderClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    assertNotNull(function);
    assertEquals("foo", function.getName());
  }

  // PY-13734
  public void testTypeDunderClass() {
    assertResolvesTo(PyClass.class, "type");
  }

  public void testTypeDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOf() {
    final PyFunction expression = assertResolvesTo(PyFunction.class, PyNames.SIZEOF);
    assertIsBuiltin(expression);
  }

  public void testTypeUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testTypeDunderClassWithClassAttr() {
    assertResolvesTo(PyClass.class, "type");
  }

  public void testTypeDunderDocWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeDunderSizeOfWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testTypeDunderClassWithInheritedClassAttr() {
    assertResolvesTo(PyClass.class, "type");
  }

  public void testTypeDunderDocWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOfWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testDunderClassInDeclaration() {
    assertUnresolved();
  }

  public void testDunderDocInDeclaration() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testDunderSizeOfInDeclaration() {
    assertUnresolved();
  }

  public void testUndeclaredClassAttrInDeclaration() {
    assertUnresolved();
  }

  // PY-13734
  public void testDunderClassInDeclarationWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderDocInDeclarationWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderSizeOfInDeclarationWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testClassAttrInDeclaration() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, "my_attr");

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  // PY-13734
  public void testDunderClassInDeclarationWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testDunderDocInDeclarationWithInheritedClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testDunderSizeOfInDeclarationWithInheritedClassAttr() {
    assertUnresolved();
  }

  public void testInheritedClassAttrInDeclaration() {
    assertUnresolved();
  }

  // PY-13734
  public void testDunderClassInDeclarationInsideFunction() {
    assertUnresolved();
  }

  // PY-20864
  public void testTopLevelVariableAnnotationFromTyping() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertResolvesTo(PyElement.class, "List"));
  }

  // PY-20864
  public void testLocalVariableAnnotationWithInnerClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertResolvesTo(PyClass.class, "MyType"));
  }

  // PY-22971
  public void testOverloadsAndNoImplementationInClass() {
    // resolve to the first overload
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());

        PyiUtil
          .getOverloads(foo, context)
          .forEach(
            overload -> {
              if (overload != foo) assertTrue(PyPsiUtils.isBefore(foo, overload));
            }
          );
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    // resolve to the implementation
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationsInClass() {
    // resolve to the first implementation
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));

        final PyClass pyClass = foo.getContainingClass();
        assertNotNull(pyClass);

        pyClass.visitMethods(
          function -> {
            assertTrue(PyiUtil.isOverload(function, context) || function == foo || PyPsiUtils.isBefore(foo, function));
            return true;
          },
          false,
          context
        );
      }
    );
  }

  // PY-22971
  public void testTopLevelOverloadsAndNoImplementation() {
    // resolve to the last overload
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());

        PyiUtil
          .getOverloads(foo, context)
          .forEach(
            overload -> {
              if (overload != foo) assertTrue(PyPsiUtils.isBefore(overload, foo));
            }
          );
      }
    );
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    // resolve to the implementation
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));
      }
    );
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementations() {
    // resolve to the last overload
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertTrue(PyiUtil.isOverload(foo, context));

        ((PyFile)foo.getContainingFile())
          .getTopLevelFunctions()
          .forEach(
            function -> assertTrue(function == foo || PyPsiUtils.isBefore(function, foo))
          );
      }
    );
  }

  // PY-22971
  public void testOverloadsAndNoImplementationInImportedClass() {
    // resolve to the first overload
    myFixture.copyDirectoryToProject("resolve/OverloadsAndNoImplementationInImportedClassDep", "OverloadsAndNoImplementationInImportedClassDep");
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());

        PyiUtil
          .getOverloads(foo, context)
          .forEach(
            overload -> {
              if (overload != foo) assertTrue(PyPsiUtils.isBefore(foo, overload));
            }
          );
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedClass() {
    // resolve to the implementation
    myFixture.copyDirectoryToProject("resolve/OverloadsAndImplementationInImportedClassDep", "OverloadsAndImplementationInImportedClassDep");
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationsInImportedClass() {
    // resolve to the first implementation
    myFixture.copyDirectoryToProject("resolve/OverloadsAndImplementationsInImportedClassDep", "OverloadsAndImplementationsInImportedClassDep");
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));

        final PyClass pyClass = foo.getContainingClass();
        assertNotNull(pyClass);

        pyClass.visitMethods(
          function -> {
            assertTrue(PyiUtil.isOverload(function, context) || function == foo || PyPsiUtils.isBefore(foo, function));
            return true;
          },
          false,
          context
        );
      }
    );
  }

  // PY-22971
  public void testOverloadsAndNoImplementationInImportedModule() {
    // resolve to the last overload
    myFixture.copyDirectoryToProject("resolve/OverloadsAndNoImplementationInImportedModuleDep", "OverloadsAndNoImplementationInImportedModuleDep");
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());

        PyiUtil
          .getOverloads(foo, context)
          .forEach(
            overload -> {
              if (overload != foo) assertTrue(PyPsiUtils.isBefore(overload, foo));
            }
          );
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    // resolve to the implementation
    myFixture.copyDirectoryToProject("resolve/OverloadsAndImplementationInImportedModuleDep", "OverloadsAndImplementationInImportedModuleDep");
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationsInImportedModule() {
    // resolve to the last implementation
    myFixture.copyDirectoryToProject("resolve/OverloadsAndImplementationsInImportedModuleDep", "OverloadsAndImplementationsInImportedModuleDep");
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
        assertFalse(PyiUtil.isOverload(foo, context));

        ((PyFile)foo.getContainingFile())
          .getTopLevelFunctions()
          .forEach(
            function -> assertTrue(PyiUtil.isOverload(function, context) || function == foo || PyPsiUtils.isBefore(function, foo))
          );
      }
    );
  }

  // PY-30512
  public void testDunderBuiltins() {
    final PsiElement element = doResolve();
    assertEquals(PyBuiltinCache.getInstance(myFixture.getFile()).getBuiltinsFile(), element);
  }

  // PY-20783
  public void testFStringFunctionParameter() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyParameter.class, "param");
  }

  // PY-20783
  public void testFStringLocalVariable() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-20783
  public void testFStringLocalVariableUnresolved() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertNull(doResolve()));
  }

  // PY-20783
  public void testFStringNestedScopes() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionTarget() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionSourcePart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringNestedInResultComprehensionSourcePart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionConditionPart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringNestedComprehensionSourcePart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-22094
  public void testFStringInsideAssertStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertResolvesTo(PyParameter.class, "name"));
  }

  // PY-21493
  public void testRegexpAndFStringCombined() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertResolvesTo(PyTargetExpression.class, "foo"));
  }

  // PY-30942
  public void testUserPyiInsteadUserPy() {
    myFixture.copyDirectoryToProject("resolve/" + getTestName(false), "");
    myFixture.configureByFile("main.py");

    final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
    assertInstanceOf(element, PyFunction.class);
    assertEquals("foo.pyi", element.getContainingFile().getName());
  }

  // PY-30942
  public void testUserPyInsteadProvidedPyi() {
    final String path = "resolve/" + getTestName(false);
    myFixture.copyDirectoryToProject(path + "/pkg", "pkg");
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
        assertInstanceOf(element, PyFunction.class);

        final PsiFile file = element.getContainingFile();
        assertEquals("foo.py", file.getName());
        assertEquals("src", file.getParent().getParent().getName());
      }
    );
  }

  // PY-32963
  public void testProvidedPyiInsteadStubPackage() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();

        final PsiFile file = element.getContainingFile();
        assertEquals("foo.pyi", file.getName());
        assertEquals("pkg", file.getParent().getName());
      }
    );
  }

  // PY-30942
  public void testStubPackageInsteadInlinePackage() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();

        final PsiFile file = element.getContainingFile();
        assertEquals("foo.pyi", file.getName());
        assertEquals("pkg-stubs", file.getParent().getName());
      }
    );
  }

  // PY-30942
  public void testStubPackageInsteadInlinePackageFullyQName() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
        assertInstanceOf(element, PyFunction.class);

        final PsiFile file = element.getContainingFile();
        assertEquals("foo.pyi", file.getName());
        assertEquals("pkg-stubs", file.getParent().getName());
      }
    );
  }

  // PY-30942
  public void testInlinePackageInsteadTypeShed() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
        assertInstanceOf(element, PyFunction.class);
        assertEquals("process.py", element.getContainingFile().getName());
      }
    );
  }

  // PY-30942
  public void testTypeShedInsteadPy() {
    assertResolvesTo(PyTargetExpression.class, "MINYEAR", "datetime.pyi");
  }

  // PY-30942
  public void testInlinePackageInsteadPartialStubPackage() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
        assertInstanceOf(element, PyFunction.class);
        assertEquals("foo.py", element.getContainingFile().getName());
      }
    );
  }

  // PY-32286
  public void testPartialStubPackageInsteadInlinePackage() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        final PsiReference reference = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
        assertInstanceOf(reference, PsiPolyVariantReference.class);

        final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
        assertSize(1, results);

        final PsiElement element = results[0].getElement();
        assertInstanceOf(element, PyFunction.class);
        assertEquals("foo.pyi", element.getContainingFile().getName());
      }
    );
  }

  // TODO: this should be fixed after introducing an ability to check visited paths while resolving some qualified name
  // PY-30942
  public void _testNoInlinePackageInsteadStubPackage() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> assertNull(PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve())
    );
  }

  // TODO: this should be fixed after introducing an ability to check visited paths while resolving some qualified name
  // PY-30942
  public void _testNoInlinePackageInsteadStubPackageAnotherImport() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile libDir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> assertNull(PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve())
    );
  }

  // PY-31354
  public void testStubPackageInOtherRoot() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    final VirtualFile lib1Dir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib1");
    assertNotNull(lib1Dir);

    final VirtualFile lib2Dir = StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + path + "/lib2");
    assertNotNull(lib2Dir);

    runWithAdditionalClassEntryInSdkRoots(
      lib1Dir,
      () ->
        runWithAdditionalClassEntryInSdkRoots(
          lib2Dir,
          () -> {
            final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
            assertInstanceOf(element, PyFunction.class);
            assertEquals("foo.pyi", element.getContainingFile().getName());
          }
        )
    );
  }
}
