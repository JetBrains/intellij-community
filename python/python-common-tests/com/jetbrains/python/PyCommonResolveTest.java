// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixture.PyCommonResolveTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public abstract class PyCommonResolveTest extends PyCommonResolveTestCase {

  @Override
  protected PsiElement doResolve() {
    final PsiReference ref = findReferenceByMarker();
    return ref.resolve();
  }

  private PsiReference findReferenceByMarker() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    return PyCommonResolveTestCase.findReferenceByMarker(myFixture.getFile());
  }

  protected PsiElement resolve() {
    PsiReference ref = configureByFile("resolve/" + getTestName(false) + ".py");
    //  if need be: PythonLanguageLevelPusher.setForcedLanguageLevel(project, LanguageLevel.PYTHON26);
    return ref.resolve();
  }

  private ResolveResult[] multiResolve() {
    PsiReference ref = findReferenceByMarker();
    assertInstanceOf(ref, PsiPolyVariantReference.class);
    return ((PsiPolyVariantReference)ref).multiResolve(false);
  }

  private <T extends PsiNamedElement> T assertResolvesTo(@Language("TEXT") @NotNull String text,
                                                         @NotNull Class<T> cls,
                                                         @NotNull String name) {
    final Ref<T> result = new Ref<>();

    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        myFixture.configureByText(PythonFileType.INSTANCE, text);
        result.set(assertResolveResult(PyCommonResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve(), cls, name));
      }
    );

    return result.get();
  }

  public void testClass() {
    assertResolvesTo(PyClass.class, "Test");
  }

  public void testFunc() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyFunction.class);
  }

  public void testInitialization() {
    assertResolvesTo(
      """
        class Foo:
          pass
        Foo()
         <ref>""",
      PyClass.class,
      "Foo"
    );
  }

  public void testInitializationWithDunderInit() {
    assertResolvesTo(
      """
        class Foo:
          def __init__(self):
            pass
        Foo()
         <ref>""",
      PyClass.class,
      "Foo"
    );
  }

  // PY-17877
  public void testInitializationWithMetaclassDunderCall() {
    assertResolvesTo(
      """
        class MyMeta(type):
            def __call__(cls, p1, p2):
                pass

        class MyClass(metaclass=MyMeta):
            pass

        MyClass()
          <ref>""",
      PyClass.class,
      "MyClass"
    );
  }

  public void testInitializationWithDunderInitAndMetaclassDunderCall() {
    assertResolvesTo(
      """
        class MyMeta(type):
            def __call__(cls, p1, p2):
                pass

        class MyClass(metaclass=MyMeta):
            def __init__(self): pass

        MyClass()
          <ref>""",
      PyClass.class,
      "MyClass"
    );
  }

  // PY-17877, PY-41380
  public void testInitializationWithMetaclassSelfArgsKwargsDunderCall() {
    assertResolvesTo(
      """
        class MyMeta(type):
            def __call__(cls, *args, **kwargs):
                pass

        class MyClass(metaclass=MyMeta):
            pass

        MyClass()
          <ref>""",
      PyClass.class,
      "MyClass"
    );
  }

  public void testInitOrNewReturnsInitWhenNewIsFirst() {
    doTestInitOrNewReturnsInit();
  }

  public void testInitOrNewReturnsInitWhenNewIsLast() {
    doTestInitOrNewReturnsInit();
  }

  private void doTestInitOrNewReturnsInit() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PyClass pyClass = PsiTreeUtil.findChildOfType(myFixture.getFile(), PyClass.class);
    assertNotNull(pyClass);
    final PyFunction init = pyClass.findInitOrNew(false, TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()));
    assertEquals(PyNames.INIT, init.getName());
  }

  public void testToConstructorInherited() {
    ResolveResult[] targets = multiResolve();
    assertEquals(1, targets.length); // to class, to init
    PsiElement elt;
    // class
    elt = targets[0].getElement();
    assertInstanceOf(elt, PyClass.class);
    assertEquals("Bar", ((PyClass)elt).getName());
  }

  // NOTE: maybe this test does not belong exactly here; still it's the best place currently.
  public void testComplexCallee() {
    PsiElement targetElement = resolve();
    PyExpression assigned = ((PyAssignmentStatement)targetElement.getContext()).getAssignedValue();
    assertInstanceOf(assigned, PyCallExpression.class);
    PsiElement callee = ((PyCallExpression)assigned).getCallee();
    assertInstanceOf(callee, PySubscriptionExpression.class);
  }

  public void testVar() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testDefaultInClass() {
    PsiElement targetElement = resolve();
    assertNotNull(targetElement);
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertEquals("FOO", ((PyTargetExpression)targetElement).getName());
  }

  public void testQualifiedFunc() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyFunction.class);
  }

  public void testQualifiedVar() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testQualifiedTarget() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testQualifiedFalseTarget() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testInnerFuncVar() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testTupleInComprh() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testForStatement() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testExceptClause() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testLookAhead() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testLookAheadCapped() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testTryExceptElse() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testGlobal() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testGlobalInNestedFunction() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(ScopeUtil.getScopeOwner(targetElement), PyFile.class);
  }

  public void testGlobalDefinedLocally() {
    final PsiElement element = resolve();
    assertInstanceOf(element, PyTargetExpression.class);
    final PsiElement parent = element.getParent();
    assertInstanceOf(parent, PyAssignmentStatement.class);
  }

  public void testLambda() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyNamedParameter.class);
  }

  public void testLambdaParameterOutside() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testSuperField() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testFieldInCondition() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testMultipleFields() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testClassPeerMembers() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyFunction.class);
  }

  public void testTuple() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testMultiTarget() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testMultiTargetTuple() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyAssignmentStatement.class)); // it's deep in a tuple
  }

  public void testWithStatement() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyWithItem.class);
  }

  public void testTupleInExcept() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertNotNull(PsiTreeUtil.getParentOfType(targetElement, PyExceptPart.class));
  }


  public void testDocStringClass() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyStringLiteralExpression.class);
    assertEquals("Docstring of class Foo", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringInstance() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyStringLiteralExpression.class);
    assertEquals("Docstring of class Foo", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringFunction() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyStringLiteralExpression.class);
    assertEquals("Docstring of function bar", ((PyStringLiteralExpression)targetElement).getStringValue());
  }

  public void testDocStringInvalid() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testFieldNotInInit() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testClassIsNotMemberOfItself() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testSuper() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyFunction.class);
    assertEquals("A", ((PyFunction)targetElement).getContainingClass().getName());
  }

  public void testSuperPy3k() {  // PY-1330
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "foo");
        assertEquals("A", pyFunction.getContainingClass().getName());
      }
    );
  }

  public void testStackOverflow() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testProperty() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyFunction.class);
    assertEquals("set_full_name", ((PyFunction)targetElement).getName());
  }

  public void testLambdaWithParens() {  // PY-882
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyParameter.class);
  }

  public void testTextBasedResolve() {
    ResolveResult[] resolveResults = multiResolve();
    assertEquals(1, resolveResults.length);
    assertInstanceOf(resolveResults[0].getElement(), PyFunction.class);
  }

  public void testClassPrivateInClass() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testClassPrivateInMethod() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testClassPrivateInMethodNested() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testClassPrivateInherited() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
    assertInstanceOf(targetElement.getParent(), PyAssignmentStatement.class);
  }

  public void testClassPrivateOutsideClass() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testClassPrivateOutsideInstance() {
    PsiElement targetElement = resolve();
    assertNull(targetElement);
  }

  public void testClassNameEqualsMethodName() {
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyFunction.class);
  }

  public void testUnresolvedImport() {
    final ResolveResult[] results = multiResolve();
    assertEquals(1, results.length);
    assertInstanceOf(results[0], ImportedResolveResult.class);
    ImportedResolveResult result = (ImportedResolveResult)results[0];
    assertNull(result.getElement());
  }

  public void testIsInstance() {  // PY-1133
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyNamedParameter.class);
  }

  public void testListComprehension() { // PY-1143
    PsiElement targetElement = resolve();
    assertInstanceOf(targetElement, PyTargetExpression.class);
  }

  public void testSuperMetaClass() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  //PY-28562
  public void testSuperMetaClassInheritsObject() {
    assertResolvesTo(PyFunction.class, "__getattribute__");
  }


  public void testSuperDunderClass() {  // PY-1190
    assertResolvesTo(PyFunction.class, "foo");
  }

  public void testSuperTwoClasses() {  // PY-2133
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "my_call");
    assertEquals("Base2", pyFunction.getContainingClass().getName());
  }

  public void testLambdaDefaultParameter() {
    final PsiElement element = doResolve();
    assertInstanceOf(element, PyTargetExpression.class);
    assertInstanceOf(element.getParent(), PySetCompExpression.class);
  }

  public void testListAssignment() {
    final PsiElement element = doResolve();
    assertInstanceOf(element, PyTargetExpression.class);
  }

  public void testStarUnpacking() {  // PY-1459
    assertResolvesTo(LanguageLevel.PYTHON34, PyTargetExpression.class, "heads");
  }

  public void testStarUnpackingInLoop() {  // PY-1525
    assertResolvesTo(LanguageLevel.PYTHON34, PyTargetExpression.class, "bbb");
  }

  public void testBuiltinVsClassMember() {  // PY-1654
    final PyFunction pyFunction = assertResolvesTo(PyFunction.class, "eval");
    assertIsBuiltin(pyFunction);
  }

  public void testLambdaToClass() {  // PY-2182
    assertResolvesTo(PyClass.class, "TestTwo");
  }

  public void testImportInTryExcept() {  // PY-2197
    assertResolvesTo(PyFile.class, "sys.pyi");
  }

  public void testModuleToBuiltins() {
    final PsiElement element = doResolve();
    assertNull(element);
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
    assertEquals("foo = 1", expr.getParent().getText());
  }

  public void testResolveInGenerator() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  public void testNestedListComp() {  // PY-3068
    assertResolvesTo(PyTargetExpression.class, "yy");
  }

  public void testSuperclassResolveScope() {  // PY-3554
    assertResolvesTo(PyClass.class, "date", "datetime.pyi");
  }

  public void testDontResolveTargetToBuiltins() {  // PY-4256
    assertResolvesTo(PyTargetExpression.class, "str");
  }

  public void testKeywordArgument() {
    assertResolvesTo(PyNamedParameter.class, "bar");
  }

  public void testImplicitResolveInstanceAttribute() {
    ResolveResult[] resolveResults = multiResolve();
    assertEquals(1, resolveResults.length);
    final PsiElement psiElement = resolveResults[0].getElement();
    assertTrue(psiElement instanceof PyTargetExpression && "xyzzy".equals(((PyTargetExpression)psiElement).getName()));
  }

  public void testAttributeAssignedNearby() {
    assertResolvesTo(PyTargetExpression.class, "xyzzy");
  }

  public void testPreviousTarget() {
    PsiElement resolved = resolve();
    assertInstanceOf(resolved, PyTargetExpression.class);
    PyTargetExpression target = (PyTargetExpression)resolved;
    PyExpression value = target.findAssignedValue();
    assertInstanceOf(value, PyNumericLiteralExpression.class);
  }

  public void testMetaclass() {
    final PyFunction function = assertResolvesTo(PyFunction.class, "getStore");
    assertEquals("PluginMetaclass", function.getContainingClass().getName());
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

  // PY-9795
  public void testGoogleDocstringParamType() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyClass.class, "datetime"));
  }

  // PY-9795
  public void testGoogleDocstringReturnType() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyClass.class, "MyClass"));
  }

  // PY-16906
  public void testGoogleDocstringModuleAttribute() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyTargetExpression.class, "module_level_variable1"));
  }

  // PY-7541
  public void testLoopToUpperReassignment() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement target = ref.resolve();
    assertNotNull(target);
    assertNotSame(source, target);
    assertTrue(PyPsiUtils.isBefore(target, source));
  }

  // PY-7541
  public void testLoopToLowerReassignment() {
    final PsiReference ref = findReferenceByMarker();
    final PsiElement source = ref.getElement();
    final PsiElement target = ref.resolve();
    assertNotNull(target);
    assertSame(source, target);
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
    assertInstanceOf(resolved, PyReferenceExpression.class);
    assertNotSame(resolved, source);
    final PyReferenceExpression res = (PyReferenceExpression)resolved;
    assertNotNull(res);
    assertEquals("foo", res.getName());
    assertInstanceOf(res.getParent(), PyAugAssignmentStatement.class);
  }

  public void testGeneratorShadowing() {  // PY-8725
    assertResolvesTo(PyFunction.class, "_");
  }

  // PY-6805
  public void testAttributeDefinedInNew() {
    final PsiElement resolved = resolve();
    assertInstanceOf(resolved, PyTargetExpression.class);
    final PyTargetExpression target = (PyTargetExpression)resolved;
    assertEquals("foo", target.getName());
    final ScopeOwner owner = ScopeUtil.getScopeOwner(target);
    assertNotNull(owner);
    assertInstanceOf(owner, PyFunction.class);
    assertEquals("__new__", owner.getName());
  }

  public void testPreferInitForAttributes() {  // PY-9228
    PyTargetExpression xyzzy = assertResolvesTo(PyTargetExpression.class, "xyzzy");
    assertEquals("__init__", PsiTreeUtil.getParentOfType(xyzzy, PyFunction.class).getName());
  }

  // PY-11401
  public void testResolveAttributesUsingOldStyleMROWhenUnresolvedAncestorsAndC3Fails() {
    assertResolvesTo(PyFunction.class, "foo");
  }

  // PY-15390
  public void testMatMul() {
    assertResolvesTo(PyFunction.class, "__matmul__");
  }

  // PY-15390
  public void testRMatMul() {
    assertResolvesTo(PyFunction.class, "__rmatmul__");
  }

  //PY-2748
  public void testFormatStringKWArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyNumericLiteralExpression.class);
    assertEquals(12, (long)((PyNumericLiteralExpression)target).getLongValue());
  }

  //PY-2748
  public void testFormatPositionalArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyReferenceExpression.class);
    assertEquals("string", target.getText());
  }

  //PY-2748
  public void testFormatArgsAndKWargs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
  }

  //PY-2748
  public void testFormatArgsAndKWargs1() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("keyword", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testFormatStringWithPackedDictAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("\"f\"", target.getText());
  }

  //PY-2748
  public void testFormatStringWithPackedListAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyNumericLiteralExpression.class);
    assertEquals("1", target.getText());
  }

  //PY-2748
  public void testFormatStringWithPackedTupleAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("\"snd\"", target.getText());
  }

  //PY-2748
  public void testFormatStringWithRefAsArgument() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStarArgument.class);
  }


  //PY-2748
  public void testPercentPositionalArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
  }

  //PY-2748
  public void testPercentKeyWordArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyNumericLiteralExpression.class);
    assertNotNull(((PyNumericLiteralExpression)target).getLongValue());
    assertEquals(Long.valueOf(4181), ((PyNumericLiteralExpression)target).getLongValue());
  }

  public void testPercentStringKeyWordArgWithParentheses() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("s", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testPercentStringBinaryStatementArg() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("1", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testPercentStringArgWithRedundantParentheses() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyStringLiteralExpression.class);
    assertEquals("1", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testPercentStringWithRefAsArgument() {
    PsiElement target = resolve();
    assertEquals("tuple", target.getText());
  }

  //PY-2748
  public void testPercentStringWithOneStringArgument() {
    PsiElement target = resolve();
    assertEquals("hello", ((PyStringLiteralExpression)target).getStringValue());
  }

  //PY-2748
  public void testFormatStringPackedDictCall() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyKeywordArgument.class);
  }

  //PY-2748
  public void testPercentStringDictCall() {
    PsiElement target = resolve();
    assertEquals("hello", ((PyStringLiteralExpression)((PyKeywordArgument)target).getValueExpression()).getStringValue());
  }

  // PY-2748
  public void testPercentStringParenDictCall() {
    PsiElement target = resolve();
    assertEquals("hello", ((PyStringLiteralExpression)((PyKeywordArgument)target).getValueExpression()).getStringValue());
  }

  // PY-2748
  public void testPercentStringPosParenDictCall() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyCallExpression.class);
    assertEquals("dict()", target.getText());
  }

  // PY-2748
  public void testFormatStringWithPackedAndNonPackedArgs() {
    PsiElement target = resolve();
    assertInstanceOf(target, PyNumericLiteralExpression.class);
    assertEquals("2", target.getText());
  }

  public void testGlobalNotDefinedAtTopLevel() {
    assertResolvesTo(PyTargetExpression.class, "foo");
  }

  // PY-13734
  public void testImplicitDunderClass() {
    assertUnresolved();
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

  public void testImplicitQualifiedClassAttr() {
    ResolveResult[] resolveResults = multiResolve();
    assertEquals(1, resolveResults.length);
    PyTargetExpression target = assertInstanceOf(resolveResults[0].getElement(), PyTargetExpression.class);
    assertEquals("CLASS_ATTR", target.getName());
    assertInstanceOf(ScopeUtil.getScopeOwner(target), PyClass.class);
  }

  // PY-13734
  public void testImplicitDunderClassNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitDunderDocNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitUndeclaredClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithClassAttr() {
    assertUnresolved();
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
  public void testImplicitDunderClassWithClassAttrNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitDunderDocWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testImplicitDunderSizeOfWithClassAttrNewStyleClass() {
    assertUnresolved();
  }

  public void testImplicitClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testImplicitDunderClassWithInheritedClassAttr() {
    assertUnresolved();
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
    assertUnresolved();
  }

  public void testInstanceUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClassNewStyleClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
  }

  public void testInstanceDunderDocNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testInstanceDunderSizeOfNewStyleClass() {
    final PyFunction expression = assertResolvesTo(PyFunction.class, PyNames.SIZEOF);
    assertIsBuiltin(expression);
  }

  public void testInstanceUndeclaredClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testInstanceDunderClassWithClassAttr() {
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
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
  public void testInstanceDunderClassWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderDocWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceDunderSizeOfWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testInstanceClassAttrNewStyleClass() {
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
    assertUnresolved();
  }

  public void testTypeDunderDoc() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOf() {
    assertUnresolved();
  }

  public void testTypeUndeclaredClassAttr() {
    assertUnresolved();
  }

  // PY-13734
  public void testTypeDunderClassNewStyleClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
  }

  public void testTypeDunderDocNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testTypeDunderSizeOfNewStyleClass() {
    final PyFunction expression = assertResolvesTo(PyFunction.class, PyNames.SIZEOF);
    assertIsBuiltin(expression);
  }

  public void testTypeUndeclaredClassAttrNewStyleClass() {
    assertUnresolved();
  }

  // PY-13734
  public void testTypeDunderClassWithClassAttr() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
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
  public void testTypeDunderClassWithClassAttrNewStyleClass() {
    assertIsBuiltin(assertResolvesTo(PyFunction.class, PyNames.__CLASS__));
  }

  public void testTypeDunderDocWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeDunderSizeOfWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testTypeClassAttrNewStyleClass() {
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
  public void testDunderClassInDeclarationNewStyleClass() {
    assertUnresolved();
  }

  public void testDunderDocInDeclarationNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);
    assertIsBuiltin(expression);
  }

  public void testDunderSizeOfInDeclarationNewStyleClass() {
    assertUnresolved();
  }

  public void testUndeclaredClassAttrInDeclarationNewStyleClass() {
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
  public void testDunderClassInDeclarationWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.__CLASS__);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderDocInDeclarationWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.DOC);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testDunderSizeOfInDeclarationWithClassAttrNewStyleClass() {
    final PyTargetExpression expression = assertResolvesTo(PyTargetExpression.class, PyNames.SIZEOF);

    final PyClass cls = expression.getContainingClass();
    assertNotNull(cls);
    assertEquals("A", cls.getName());
  }

  public void testClassAttrInDeclarationNewStyleClass() {
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

  // PY-22763
  public void testComparisonOperatorReceiver() {
    final PsiElement element = doResolve();
    final PyFunction dunderLt = assertInstanceOf(element, PyFunction.class);
    assertEquals("__lt__", dunderLt.getName());
    assertEquals("str", dunderLt.getContainingClass().getName());
  }

  // PY-26006
  public void testSOEDecoratingFunctionWithSameNameDecorator() {
    final PyFunction function = assertInstanceOf(doResolve(), PyFunction.class);
    assertEquals(4, function.getTextOffset());
  }

  // PY-23259
  public void testTypingListInheritor() {
    assertResolvesTo(PyFunction.class, "append");
  }

  // PY-23259
  public void testImportedTypingListInheritor() {
    myFixture.copyDirectoryToProject("resolve/" + getTestName(false), "");
    assertResolvesTo(PyFunction.class, "append");
  }

  // PY-27863
  public void testAttributeClassLevelAnnotation() {
    myFixture.copyDirectoryToProject("resolve/" + getTestName(false), "");

    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "some_attr");

    final PsiFile file = myFixture.getFile();
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), file);
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

    // It's like an attempt to find type annotation for attribute on the class level.
    final PyClassTypeImpl classType = new PyClassTypeImpl(target.getContainingClass(), true);
    assertEmpty(classType.resolveMember(target.getReferencedName(), target, AccessDirection.READ, resolveContext, true));

    assertProjectFilesNotParsed(file);
    assertSdkRootsNotParsed(file);
  }

  // PY-28228
  public void testReturnAnnotationForwardReference() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> assertResolvesTo(PyClass.class, "A")
    );
  }

  // PY-28228
  public void testParameterAnnotationForwardReference() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> assertResolvesTo(PyClass.class, "A")
    );
  }

  // PY-19890
  public void testUnboundVariableOnClassLevelDeclaredBelowAsTarget() {
    final PyTargetExpression foo = assertResolvesTo(PyTargetExpression.class, "foo");

    final PyExpression value = foo.findAssignedValue();
    assertInstanceOf(value, PyStringLiteralExpression.class);
    assertEquals("global", ((PyStringLiteralExpression)value).getStringValue());
  }

  // PY-19890
  public void testUnboundVariableOnClassLevelDeclaredBelowAsMethod() {
    final PyTargetExpression foo = assertResolvesTo(PyTargetExpression.class, "foo");

    final PyExpression value = foo.findAssignedValue();
    assertInstanceOf(value, PyStringLiteralExpression.class);
    assertEquals("global", ((PyStringLiteralExpression)value).getStringValue());
  }

  // PY-19890
  public void testUnboundVariableOnClassLevelDeclaredBelowAsClass() {
    final PyTargetExpression foo = assertResolvesTo(PyTargetExpression.class, "foo");

    final PyExpression value = foo.findAssignedValue();
    assertInstanceOf(value, PyStringLiteralExpression.class);
    assertEquals("global", ((PyStringLiteralExpression)value).getStringValue());
  }

  // PY-19890
  public void testUnboundVariableOnClassLevelDeclaredBelowAsImport() {
    final PyTargetExpression foo = assertResolvesTo(PyTargetExpression.class, "foo");

    final PyExpression value = foo.findAssignedValue();
    assertInstanceOf(value, PyStringLiteralExpression.class);
    assertEquals("global", ((PyStringLiteralExpression)value).getStringValue());
  }

  // PY-19890
  public void testUnboundVariableOnClassLevelDeclaredBelowAsImportWithAs() {
    final PyTargetExpression foo = assertResolvesTo(PyTargetExpression.class, "foo");

    final PyExpression value = foo.findAssignedValue();
    assertInstanceOf(value, PyStringLiteralExpression.class);
    assertEquals("global", ((PyStringLiteralExpression)value).getStringValue());
  }

  // PY-26947
  public void testVariableDeclaredOnClassLevelResolvesOnlyToItself() {
    final PyTargetExpression foo = assertResolvesTo(PyTargetExpression.class, "foo");

    final PyExpression value = foo.findAssignedValue();
    assertInstanceOf(value, PyStringLiteralExpression.class);
    assertEquals("correct", ((PyStringLiteralExpression)value).getStringValue());
  }

  // PY-29975
  public void testUnboundVariableOnClassLevelNotDeclaredBelow() {
    assertResolvesTo(PyNamedParameter.class, "foo");
  }

  // PY-30512
  public void testDunderBuiltins() {
    final PsiElement element = doResolve();
    assertEquals(PyBuiltinCache.getInstance(myFixture.getFile()).getBuiltinsFile(), element);
  }

  // PY-35531
  public void testOverloadedDunderInit() {
    final PyFile file = (PyFile)myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), file);

    final PyFunction function = file.findTopLevelClass("A").findInitOrNew(false, context);
    assertNotNull(function);
    assertFalse(PyiUtil.isOverload(function, context));
  }

  // PY-33886
  public void testAssignmentExpressions() {
    assertResolvesTo(
      """
        if a := b:
            print(a)
                  <ref>""",
      PyTargetExpression.class,
      "a"
    );

    assertResolvesTo(
      "[y := 2, y**2]\n" +
      "         <ref>",
      PyTargetExpression.class,
      "y"
    );

    assertResolvesTo(
      "[y for x in data if (y := f(x))]\n" +
      " <ref>",
      PyTargetExpression.class,
      "y"
    );

    assertResolvesTo(
      """
        len(lines := [])
        print(lines)
               <ref>""",
      PyTargetExpression.class,
      "lines"
    );
  }

  // PY-33886
  public void testAssignmentExpressionGoesToOuterScope() {
    assertResolvesTo(
      """
        if any({(comment := line).startswith('#') for line in lines}):
            print("First comment:", comment)
                                     <ref>""",
      PyTargetExpression.class,
      "comment"
    );

    assertResolvesTo(
      """
        if all((nonblank := line).strip() == '' for line in lines):
            pass
        else:
            print("First non-blank line:", nonblank)
                                             <ref>""",
      PyTargetExpression.class,
      "nonblank"
    );
  }

  // PY-33886
  public void testAssignmentExpressionGoesFromOuterScope() {
    assertResolvesTo(
      "[[x * y for x in range(5)] for i in range(5) if (y := i)]\n" +
      "      <ref>",
      PyTargetExpression.class,
      "y"
    );
  }

  // PY-33886
  public void testAssignmentExpressionsAndOuterVar() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> {
        final ResolveResult[] results = multiResolve();

        final PsiElement first = results[0].getElement();
        assertResolveResult(first, PyTargetExpression.class, "total");
        assertInstanceOf(first.getParent(), PyAssignmentStatement.class);

        final PsiElement second = results[1].getElement();
        assertResolveResult(second, PyTargetExpression.class, "total");
        assertInstanceOf(second.getParent(), PyAssignmentExpression.class);
      }
    );
  }

  // PY-38220
  public void testAssignedQNameForTargetInitializedWithSubscriptionExpression() {
    final PyFile file = (PyFile)myFixture.configureByText(PythonFileType.INSTANCE, "import a\nt = a.b[c]");
    assertNull(file.findTopLevelAttribute("t").getAssignedQName());
  }

  // PY-38220
  public void testResolvingAssignedValueForTargetInitializedWithSubscriptionExpression() {
    final PyFile file = (PyFile)myFixture.configureByText(PythonFileType.INSTANCE, "import a\nt = a.b[c]");
    myFixture.addFileToProject("a.py", "b = {}  # type: dict"); // specify type of `b` so `__getitem__` could be resolved

    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(myFixture.getProject());
    assertEmpty(file.findTopLevelAttribute("t").multiResolveAssignedValue(PyResolveContext.defaultContext(context)));
  }

  // PY-36062
  public void testModuleTypeAttributes() {
    myFixture.copyDirectoryToProject("resolve/" + getTestName(false), "");
    final PyTargetExpression target = assertResolvesTo(PyTargetExpression.class, "__name__");
    assertEquals("ModuleType", target.getContainingClass().getName());
    assertEquals("types.pyi", target.getContainingFile().getName());
  }

  // PY-16760
  public void testGoogleDocstringAttributeNameResolvesToClassAttribute() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-16760
  public void testGoogleDocstringAttributeNameResolvesToInstanceAttributeOverClassAttribute() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> {
      PyTargetExpression definition = assertResolvesTo(PyTargetExpression.class, "attr1");
      assertTrue(PyUtil.isInstanceAttribute(definition));
    });
  }

  // PY-16760
  public void testGoogleDocstringAttributeNameResolvesToInstanceAttributeOverInitParameter() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-16760
  public void testNumpyDocstringAttributeNameResolvesToClassAttribute() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-28549
  public void testGoogleDocstringAttributeNameResolvesToDataclassClassAttribute() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-28549
  public void testNumpyDocstringAttributeNameResolvesToDataclassClassAttribute() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-28549
  public void testNumpyDocstringParameterNameResolvesToDataclassClassAttributeWithoutInit() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-28549
  public void testNumpyDocstringParameterNameUnresolvedWithInit() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertUnresolved());
  }

  // PY-28549
  public void testNumpyDocstringParameterNameResolvesToDataclassInitParameterOverClassAttribute() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyNamedParameter.class, "attr1"));
  }

  // PY-35743
  public void testGoogleDocstringAttributeNameResolvesToNamedTupleClassAttribute() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-35743
  public void testNumpyDocstringAttributeNameResolvesToNamedTupleClassAttribute() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyTargetExpression.class, "attr1"));
  }

  // PY-55609
  public void testRestDocstringVarNameResolvesToInstanceAttributeOverInitParameter() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      PyTargetExpression definition = assertResolvesTo(PyTargetExpression.class, "var1");
      assertTrue(PyUtil.isInstanceAttribute(definition));
    });
  }

  // PY-55609
  public void testRestDocstringVarNameResolvesToInstanceAttributeOverClassAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      PyTargetExpression definition = assertResolvesTo(PyTargetExpression.class, "var1");
      assertTrue(PyUtil.isInstanceAttribute(definition));
    });
  }

  // PY-55609
  public void testRestDocstringIvarNameResolvesToInstanceAttributeOverInitParameter() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      PyTargetExpression definition = assertResolvesTo(PyTargetExpression.class, "var1");
      assertTrue(PyUtil.isInstanceAttribute(definition));
    });
  }

  // PY-55609
  public void testRestDocstringCvarNameResolvesToClassAttributeOverInitParameter() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      PyTargetExpression definition = assertResolvesTo(PyTargetExpression.class, "var1");
      assertTrue(PyUtil.isClassAttribute(definition));
    });
  }

  // PY-55609
  public void testRestDocstringCvarNameResolvesToClassAttributeOverInstanceAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      PyTargetExpression definition = assertResolvesTo(PyTargetExpression.class, "var1");
      assertTrue(PyUtil.isClassAttribute(definition));
    });
  }

  // PY-55609
  public void testRestDocstringVarNameResolvesToClassAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyTargetExpression.class, "var1"));
  }

  // PY-55609
  public void testRestDocstringTypeOwnerNameResolvesToInitParameterOverClassAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyNamedParameter.class, "p"));
  }

  // PY-55609
  public void testRestDocstringTypeOwnerNameResolvesToInitParameterOverInstanceAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyNamedParameter.class, "p"));
  }

  // PY-46654
  public void testRestDocstringIvarNameResolvesToDataClassAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyTargetExpression.class, "var1"));
  }

  // PY-50788
  public void testRestDocstringIvarNameResolvesToInheritedInstanceAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyTargetExpression.class, "attr"));
  }

  // PY-50788
  public void testRestDocstringVarNameResolvesToInheritedInstanceAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyTargetExpression.class, "attr"));
  }

  // PY-50788
  public void testRestDocstringVarNameResolvesToInheritedClassAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyTargetExpression.class, "attr"));
  }

  // PY-50788
  public void testRestDocstringCvarNameResolvesToInheritedClassAttribute() {
    runWithDocStringFormat(DocStringFormat.REST, () -> assertResolvesTo(PyTargetExpression.class, "attr"));
  }

  // PY-50788
  public void testNumpyDocstringAttributeNameResolvesToInheritedInstanceAttribute() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyTargetExpression.class, "bar"));
  }

  // PY-50788
  public void testNumpyDocstringAttributeNameResolvesToInheritedClassAttribute() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> assertResolvesTo(PyTargetExpression.class, "bar"));
  }

  // PY-61878
  public void testTypeAliasStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeAliasStatement.class, "myType");
    });
  }

  // PY-61878
  public void testTypeParameterResolvedInsideTypeAliasStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideNamedParameterInFunctionDeclaration() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideNestedFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testFunctionParameterDefaultValueNotResolvedToTypeParameterInFunctionDeclaration() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTargetExpression.class, "T");
    });
  }

  // PY-61877
  public void testDecoratorArgumentNotResolvedToTypeParameterOfDecoratedFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertNotResolved();
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideReturnTypeInFunctionDeclaration() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testNotResolvedToTypeParameterOutsideOfFunctionScope() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertNotResolved();
    });
  }

  // PY-61877
  public void testNotResolvedToTypeParameterOutsideOfClassScope() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertNotResolved();
    });
  }

  // PY-61878
  public void testNotResolvedToTypeParameterOutsideOfTypeAliasStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTargetExpression.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideClassDeclaration() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideVariableAnnotationInsideFunction() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideClassAttributeAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideNamedParameterOfClassMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideReturnTypeAnnotationOfClassMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testVariableInsideFunctionResolvedToTypeParameterInsteadOfGlobalVariableWithTheSameName() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testTypeParameterResolvedInsideNestedClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testGlobalVariableRedeclarationWithSameAsTypeParameterNameInsideNestedClassNotResolvedToTypeParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTargetExpression.class, "T");
    });
  }

  // PY-61877
  public void testReferenceInFunctionInsideNestedClassResolvedToTypeParameterIfTheOuterClassIsParameterized() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTypeParameter.class, "T");
    });
  }

  // PY-61877
  public void testReferenceInFunctionInsideNestedClassResolvedToGlobalVariableIfTheOuterClassIsNotParameterized() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTargetExpression.class, "T");
    });
  }

  // PY-61877
  public void testReferenceInsideNestedFunctionNotResolvedToTypeParameterOfOuterClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      assertResolvesTo(PyTargetExpression.class, "T");
    });
  }

  // PY-61877 PY-63366
  public void testNestedClassNotResolvedInsideMethodOfNewStyleGenericClass() {
    assertNotResolved();
  }

  // PY-61877 PY-63367
  public void testNestedClassResolvedInsideAnnotationOfNewStyleGenericMethod() {
    assertResolvesTo(PyClass.class, "Nested");
  }

  // PY-61877
  public void testNestedClassIsResolvedInSuperclassListOfAnotherNewStyleGenericNestedClass() {
    assertResolvesTo(PyClass.class, "Nested");
  }

  // PY-61877
  public void testNewStyleTypeParameterNotResolvedAsClassAttribute() {
    assertNotResolved();
  }

  // [TODO] daniil.kalinin enable when resolve for collisions in type parameter names and class attribute names is implemented
  // PY-61877
  //public void testClassAttributeDeclarationWithSameAsTypeParameterNameNotResolvedToTypeParameter() {
  //  runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
  //    assertResolvesTo(PyTargetExpression.class, "T");
  //  });
  //}

  // [TODO] daniil.kalinin enable when resolve for collisions in type parameter names and class attribute names is implemented
  // PY-61877
  //public void testGlobalVariableRedeclarationWithSameAsTypeParameterNameInsideClassNotResolvedToTypeParameter() {
  //  runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
  //    assertResolvesTo(PyTargetExpression.class, "T");
  //  });
  //}


  // PY-17627
  public void testClassAttributeDefinedInSameClassMethod() {
    assertResolvesTo(PyTargetExpression.class, "attr");
  }

  // PY-17627
  public void testClassAttributeDefinedInOtherClassMethod() {
    PyTargetExpression classAttr = assertResolvesTo(PyTargetExpression.class, "attr");
    PyFunction containingMethod = assertInstanceOf(ScopeUtil.getScopeOwner(classAttr), PyFunction.class);
    assertEquals("first", containingMethod.getName());
  }

  // PY-17627
  public void testClassAttributeDefinedInAncestorClassMethod() {
    assertResolvesTo(PyTargetExpression.class, "attr");
  }

  // PY-17627
  public void testClassAttributeInitializationInClassHasPrecedenceOverSameClassMethod() {
    PyTargetExpression classAttr = assertResolvesTo(PyTargetExpression.class, "attr");
    assertInstanceOf(ScopeUtil.getScopeOwner(classAttr), PyClass.class);
  }

  // PY-17627
  public void testClassAttributeInitializationInSameClassMethodHasPrecedenceOverOtherClassMethods() {
    PyTargetExpression classAttr = assertResolvesTo(PyTargetExpression.class, "attr");
    PyFunction containingMethod = assertInstanceOf(ScopeUtil.getScopeOwner(classAttr), PyFunction.class);
    assertEquals("current", containingMethod.getName());
  }

  // PY-17627
  public void testClassAttributeResolvesToNextClassMethodIfItsOwnDefinitionPrecedesUsage() {
    PyTargetExpression classAttr = assertResolvesTo(PyTargetExpression.class, "attr");
    PyFunction containingMethod = assertInstanceOf(ScopeUtil.getScopeOwner(classAttr), PyFunction.class);
    assertEquals("next", containingMethod.getName());
  }

  // PY-34617
  public void testModuleAttributeUnderVersionCheck() {
    String decl = """
      import sys
            
      if True:
          if sys.version_info >= (3,):
              if sys.version_info >= (3, 10) and sys.version_info < (3, 12):
                  foo = 23
              if sys.version_info < (3, 11) and (sys.version_info < (3, 5) or sys.version_info > (3, 7)):
                  buz = 23
          else:
              bar = -1

      """;

    String foo = decl + """
      foo
       <ref>""";
    Consumer<PsiElement> fooTargetExpr = e -> assertResolveResult(e, PyTargetExpression.class, "foo", null);
    String buz = decl + """
      buz
       <ref>""";
    Consumer<PsiElement> buzTargetExpr = e -> assertResolveResult(e, PyTargetExpression.class, "buz", null);
    String bar = decl + """
      bar
       <ref>""";
    Consumer<PsiElement> barTargetExpr = e -> assertResolveResult(e, PyTargetExpression.class, "bar", null);

    assertResolvedElement(LanguageLevel.PYTHON310, foo, fooTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON310, buz, buzTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON310, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON312, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON312, buz, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON312, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON38, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON38, buz, buzTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON38, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON37, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON37, buz, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON37, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON34, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON34, buz, buzTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON34, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON27, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, buz, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, bar, barTargetExpr);
  }

  // PY-34617
  public void testModuleAttributeUnderVersionCheckMultifile() {
    myFixture.copyDirectoryToProject("resolve/ModuleAttributeUnderVersionCheck", "");
    String foo = """
      import mod
      mod.foo
           <ref>""";
    Consumer<PsiElement> fooTargetExpr = e -> assertResolveResult(e, PyTargetExpression.class, "foo", "mod.py");
    String buz = """
      import mod
      mod.buz
           <ref>""";
    Consumer<PsiElement> buzTargetExpr = e -> assertResolveResult(e, PyTargetExpression.class, "buz", "mod.py");
    String bar = """
      import mod
      mod.bar
           <ref>""";
    Consumer<PsiElement> barTargetExpr = e -> assertResolveResult(e, PyTargetExpression.class, "bar", "mod.py");

    assertResolvedElement(LanguageLevel.PYTHON310, foo, fooTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON310, buz, buzTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON310, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON312, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON312, buz, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON312, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON38, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON38, buz, buzTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON38, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON37, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON37, buz, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON37, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON34, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON34, buz, buzTargetExpr);
    assertResolvedElement(LanguageLevel.PYTHON34, bar, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON27, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, buz, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, bar, barTargetExpr);
  }

  // PY-34617
  public void testClassAttributeUnderVersionCheck() {
    String classDecl = """
      import sys
            
      if sys.version_info < (4,):
          class MyClass:
              if sys.version_info >= (3,):
                  def foo(self):
                      pass
              elif sys.version_info < (2, 5):
                  def bar(self):
                      pass
              else:
                  def buz(self):
                      pass

      """;

    String foo = classDecl + """
      MyClass().foo()
                 <ref>""";
    String bar = classDecl + """
      MyClass().bar()
                 <ref>""";
    String buz = classDecl + """
      MyClass().buz()
                 <ref>""";

    assertResolvedElement(LanguageLevel.PYTHON310, foo, e -> assertResolveResult(e, PyFunction.class, "foo", null));
    assertResolvedElement(LanguageLevel.PYTHON310, bar, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON310, buz, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON24, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON24, bar, e -> assertResolveResult(e, PyFunction.class, "bar", null));
    assertResolvedElement(LanguageLevel.PYTHON24, buz, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON27, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, bar, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, buz, e -> assertResolveResult(e, PyFunction.class, "buz", null));
  }

  // PY-34617
  public void testClassAttributeUnderVersionCheckMultifile() {
    myFixture.copyDirectoryToProject("resolve/ClassAttributeUnderVersionCheck", "");
    String foo = """
      from mod import MyClass
      m = MyClass()
      m.foo()
         <ref>""";
    String bar = """
      from mod import MyClass
      m = MyClass()
      m.bar()
         <ref>""";
    String buz = """
      from mod import MyClass
      m = MyClass()
      m.buz()
         <ref>""";

    assertResolvedElement(LanguageLevel.PYTHON310, foo, e -> assertResolveResult(e, PyFunction.class, "foo", "mod.py"));
    assertResolvedElement(LanguageLevel.PYTHON310, bar, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON310, buz, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON24, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON24, bar, e -> assertResolveResult(e, PyFunction.class, "bar", "mod.py"));
    assertResolvedElement(LanguageLevel.PYTHON24, buz, TestCase::assertNull);

    assertResolvedElement(LanguageLevel.PYTHON27, foo, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, bar, TestCase::assertNull);
    assertResolvedElement(LanguageLevel.PYTHON27, buz, e -> assertResolveResult(e, PyFunction.class, "buz", "mod.py"));
  }

  // PY-34617
  public void testImportUnderVersionCheckMultifile() {
    myFixture.copyDirectoryToProject("resolve/ImportUnderVersionCheck", "");
    String plainImport = """
      from mod import *
      math
       <ref>""";
    assertResolvedElement(LanguageLevel.PYTHON35, plainImport, e -> assertResolveResult(e, PyFile.class, "math.pyi", null));
    assertResolvedElement(LanguageLevel.PYTHON34, plainImport, TestCase::assertNull);

    String importAlias = """
      from mod import *
      cm
       <ref>""";
    assertResolvedElement(LanguageLevel.PYTHON35, importAlias, e -> assertResolveResult(e, PyFile.class, "cmath.pyi", null));
    assertResolvedElement(LanguageLevel.PYTHON34, importAlias, TestCase::assertNull);
  }

  // PY-34617
  public void testImportFromUnderVersionCheckMultifile() {
    myFixture.copyDirectoryToProject("resolve/ImportUnderVersionCheck", "");
    String plainImport = """
      from mod import *
      digits
       <ref>""";
    assertResolvedElement(LanguageLevel.PYTHON35, plainImport, e -> assertResolveResult(e, PyTargetExpression.class, "digits", null));
    assertResolvedElement(LanguageLevel.PYTHON34, plainImport, TestCase::assertNull);

    String importAlias = """
      from mod import *
      imported_name
       <ref>""";
    assertResolvedElement(LanguageLevel.PYTHON35, importAlias, e -> assertResolveResult(e, PyTargetExpression.class, "hexdigits", null));
    assertResolvedElement(LanguageLevel.PYTHON34, importAlias, TestCase::assertNull);

    String starImport = """
      from mod import *
      DivisionByZero
       <ref>""";
    assertResolvedElement(LanguageLevel.PYTHON35, starImport, e -> assertResolveResult(e, PyClass.class, "DivisionByZero", null));
    assertResolvedElement(LanguageLevel.PYTHON34, starImport, TestCase::assertNull);
  }

  // PY-77168
  public void testResolveFromUnderUnmatchedVersionCheck() {
    assertResolvesTo("""
                       import sys
                       
                       Alias = int
                       if sys.version_info < (3, 12):
                           name: Alias
                       #          <ref>
                       """, PyTargetExpression.class, "Alias");
  }

  private void assertResolvedElement(@NotNull LanguageLevel languageLevel, @NotNull String text, @NotNull Consumer<PsiElement> assertion) {
    runWithLanguageLevel(languageLevel, () -> {
      myFixture.configureByText(PythonFileType.INSTANCE, text);
      PsiElement element = PyCommonResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
      assertion.accept(element);
    });
    assertFilesNotParsed();
  }

  private void assertFilesNotParsed() {
    final PsiFile file = myFixture.getFile();
    assertProjectFilesNotParsed(file);
    assertSdkRootsNotParsed(file);
  }
}
