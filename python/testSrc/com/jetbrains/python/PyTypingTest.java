// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.intellij.idea.TestFor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyCollectionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * legacy, use a `PyCodeInsightTestCase` suite
 */
@Subsystems.CodeInsight
@Layers.Functional
public class PyTypingTest extends PyTestCase {

  public void testStringLiteralInjection() {
    doTestInjectedText("""
                         class C:
                             def foo(self, expr: '<caret>C'):
                                 pass
                         """,
                       "C");
  }

  public void testStringLiteralInjectionParameterizedType() {
    doTestInjectedText("""
                         from typing import Union, List
                         
                         class C:
                             def foo(self, expr: '<caret>Union[List[C], C]'):
                                 pass
                         """,
                       "Union[List[C], C]");
  }

  // PY-37515
  public void testNoStringLiteralInjectionUnderCall() {
    doTestNoInjectedText("x: call('<caret>List[str]')");
  }

  @TestFor(issues="PY-82245")
  public void testStringLiteralInjectionUnderCallWithNestedTypeForm() {
    doTestInjectedText("x: call(set['<caret>str'])");
  }

  @TestFor(issues="PY-88670")
  public void testStringLiteralInjectionBaseClasses() {
    doTestInjectedText("class A(set['<caret>int']): pass");
  }

  @TestFor(issues="PY-82245")
  public void testStringLiteralInjectionNestedSubscriptionBaseClass() {
    doTestInjectedText("class A(set[set['<caret>int']]): pass");
  }

  @TestFor(issues="PY-88670")
  public void testNoStringLiteralInjectionForNonTypeBaseClass() {
    doTestNoInjectedText("class A(namedtuple('Model', '<caret>int')): pass");
  }

  @TestFor(issues="PY-88670")
  public void testNoStringLiteralInjectionForNonGenericMetaclass() {
    doTestNoInjectedText(
      """
        class M(type):
            def __getitem__(self, item): ...
        
        class A(metaclass=M): pass
        
        print(A["<caret>foo"])
        """);
  }

  // PY-15810
  public void testNoStringLiteralInjectionForNonTypingStrings() {
    doTestNoInjectedText("""
                           class C:
                               def foo(self, expr: '<caret>foo bar'):
                                   pass
                           """);
  }

  // PY-42334
  public void testStringLiteralInjectionForExplicitTypeAlias() {
    doTestInjectedText("""
                         from typing import TypeAlias
                         
                         Alias: TypeAlias = 'any + <caret>text'""",
                       "any + text");
  }

  // PY-42334
  public void testStringLiteralInjectionForExplicitTypeAliasUsingTypeComment() {
    doTestInjectedText("""
                         from typing import TypeAlias
                         
                         Alias = 'any + <caret>text'  # type: TypeAlias""",
                       "any + text");
  }

  // PY-22620
  public void testVariableTypeCommentInjectionTuple() {
    doTestInjectedText("x, y = undefined()  # type: int,<caret> int",
                       "int, int");
  }

  // PY-21195
  public void testVariableTypeCommentWithSubsequentComment() {
    doTestInjectedText("x, y = undefined()  # type: int,<caret> str # comment",
                       "int, str");
  }

  // PY-21195
  public void testVariableTypeCommentWithSubsequentCommentWithoutSpacesInBetween() {
    doTestInjectedText("x, y = undefined()  # type: int,<caret> str# comment",
                       "int, str");
  }

  // PY-22620
  public void testVariableTypeCommentInjectionParenthesisedTuple() {
    doTestInjectedText("x, y = undefined()  # type: (int,<caret> int)",
                       "(int, int)");
  }

  // PY-22620
  public void testForTypeCommentInjectionTuple() {
    doTestInjectedText("for x, y in undefined():  # type: int,<caret> int\n" +
                       "    pass",
                       "int, int");
  }

  // PY-22620
  public void testWithTypeCommentInjectionTuple() {
    doTestInjectedText("with undefined() as (x, y):  # type: int,<caret> int\n" +
                       "    pass",
                       "int, int");
  }

  // PY-18726
  public void testFunctionTypeCommentBadCallableParameter1() {
    doTest("Any",
           """
             from typing import Callable, Tuple
             
             def f(cb):
                 # type: (Callable[Tuple[bool, str], int]) -> None
                 expr = cb""");
  }

  // PY-18726
  public void testFunctionTypeCommentBadCallableParameter2() {
    doTest("(bool, int) -> Any",
           """
             from typing import Callable, Tuple
             
             def f(cb):
                 # type: (Callable[[bool, int], [int]]) -> None
                 expr = cb""");
  }

  // PY-24990
  public void _testClsAnnotationReceiverUnionType() {
    doTest("Union[A, B]",
           """
             from typing import TypeVar, Type
             
             T = TypeVar('T')
             
             class Base:
                 @classmethod
                 def factory(cls: Type[T]) -> T:
                     pass
             
             class A(Base):
                 pass
             
             class B(Base):
                 pass
             
             expr = (A or B).factory()""");
  }

  // PY-35235
  public void testNoStringLiteralInjectionForTypingLiteral() {
    doTestNoInjectedText("""
                           from typing import Literal
                           a: Literal["f<caret>oo"]
                           """);

    doTestNoInjectedText("""
                           from typing import Literal
                           a: Literal[42, "f<caret>oo", True]
                           """);

    doTestNoInjectedText("""
                           from typing import Literal
                           MyType = Literal[42, "f<caret>oo", True]
                           a: MyType
                           """);

    doTestNoInjectedText("""
                           from typing import Literal, TypeAlias
                           MyType: TypeAlias = Literal[42, "f<caret>oo", True]
                           """);

    doTestNoInjectedText("""
                           import typing
                           a: typing.Literal["f<caret>oo"]
                           """);
  }

  // PY-41847
  public void testNoStringLiteralInjectionForTypingAnnotated() {
    doTestNoInjectedText("""
                           from typing import Annotated
                           MyType = Annotated[str, "f<caret>oo", True]
                           a: MyType
                           """);

    doTestNoInjectedText("""
                           from typing import Annotated
                           a: Annotated[int, "f<caret>oo", True]
                           """);

    doTestInjectedText("from typing import Annotated\n" +
                       "a: Annotated['Forward<caret>Reference', 'foo']",
                       "ForwardReference");
  }

  // PY-35370
  public void testAnyArgumentsCallableInTypeComment() {
    doTestInjectedText("from typing import Callable\n" +
                       "a = b  # type: Call<caret>able[..., int]",
                       "Callable[..., int]");
  }

  // PY-56541
  public void testRecursiveTypedDictDeclarations() {
    //RecursionManager.assertOnRecursionPrevention(getTestRootDisposable());
    StringBuilder text = new StringBuilder("""
                                             from __future__ import annotations
                                             from typing import TypedDict, Union
                                             """);
    int typedDictCount = 30;
    for (int i = 1; i <= typedDictCount; i++) {
      text.append(String.format("""
                                  class D%d(TypedDict):
                                      key%d: Alias
                                  """, i, i));
    }
    text.append("Alias = Union[");
    for (int i = 1; i <= typedDictCount; i++) {
      text.append("D").append(i).append(", ");
    }
    text.append("]\n");
    text.append("expr: D1\n");

    myFixture.configureByText(PythonFileType.INSTANCE, text.toString());
    PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    PyType type = codeAnalysis.getType(expr);
    assertInstanceOf(type, PyTypedDictType.class);
    assertTrue(countTypes(type) < 100);
  }

  public static int countTypes(@Nullable PyType type) {
    int result = 1;
    if (type instanceof PyUnionType pyUnionType) {
      for (PyType member : pyUnionType.getMembers()) {
        result += countTypes(member);
      }
    }
    else if (type instanceof PyCollectionType pyCollectionType) {
      for (PyType member : pyCollectionType.getElementTypes()) {
        result += countTypes(member);
      }
    }
    return result;
  }

  // PY-76243
  public void testGenericClassDeclaredInStubPackage() {
    runWithAdditionalClassEntryInSdkRoots("types/" + getTestName(false) + "/site-packages", () -> {
      doTest("MyClass[int]",
             """
               from pkg.mod import MyClass
               expr: MyClass[int]
               """);
    });
  }

  // PY-77940
  public void testUnderscoredNameInPyiStub() {
    doMultiFileStubAwareTest("int", """
      from lib import f
      
      expr = f()
      """);
  }

  // PY-82869
  public void testEscapedMultilineTypeHint() {
    doTest("int | str", """
      expr: '''
        int |
        str
      '''
      """);
  }

  @TestFor(issues = "PY-89012")
  public void testPydanticFieldInsideAnnotatedConstructorSignature() {
    myFixture.copyDirectoryToProject("stubs/pydantic", "pydantic");
    doTestExpressionUnderCaret("(*, A: str | None, B: str | None) -> MyModel", """
          from typing import Annotated
          from pydantic import BaseModel, Field

          class MyModel(BaseModel):
              a: str | None = Field(default=None, alias="A")
              b: Annotated[str | None, Field(default=None, alias="B")]

          MyMo<caret>del()
          """);
  }

  // PY-87012
  public void testLegacyTypeAliasesWithQuotedUnionTypesPreservedInStubs() {
    doMultiFileStubAwareTest("list[int | str]", """
      from mod import x
      
      expr = x
      """);
  }

  // PY-87012
  public void testLegacyTypeAliasWithFullyQuotedTypeIsNotAllowed() {
    doMultiFileStubAwareTest("Any", """
      from mod import x
      
      expr = x
      """);
  }

  // PY-76922
  public void testLegacyTypeAliasesWithQuotedIntersectionTypesPreservedInStubs() {
    doMultiFileStubAwareTest("list[int & str]", """
      from mod import x
      
      expr = x
      """);
  }

  public void testPsiStubbedAny() {
    withNewAnyTypeEnabled(() -> {
      runWithAdditionalFileInLibDir("other.py", """
        from typing import Any
        
        x: Any
        """, x -> {
        myFixture.configureByText(PythonFileType.INSTANCE, """
          from other import x
          
          expr = x
          """);
        final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
        final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
        assertType("Failed in code analysis context", "Any", expr, codeAnalysis);
      });
    });
  }

  public void testPsiStubbedUnknown() {
    withNewAnyTypeEnabled(() -> {
      runWithAdditionalFileInLibDir("other.py", """
        x = asdf
        """, x -> {
        myFixture.configureByText(PythonFileType.INSTANCE, """
          from other import x
          
          expr = x
          """);
        final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
        final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
        assertType("Failed in code analysis context", "Unknown", expr, codeAnalysis);
      });
    });
  }

  private void doTestNoInjectedText(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNull(host);
  }

  private void doTestInjectedText(@NotNull String text) {
    doTestInjectedText(text, null);
  }

  private void doTestInjectedText(@NotNull String text, String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElementAtCaret());
    assertNotNull(host);
    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    assertNotNull(files);
    assertFalse(files.isEmpty());
    final PsiElement injected = files.get(0).getFirst();
    if (expected != null) {
      assertEquals(expected, injected.getText());
    }
    assertFalse(PsiTreeUtil.hasErrorElements(injected));
  }

  private void doTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);
    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  private void doTestExpressionUnderCaret(@NotNull String expectedType, @NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PyExpression.class);
    TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  private void doMultiFileStubAwareTest(@NotNull final String expectedType, @NotNull final String text) {
    myFixture.copyDirectoryToProject("types/" + getTestName(false), "");
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyExpression expr = myFixture.findElementByText("expr", PyExpression.class);

    final TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertProjectFilesNotParsed(expr.getContainingFile());

    final TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }

  private void doMultiFileExpressionUnderCaretTest(@NotNull String expectedType, @NotNull String text) {
    myFixture.copyDirectoryToProject("types/" + getTestName(false), "");
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PyExpression.class);

    TypeEvalContext codeAnalysis = TypeEvalContext.codeAnalysis(expr.getProject(), expr.getContainingFile());
    assertType("Failed in code analysis context", expectedType, expr, codeAnalysis);
    assertProjectFilesNotParsed(expr.getContainingFile());

    TypeEvalContext userInitiated = TypeEvalContext.userInitiated(expr.getProject(), expr.getContainingFile()).withTracing();
    assertType("Failed in user initiated context", expectedType, expr, userInitiated);
  }
}
