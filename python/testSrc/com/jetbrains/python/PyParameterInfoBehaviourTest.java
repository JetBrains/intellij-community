// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.codeInsight.hint.ParameterInfoControllerBase;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
}
