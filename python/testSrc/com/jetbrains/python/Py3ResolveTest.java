// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.StackOverflowPreventedException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;


public class Py3ResolveTest extends PyResolveTestCase {

  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PsiReference ref = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    return ref.resolve();
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
    assertResolvesTo(PyElement.class, "List");
  }

  // PY-20864
  public void testLocalVariableAnnotationWithInnerClass() {
    assertResolvesTo(PyClass.class, "MyType");
  }

  // PY-22971
  public void testOverloadsAndNoImplementationInClass() {
    // resolve to the first overload
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

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    // resolve to the implementation
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertFalse(PyiUtil.isOverload(foo, context));
  }

  // PY-22971
  public void testOverloadsAndImplementationsInClass() {
    // resolve to the first implementation
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

  // PY-22971
  public void testTopLevelOverloadsAndNoImplementation() {
    // resolve to the first overload
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertTrue(PyiUtil.isOverload(foo, context));

    PyiUtil
      .getOverloads(foo, context)
      .forEach(
        overload -> {
          if (overload != foo) assertTrue(PyPsiUtils.isBefore(foo, overload));
        }
      );
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    // resolve to the implementation
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertFalse(PyiUtil.isOverload(foo, context));
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementations() {
    // resolve to the first overload because there is no subsequent implementation before the reference
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertTrue(PyiUtil.isOverload(foo, context));

    ((PyFile)foo.getContainingFile())
      .getTopLevelFunctions()
      .forEach(
        function -> assertTrue(function == foo || PyPsiUtils.isBefore(foo, function))
      );
  }

  // PY-22971
  public void testOverloadsAndNoImplementationInImportedClass() {
    // resolve to the first overload
    myFixture
      .copyDirectoryToProject("resolve/OverloadsAndNoImplementationInImportedClassDep", "OverloadsAndNoImplementationInImportedClassDep");
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

  // PY-22971
  public void testOverloadsAndImplementationInImportedClass() {
    // resolve to the implementation
    myFixture
      .copyDirectoryToProject("resolve/OverloadsAndImplementationInImportedClassDep", "OverloadsAndImplementationInImportedClassDep");
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertFalse(PyiUtil.isOverload(foo, context));
  }

  // PY-22971
  public void testOverloadsAndImplementationsInImportedClass() {
    // resolve to the first implementation
    myFixture
      .copyDirectoryToProject("resolve/OverloadsAndImplementationsInImportedClassDep", "OverloadsAndImplementationsInImportedClassDep");
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

  // PY-22971
  public void testOverloadsAndNoImplementationInImportedModule() {
    // resolve to the first overload
    myFixture
      .copyDirectoryToProject("resolve/OverloadsAndNoImplementationInImportedModuleDep", "OverloadsAndNoImplementationInImportedModuleDep");
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

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    // resolve to the implementation
    myFixture
      .copyDirectoryToProject("resolve/OverloadsAndImplementationInImportedModuleDep", "OverloadsAndImplementationInImportedModuleDep");
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertFalse(PyiUtil.isOverload(foo, context));
  }

  // PY-22971
  public void testOverloadsAndImplementationsInImportedModule() {
    // resolve to the last implementation
    myFixture
      .copyDirectoryToProject("resolve/OverloadsAndImplementationsInImportedModuleDep", "OverloadsAndImplementationsInImportedModuleDep");
    final PyFunction foo = assertResolvesTo(PyFunction.class, "foo");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertFalse(PyiUtil.isOverload(foo, context));

    ((PyFile)foo.getContainingFile())
      .getTopLevelFunctions()
      .forEach(
        function -> assertTrue(PyiUtil.isOverload(function, context) || function == foo || PyPsiUtils.isBefore(function, foo))
      );
  }

  // PY-30512
  public void testDunderBuiltins() {
    final PsiElement element = doResolve();
    assertEquals(PyBuiltinCache.getInstance(myFixture.getFile()).getBuiltinsFile(), element);
  }

  // PY-20783
  public void testFStringFunctionParameter() {
    assertResolvesTo(PyParameter.class, "param");
  }

  // PY-20783
  public void testFStringLocalVariable() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-20783
  public void testFStringLocalVariableUnresolved() {
    assertNull(doResolve());
  }

  // PY-20783
  public void testFStringNestedScopes() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionTarget() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionSourcePart() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringNestedInResultComprehensionSourcePart() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionConditionPart() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringNestedComprehensionSourcePart() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-22094
  public void testFStringInsideAssertStatement() {
    assertResolvesTo(PyParameter.class, "name");
  }

  // PY-21493
  public void testRegexpAndFStringCombined() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-29898
  public void testKeywordArgumentToDataclassAttribute() {
    assertResolvesTo(PyTargetExpression.class, "some_attr");
  }

  // PY-29898
  public void testKeywordArgumentToAttrsAttribute() {
    assertResolvesTo(PyTargetExpression.class, "some_attr");
  }

  // PY-55231
  public void testKeywordArgumentToConstructorParameter() {
    assertResolvesTo(PyNamedParameterImpl.class, "param");
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
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

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
      () -> assertNull(PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve())
    );
  }

  // TODO: this should be fixed after introducing an ability to check visited paths while resolving some qualified name
  // PY-30942
  public void _testNoInlinePackageInsteadStubPackageAnotherImport() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib",
      () -> assertNull(PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve())
    );
  }

  // PY-31354
  public void testStubPackageInOtherRoot() {
    final String path = "resolve/" + getTestName(false);
    myFixture.configureByFile(path + "/main.py");

    runWithAdditionalClassEntryInSdkRoots(
      path + "/lib1",
      () ->
        runWithAdditionalClassEntryInSdkRoots(
          path + "/lib2",
          () -> {
            final PsiElement element = PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
            assertInstanceOf(element, PyFunction.class);
            assertEquals("foo.pyi", element.getContainingFile().getName());
          }
        )
    );
  }

  // PY-25832
  public void testTypeVarBoundAttribute() {
    assertResolvesTo(PyFunction.class, "upper", "builtins.pyi");
  }

  // PY-25832
  public void testTypeVarConstraintAttribute() {
    assertResolvesTo(PyFunction.class, "bit_length", "builtins.pyi");
  }

  // PY-25832
  public void testTypeVarClassObjectBoundAttribute() {
    assertNull(doResolve());
  }

  // PY-36158
  public void testDataclassFieldsDataclassesStarImport() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testInstanceAttrAbove() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testNoResolveInstanceAttrBelow() {
    assertUnresolved();
  }

  public void testNoResolveInstanceAttrSameLine() {
    assertUnresolved();
  }

  public void testInstanceAttrOtherMethod() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testInstanceAttrOtherMethodAndAbove() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("f", function.getName());
  }

  public void testInstanceAttrBelowAndOtherMethodAbove() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("g", function.getName());
  }

  public void testInstanceAttrBelowAndOtherMethodBelow() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("g", function.getName());
  }

  public void testInstanceAttrInheritedAndAbove() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("f", function.getName());
  }

  public void testInstanceAttrInheritedAndBelow() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("g", function.getName());
  }

  public void testInstanceAttrBelowEarlierByControlFlow() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testInstanceAttrBelowDifferentBranchesOfSameIfStatement() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testInstanceAttrBothEarlierAndLater() {
    PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    assertEquals("self.foo = 1", target.getParent().getText());
  }

  public void testInstanceAttrBelowInInitAndOtherMethodAbove() {
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "foo");
    final PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("g", function.getName());
  }

  // PY-48012
  public void testKeywordPatternResolvesToInstanceAttribute() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-48012
  public void testKeywordPatternResolvesToClassAttribute() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-48012
  public void testKeywordPatternResolvesToProperty() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-48012
  public void testKeywordPatternResolvesToInheritedInstanceAttribute() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-48012
  public void testKeywordPatternResolvesToInheritedClassAttribute() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-48012
  public void testKeywordPatternResolvesToInheritedProperty() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-82115
  public void testNonlocalInPresenceOfGlobalInNestedFunction() {
    PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "s");
    PyFunction function = assertInstanceOf(ScopeUtil.getScopeOwner(target), PyFunction.class);
    assertEquals("outer1", function.getName());
  }

  // PY-82115
  public void testNonlocalInPresenceOfGlobalInOuterScope() {
    assertResolvesToItself();
  }

  // PY-82115
  public void testNonlocalNotResolvedToGlobalName() {
    assertResolvesToItself();
  }

  // PY-82699
  public void testTypeParameterRebindToLocalVariableInEnclosingScope() {
    assertResolvesTo(PyTargetExpression.class, "T");
  }

  // PY-82699
  public void testTypeParameterRebindToLocalVariableInSameScope() {
    assertUnresolved();
  }

  // PY-82850
  public void testNonIdempotentComputation() {
    PyTestCase.fixme("PY-83181", StackOverflowPreventedException.class, () -> {
      RecursionManager.assertOnRecursionPrevention(myFixture.getTestRootDisposable());

      myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
      PsiElement result1 = findReferenceByMarker(myFixture.getFile(), "<ref1>").resolve();
      assertNotNull(result1);

      PsiElement result2 = findReferenceByMarker(myFixture.getFile(), "<ref2>").resolve();
      assertNotNull(result2);
    });
  }

  private void assertResolvesToItself() {
    PsiElement resolved = doResolve();
    PsiReference reference = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    assertEquals(reference.getElement(), resolved);
  }
}
