// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.idea.TestFor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Subsystems.CodeInsight
@Layers.Functional
public class PyParameterInfoBehaviourTest extends PyTestCase {

  private EditorHintFixture myHintFixture;

  private static final String baseTestOverloadsText = """
    from typing import overload
    @overload
    def foo(a: int, b: int, c: int): ...
    @overload
    def foo(a: str, b: int, c: int): ...
    @overload
    def foo(a: str, b: str, c: int): ...
    def foo(a, b, c): ...
    
    """;

  public void testOverloadsExpandedOnTheSecondCall() {
    configurePython(baseTestOverloadsText + "foo(<caret>)");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          """);
  }

  public void testOverloadsCollapsedAgainOnTheThirdCall() {
    configurePython(baseTestOverloadsText + "foo(<caret>)");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          """);
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -""");
  }

  public void testNoExtraDescriptionsOnFunctionWithoutOverloads() {
    configurePython("""
                      def foo(a, b, c, d): ...
                      foo(<caret>)
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>a,</b> b, c, d</html>");
    showParameterInfo();
    checkParameterInfos("<html><b>a,</b> b, c, d</html>");
  }

  public void testAllOverloadsShownOnTheSecondActionCallWithOneFilledArgument() {
    configurePython(baseTestOverloadsText + "foo(1, <caret>)");
    showParameterInfo();
    checkParameterInfos("""
                          <html>a, <b>b: int,</b> c</html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html>a, <b>b: int,</b> c</html>
                          -
                          <html>a, <b>b: int,</b> c</html>
                          -
                          <html>a, <b>b: str,</b> c</html>
                          """);
  }

  public void testOverloadsStayExpandedAfterMoveRight() {
    configurePython(baseTestOverloadsText + "foo(1<caret>, 2, 3)");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          """);
    moveCaret(true, 2);
    checkParameterInfos("""
                          <html>a, <b>b: int,</b> c</html>
                          -
                          <html>a, <b>b: int,</b> c</html>
                          -
                          <html>a, <b>b: str,</b> c</html>
                          """);

  }

  public void testOverloadsStayExpandedAfterMoveLeft() {
    configurePython(baseTestOverloadsText + "foo(1, <caret>2, 3)");
    showParameterInfo();
    checkParameterInfos("""
                          <html>a, <b>b: int,</b> c</html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html>a, <b>b: int,</b> c</html>
                          -
                          <html>a, <b>b: int,</b> c</html>
                          -
                          <html>a, <b>b: str,</b> c</html>
                          """);
    moveCaret(false, 2);
    checkParameterInfos("""
                          <html><b>a: int,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          -
                          <html><b>a: str,</b> b, c</html>
                          """);
  }

  public void testOverloadsDoNotCollapseOnMovingAcrossWhitespaces() {
    configurePython(baseTestOverloadsText + "foo(1, 2, 3<caret>              )");
    showParameterInfo();
    checkParameterInfos("""
                          <html>a, b, <b>c: int</b></html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html>a, b, <b>c: int</b></html>
                          -
                          <html>a, b, <b>c: int</b></html>
                          -
                          <html>a, b, <b>c: int</b></html>
                          """);
    moveCaret(true, 5);
    checkParameterInfos("""
                          <html>a, b, <b>c: int</b></html>
                          -
                          <html>a, b, <b>c: int</b></html>
                          -
                          <html>a, b, <b>c: int</b></html>
                          """);
  }

  public void testExpandOnBuiltinMax() {
    configurePython("max(<caret>)");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>arg1: SupportsRichComparisonT,</b> arg2, /, _args, key</html>
                          -""");
    showParameterInfo();
    checkParameterInfos("""
                          <html><b>arg1: SupportsRichComparisonT,</b> arg2, /, _args, key</html>
                          -
                          <html><b>arg1: _T,</b> arg2, /, _args, key</html>
                          -
                          <html><b>iterable: Iterable[SupportsRichComparisonT],</b> /, *, key</html>
                          -
                          <html><b>iterable: Iterable[_T],</b> /, *, key</html>
                          -
                          <html><b>iterable: Iterable[SupportsRichComparisonT],</b> /, *, key, default</html>
                          -
                          <html><b>iterable: Iterable[_T1],</b> /, *, key, default</html>
                          """);
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfGenericClassFromTyping() {
    configurePython("""
                      from typing import Generator
                      def f() -> Generator[<caret>]: ...
                      """);
    showParameterInfo();
    // typeshed declares PEP 696 defaults on Generator's send/return type parameters
    checkParameterInfos("<html><b>_YieldT_co,</b> _SendT_contra = None, _ReturnT_co = None</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testCurrentTypeParameterHighlightedByPosition() {
    configurePython("""
                      from typing import Generator
                      def f() -> Generator[int, <caret>]: ...
                      """);
    showParameterInfo();
    checkParameterInfos("<html>_YieldT_co, <b>_SendT_contra = None,</b> _ReturnT_co = None</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfBuiltinDict() {
    configurePython("x: dict[<caret>]");
    showParameterInfo();
    checkParameterInfos("<html><b>_KT,</b> _VT</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfUserGenericClassOldStyle() {
    configurePython("""
                      from typing import Generic, TypeVar
                      T = TypeVar('T')
                      S = TypeVar('S')
                      class C(Generic[T, S]): ...
                      c: C[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T,</b> S</html>");
  }


  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfUserGenericClass() {
    configurePython("""
                      class C[T, S]: ...
                      c: C[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T,</b> S</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParameterDefaultIsShown() {
    configurePython("""
                      from typing import Generic, TypeVar
                      T = TypeVar('T')
                      S = TypeVar('S', default=int)
                      class C[T, S=int]: ...
                      c: C[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T,</b> S = int</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfGenericTypeAliasOldStyle() {
    configurePython("""
                      from typing import TypeVar
                      T = TypeVar('T')
                      Alias = list[T]
                      x: Alias[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T</b></html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfNonParameterizedGenericClassNameAlias() {
    configurePython("""
                      from typing import TypeVar
                      T = TypeVar('T')
                      Alias = list
                      x: Alias[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>_T</b></html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfGenericTypeAliasStatement() {
    configurePython("""
                      type Alias[T] = list[T]
                      x: Alias[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T</b></html>");
  }

  @TestFor(issues = "PY-73402")
  public void testNestedSubscriptionShowsInnerTypeParameters() {
    configurePython("x: dict[str, list[<caret>]]");
    showParameterInfo();
    checkParameterInfos("<html><b>_T</b></html>");
  }

  @TestFor(issues = "PY-73402")
  public void testNoTypeParameterInfoForValueSubscription() {
    configurePython("""
                      d = {1: 2}
                      d[<caret>]
                      """);
    showParameterInfo();
    checkNoParameterInfo();
  }

  @TestFor(issues = "PY-73402")
  public void testNoTypeParameterInfoForNonGenericClass() {
    configurePython("""
                      class P: ...
                      x: P[<caret>]
                      """);
    showParameterInfo();
    checkNoParameterInfo();
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParameterBoundAndDefault() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      configurePython("""
                        class A[T: int = int, R: int = str]: ...
                        a: A[<caret>]
                        """);
      showParameterInfo();
      checkParameterInfos("<html><b>T: int = int,</b> R: int = str</html>");
    });
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParameterBoundFromLegacyTypeVar() {
    configurePython("""
                      from typing import Generic, TypeVar
                      T = TypeVar('T', bound=int)
                      class C(Generic[T]): ...
                      c: C[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T: int</b></html>");
  }

  @TestFor(issues = "PY-73402")
  public void testParamSpecTypeParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      configurePython("""
                        class C[**P]: ...
                        c: C[<caret>]
                        """);
      showParameterInfo();
      checkParameterInfos("<html><b>**P</b></html>");
    });
  }

  @TestFor(issues = "PY-73402")
  public void testTypeVarTupleTypeParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      configurePython("""
                        class C[*Ts]: ...
                        c: C[<caret>]
                        """);
      showParameterInfo();
      checkParameterInfos("<html><b>*Ts</b></html>");
    });
  }

  @TestFor(issues = "PY-73402")
  public void testMixedTypeParametersWithVariadics() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      configurePython("""
                        class C[T, *Ts, **P]: ...
                        c: C[<caret>]
                        """);
      showParameterInfo();
      checkParameterInfos("<html><b>T,</b> *Ts, **P</html>");
    });
  }

  @TestFor(issues = "PY-73402")
  public void testCurrentTypeParameterHighlightedWhenCaretOnArgument() {
    configurePython("""
                      class C[T, S]: ...
                      c: C[in<caret>t, str]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T,</b> S</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testCurrentTypeParameterHighlightedBeforeArgument() {
    configurePython("""
                      class C[T, S]: ...
                      c: C[<caret>int, str]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T,</b> S</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testCurrentTypeParameterHighlightedBeforeComma() {
    configurePython("""
                      class C[T, S]: ...
                      c: C[int<caret>, str]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>T,</b> S</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testCurrentTypeParameterHighlightedAfterComma() {
    configurePython("""
                      class C[T, S]: ...
                      c: C[int,<caret> str]
                      """);
    showParameterInfo();
    checkParameterInfos("<html>T, <b>S</b></html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTrailingTypeVarTupleAbsorbsExtraArguments() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      configurePython("""
                        class C[T, *Ts]: ...
                        c: C[int, str, <caret>]
                        """);
      showParameterInfo();
      checkParameterInfos("<html>T, <b>*Ts</b></html>");
    });
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfExplicitTypeAlias() {
    configurePython("""
                      from typing import TypeAlias, TypeVar
                      K = TypeVar('K')
                      V = TypeVar('V')
                      Alias: TypeAlias = dict[K, V]
                      x: Alias[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>K,</b> V</html>");
  }

  @TestFor(issues = "PY-73402")
  public void testTypeParametersOfTypingCollectionAlias() {
    configurePython("""
                      from typing import List
                      x: List[<caret>]
                      """);
    showParameterInfo();
    checkParameterInfos("<html><b>_T</b></html>");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHintFixture = new EditorHintFixture(getTestRootDisposable());
  }

  protected void configurePython(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
  }

  protected void showParameterInfo() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
  }

  private void moveCaret(boolean right, int steps) {
    for (int i = 0; i < steps; i++) {
      myFixture.performEditorAction(right ? IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT : IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
    }
  }

  public static void waitForParameterInfo() {
    for (int i = 0; i < 3; i++) {
      UIUtil.dispatchAllInvocationEvents();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
  }

  private void checkParameterInfos(String hintText) {
    try {
      ParameterInfoControllerBase.waitForDelayedActions(myFixture.getEditor(), 1, TimeUnit.MINUTES);
    }
    catch (TimeoutException e) {
      fail("Timed out waiting for parameter info update");
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    waitForParameterInfo();
    assertEquals(hintText.trim(), myHintFixture.getCurrentHintText());
  }

  private void checkNoParameterInfo() {
    try {
      ParameterInfoControllerBase.waitForDelayedActions(myFixture.getEditor(), 1, TimeUnit.MINUTES);
    }
    catch (TimeoutException e) {
      fail("Timed out waiting for parameter info update");
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    waitForParameterInfo();
    assertNull(myHintFixture.getCurrentHintText());
  }
}
