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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import junit.framework.Assert;

public class PyResolveTest extends PyResolveTestCase {
  @Override
  protected PsiElement doResolve() {
    final PsiReference ref = findReferenceByMarker();
    return ref.resolve();
  }

  private PsiReference findReferenceByMarker() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    return PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
  }

  protected PsiElement resolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
    //  if need be: PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.PYTHON26);
    return ref.resolve();
  }

  private ResolveResult[] multiResolve() {
    PsiReference ref = findReferenceByMarker();
    Assert.assertTrue(ref instanceof PsiPolyVariantReference);
    return ((PsiPolyVariantReference)ref).multiResolve(false);
  }

  public void testClass() {
    assertResolvesTo(PyClass.class, "Test");
  }

  public void testFunc() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyFunction);
  }

  public void testToConstructor() {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PyFunction);
    Assert.assertEquals(PyNames.INIT, ((PyFunction)target).getName());
  }

  public void testToConstructorInherited() {
    ResolveResult[] targets = multiResolve();
    Assert.assertEquals(2, targets.length); // to class, to init
    PsiElement elt;
    // class
    elt = targets[0].getElement();
    Assert.assertTrue(elt instanceof PyClass);
    Assert.assertEquals("Bar", ((PyClass)elt).getName());
    // init
    elt = targets[1].getElement();
    Assert.assertTrue(elt instanceof PyFunction);
    PyFunction fun = (PyFunction)elt;
    Assert.assertEquals(PyNames.INIT, fun.getName());
    PyClass cls = fun.getContainingClass();
    Assert.assertNotNull(cls);
    Assert.assertEquals("Foo", cls.getName());
  }

  // NOTE: maybe this test does not belong exactly here; still it's the best place currently.
  public void testComplexCallee() {
    PsiElement targetElement = resolve();
    PyExpression assigned = ((PyAssignmentStatement)targetElement.getContext()).getAssignedValue();
    Assert.assertTrue(assigned instanceof PyCallExpression);
    PsiElement callee = ((PyCallExpression)assigned).getCallee();
    Assert.assertTrue(callee instanceof PySubscriptionExpression);
  }

  public void testVar() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testDefaultInClass() {
    PsiElement targetElement = resolve();
    Assert.assertNotNull(targetElement);
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertEquals("FOO", ((PyTargetExpression)targetElement).getName());
  }

  public void testQualifiedFunc() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyFunction);
  }

  public void testQualifiedVar() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testQualifiedTarget() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testQualifiedFalseTarget() {
    PsiElement targetElement = resolve();
    Assert.assertNull(targetElement);
  }

  public void testInnerFuncVar() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testTupleInComprh() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testForStatement() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testExceptClause() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testLookAhead() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testLookAheadCapped() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testTryExceptElse() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testGlobal() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testGlobalDefinedLocally() {
    final PsiElement element = resolve();
    UsefulTestCase.assertInstanceOf(element, PyTargetExpression.class);
    final PsiElement parent = element.getParent();
    UsefulTestCase.assertInstanceOf(parent, PyAssignmentStatement.class);
  }

  public void testLambda() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyNamedParameter);
  }

  public void testLambdaParameterOutside() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testSuperField() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testFieldInCondition() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testMultipleFields() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testClassPeerMembers() {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PyFunction);
  }

  public void testTuple() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testMultiTarget() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testMultiTargetTuple() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyAssignmentStatement.class)); // it's deep in a tuple
  }

  public void testWithStatement() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyWithItem);
  }

  public void testTupleInExcept() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(PsiTreeUtil.getParentOfType(targetElement, PyExceptPart.class) != null);
  }


  public void testDocStringClass() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyStringLiteralExpression);
    Assert.assertEquals("Docstring of class Foo", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringInstance() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyStringLiteralExpression);
    Assert.assertEquals("Docstring of class Foo", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringFunction() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyStringLiteralExpression);
    Assert.assertEquals("Docstring of function bar", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringInvalid() {
    PsiElement targetElement = resolve();
    Assert.assertNull(targetElement);
  }

  public void testFieldNotInInit() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
  }

  public void testClassIsNotMemberOfItself() {
    PsiElement targetElement = resolve();
    Assert.assertNull(targetElement);
  }

  public void testSuper() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyFunction);
    Assert.assertEquals("A", ((PyFunction)targetElement).getContainingClass().getName());
  }

  public void testSuperPy3k() {  // PY-1330
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "foo");
      Assert.assertEquals("A", pyFunction.getContainingClass().getName());
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testStackOverflow() {
    PsiElement targetElement = resolve();
    Assert.assertNull(targetElement);
  }

  public void testProperty() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyFunction);
    Assert.assertEquals("set_full_name", ((PyFunction)targetElement).getName());
  }

  public void testLambdaWithParens() {  // PY-882
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyParameter);
  }

  public void testTextBasedResolve() {
    ResolveResult[] resolveResults = multiResolve();
    Assert.assertEquals(1, resolveResults.length);
    Assert.assertTrue(resolveResults[0].getElement() instanceof PyFunction);
  }

  public void testClassPrivateInClass() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateInMethod() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateInMethodNested() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateInherited() {
    PsiElement targetElement = resolve();
    Assert.assertTrue(targetElement instanceof PyTargetExpression);
    Assert.assertTrue(targetElement.getParent() instanceof PyAssignmentStatement);
  }

  public void testClassPrivateOutsideClass() {
    PsiElement targetElement = resolve();
    Assert.assertNull(targetElement);
  }

  public void testClassPrivateOutsideInstance() {
    PsiElement targetElement = resolve();
    Assert.assertNull(targetElement);
  }

  public void testClassNameEqualsMethodName() {
    PsiElement targetElement = resolve();
    UsefulTestCase.assertInstanceOf(targetElement, PyFunction.class);
  }

  public void testUnresolvedImport() {
    final ResolveResult[] results = multiResolve();
    Assert.assertEquals(1, results.length);
    UsefulTestCase.assertInstanceOf(results[0], ImportedResolveResult.class);
    ImportedResolveResult result = (ImportedResolveResult) results [0];
    Assert.assertNull(result.getElement());
  }

  public void testIsInstance() {  // PY-1133
    PsiElement targetElement = resolve();
    UsefulTestCase.assertInstanceOf(targetElement, PyNamedParameter.class);
  }

  public void testListComprehension() { // PY-1143
    PsiElement targetElement = resolve();
    UsefulTestCase.assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testSuperMetaClass() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testSuperDunderClass() {  // PY-1190
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testSuperTwoClasses() {  // PY-2133
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "my_call");
    Assert.assertEquals("Base2", pyFunction.getContainingClass().getName());
  }

  public void testLambdaDefaultParameter() {
    final PsiElement element = doResolve();
    UsefulTestCase.assertInstanceOf(element, PyTargetExpression.class);
    Assert.assertTrue(element.getParent() instanceof PySetCompExpression);
  }

  public void testListAssignment() {
    final PsiElement element = doResolve();
    UsefulTestCase.assertInstanceOf(element, PyTargetExpression.class);
  }

  public void testStarUnpacking() {  // PY-1459
    assertResolvesTo(LanguageLevel.PYTHON30, PyTargetExpression.class, "heads");
  }

  public void testStarUnpackingInLoop() {  // PY-1525
    assertResolvesTo(LanguageLevel.PYTHON30, PyTargetExpression.class, "bbb");
  }

  public void testBuiltinVsClassMember() {  // PY-1654
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "eval");
    Assert.assertEquals("__builtin__.py", pyFunction.getContainingFile().getName());
  }

  public void testLambdaToClass() {  // PY-2182
    assertResolvesTo(PyClass.class, "TestTwo");
  }

  public void testImportInTryExcept() {  // PY-2197
    assertResolvesTo(PyFile.class, "sys.py");
  }

  public void testModuleToBuiltins() {
    final PsiElement element = doResolve();
    Assert.assertNull(element);
  }

  public void testWithParentheses() {
    assertResolvesTo(LanguageLevel.PYTHON27, PyTargetExpression.class, "MockClass1");
  }

  public void testPrivateInsideModule() {  // PY-2618
    assertResolvesTo(PyClass.class, "__VeryPrivate");
  }

  public void testRedeclaredInstanceVar() {  // PY-2740
    assertResolvesTo(PyFunction.class, "jobsDoneCount");
  }

  public void testNoResolveIntoGenerator() {  // PY-3030
    PyTargetExpression expr = assertResolvesTo(PyTargetExpression.class, "foo");
    Assert.assertEquals("foo = 1", expr.getParent().getText());
  }

  public void testResolveInGenerator() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testNestedListComp() {  // PY-3068
    assertResolvesTo(PyTargetExpression.class, "yy");
  }

  public void testSuperclassResolveScope() {  // PY-3554
    assertResolvesTo(PyClass.class, "date", "datetime.py");
  }

  public void testDontResolveTargetToBuiltins() {  // PY-4256
    assertResolvesTo(PyTargetExpression.class, "str");
  }

  public void testKeywordArgument() {
    assertResolvesTo(PyNamedParameter.class, "bar");
  }

  public void testImplicitResolveInstanceAttribute() {
    ResolveResult[] resolveResults = multiResolve();
    Assert.assertEquals(1, resolveResults.length);
    final PsiElement psiElement = resolveResults[0].getElement();
    Assert.assertTrue(psiElement instanceof PyTargetExpression && "xyzzy".equals(((PyTargetExpression)psiElement).getName()));
  }

  public void testAttributeAssignedNearby() {
    assertResolvesTo(PyTargetExpression.class, "xyzzy");
  }

  public void testPreviousTarget() {
    PsiElement resolved = resolve();
    UsefulTestCase.assertInstanceOf(resolved, PyTargetExpression.class);
    PyTargetExpression target = (PyTargetExpression)resolved;
    PyExpression value = target.findAssignedValue();
    UsefulTestCase.assertInstanceOf(value, PyNumericLiteralExpression.class);
  }

  public void testMetaclass() {
    final PyFunction function = assertResolvesTo(PyFunction.class, "getStore");
    Assert.assertEquals("PluginMetaclass", function.getContainingClass().getName());
  }

  // PY-6083
  public void testLambdaParameterInDecorator() {
    assertResolvesTo(PyNamedParameter.class, "xx");
  }

  // PY-6435
  public void testLambdaParameterInDefaultValue() {
    assertResolvesTo(PyNamedParameter.class, "xx");
  }

  // PY-6540
  public void testClassRedefinedField() {
    assertResolvesTo(PyClass.class, "Foo");
  }

  public void testKWArg() {
    assertResolvesTo(PyClass.class, "timedelta");
  }

  public void testShadowingTargetExpression() {
    assertResolvesTo(PyTargetExpression.class, "lab");
  }

  public void testReferenceInDocstring() {
    assertResolvesTo(PyClass.class, "datetime");
  }

  // PY-7541
  public void testLoopToUpperReassignment() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement target = ref.resolve();
    Assert.assertNotNull(target);
    Assert.assertTrue(source != target);
    Assert.assertTrue(PyPsiUtils.isBefore(target, source));
  }

  // PY-7541
  public void testLoopToLowerReassignment() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement target = ref.resolve();
    Assert.assertNotNull(target);
    Assert.assertTrue(source == target);
  }

  // PY-7970
  public void testAugmentedAssignment() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-7970
  public void testAugmentedAfterAugmented() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement resolved = ref.resolve();
    UsefulTestCase.assertInstanceOf(resolved, PyReferenceExpression.class);
    Assert.assertNotSame(resolved, source);
    final PyReferenceExpression res = (PyReferenceExpression)resolved;
    Assert.assertNotNull(res);
    Assert.assertEquals("foo", res.getName());
    UsefulTestCase.assertInstanceOf(res.getParent(), PyAugAssignmentStatement.class);
  }

  public void testGeneratorShadowing() {  // PY-8725
    assertResolvesTo(PyFunction.class, "_");
  }

  // PY-6805
  public void testAttributeDefinedInNew() {
    final PsiElement resolved = resolve();
    UsefulTestCase.assertInstanceOf(resolved, PyTargetExpression.class);
    final PyTargetExpression target = (PyTargetExpression)resolved;
    Assert.assertEquals("foo", target.getName());
    final ScopeOwner owner = ScopeUtil.getScopeOwner(target);
    Assert.assertNotNull(owner);
    UsefulTestCase.assertInstanceOf(owner, PyFunction.class);
    Assert.assertEquals("__new__", owner.getName());
  }

  public void testPreferInitForAttributes() {  // PY-9228
    PyTargetExpression xyzzy = assertResolvesTo(PyTargetExpression.class, "xyzzy");
    assertEquals("__init__", PsiTreeUtil.getParentOfType(xyzzy, PyFunction.class).getName());
  }
}
