package com.jetbrains.python.refactoring;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.refactoring.RefactoringActionHandler;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodUtil;

/**
 * @author oleg
 */
public class PyExtractMethodTest extends LightMarkedTestCase {
  private void doTest(final String testPath,
                      final String name,
                      final String result) {

    myFixture.configureByFile("/refactoring/extractmethod/" + testPath);
    final RefactoringActionHandler handler = LanguageRefactoringSupport.INSTANCE.forLanguage(PythonLanguage.getInstance()).getExtractMethodHandler();
    try {
      System.setProperty(PyExtractMethodUtil.NAME, name);
      try {
        handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), ((EditorEx) myFixture.getEditor()).getDataContext());
      }
      catch (Exception e) {
        if (result.endsWith(".py")) { // expected output file, not an exception
          e.printStackTrace();
        }
        assertEquals(result, e.getMessage());
        return;
      }
    } finally {
      System.clearProperty(PyExtractMethodUtil.NAME);
    }
    myFixture.checkResultByFile("/refactoring/extractmethod/" + result);
  }

  public void testParameter() {
    doTest("outEmpty/parameter.before.py", "bar", "outEmpty/parameter.after.py");
  }

  public void testBreakAst() {
    doTest("outEmpty/break_ast.before.py", "bar", "outEmpty/break_ast.after.py");
  }

  public void testExpression() {
    doTest("outEmpty/expression.before.py", "plus", "outEmpty/expression.after.py");
  }

  public void testStatement() {
    doTest("outEmpty/statement.before.py", "foo", "outEmpty/statement.after.py");
  }

  public void testStatements() {
    doTest("outEmpty/statements.before.py", "foo", "outEmpty/statements.after.py");
  }

  public void testStatementReturn() {
    doTest("outEmpty/statement_return.before.py", "foo", "outEmpty/statement_return.after.py");
  }

  public void testBinaryExpression() {
    doTest("controlFlow/binary_expr.before.py", "foo", "controlFlow/binary_expr.after.py");
  }

  public void testWhileOutput() {
    doTest("controlFlow/while_output.before.py", "bar", "controlFlow/while_output.after.py");
  }

  public void testNameCollisionClass() {
    doTest("namesCollision/class.before.py", "hello", "Method name clashes with already existing method name");
  }

  public void testNameCollisionFile() {
    doTest("namesCollision/file.before.py", "hello", "Method name clashes with already existing method name");
  }

  public void testNameCollisionSuperClass() {
    doTest("namesCollision/superclass.before.py", "hello", "Method name clashes with already existing method name");
  }

  public void testOutNotEmptyStatements() {
    doTest("outNotEmpty/statements.before.py", "sum_squares", "outNotEmpty/statements.after.py");
  }

  public void testOutNotEmptyStatements2() {
    doTest("outNotEmpty/statements2.before.py", "sum_squares", "outNotEmpty/statements2.after.py");
  }

  public void testComment() {
    doTest("comment.before.py", "bar", "comment.after.py");
  }

  public void testFile() {
    doTest("file.before.py", "bar", "file.after.py");
  }


  public void testMethod() {
    doTest("context/method.before.py", "bar", "context/method.after.py");
  }

  public void testMethodIndent() {
    doTest("context/methodindent.before.py", "bar", "context/methodindent.after.py");
  }

  public void testMethodReturn() {
    doTest("context/methodreturn.before.py", "bar", "context/methodreturn.after.py");
  }

  public void testWrongSelectionIfPart() {
    doTest("wrongSelection/ifpart.before.py", "bar", "Cannot perform extract method using selected element(s)");
  }

  public void testFromImportStatement() {
    doTest("wrongSelection/fromimport.before.py", "bar", "Cannot perform refactoring with from import statement inside code block");
  }

  public void testPy479() {
    doTest("outEmpty/py479.before.py", "bar", "outEmpty/py479.after.py");
  }

  public void testClass() {
    doTest("context/class.before.py", "bar", "context/class.after.py");
  }

  public void testConditionalReturn() {
    doTest("conditionalreturn.before.py", "bar", "Cannot perform refactoring when execution flow is interrupted");
  }

  public void testReturnTuple() {
    doTest("return_tuple.before.py", "bar", "return_tuple.after.py");
  }

  public void testComment2() {
    doTest("comment2.before.py", "baz", "comment2.after.py");
  }

  public void testElseBody() {
    doTest("elsebody.before.py", "baz", "elsebody.after.py");
  }

  public void testClassMethod() {
    doTest("classmethod.before.py", "baz", "classmethod.after.py");
  }

  public void testStaticMethod() {
    doTest("staticmethod.before.py", "baz", "staticmethod.after.py");
  }
}
