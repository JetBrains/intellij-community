package com.jetbrains.python.refactoring;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.util.Pair;
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
                      final String result,
                      final Pair<String, String>... files2Create) throws Exception {

    // Create additional files
    for (Pair<String, String> pair : files2Create) {
      myFixture.addFileToProject(pair.first, pair.second);
    }
    myFixture.configureByFile("/refactoring/extractmethod/" + testPath);
    final RefactoringActionHandler handler = LanguageRefactoringSupport.INSTANCE.forLanguage(PythonLanguage.getInstance()).getExtractMethodHandler();
    try {
      System.setProperty(PyExtractMethodUtil.NAME, name);
      try {
        handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new FileDataContext(myFixture.getFile()));
      }
      catch (Exception e) {
        assertEquals(result, e.getMessage());
        return;
      }
    } finally {
      System.clearProperty(PyExtractMethodUtil.NAME);
    }
    myFixture.checkResultByFile("/refactoring/extractmethod/" + result);
  }

  public void testParameter() throws Throwable {
    doTest("outEmpty/parameter.before.py", "bar", "outEmpty/parameter.after.py");
  }

  public void testBreakAst() throws Throwable {
    doTest("outEmpty/break_ast.before.py", "bar", "outEmpty/break_ast.after.py");
  }

  public void testExpression() throws Throwable {
    doTest("outEmpty/expression.before.py", "plus", "outEmpty/expression.after.py");
  }

  public void testStatement() throws Throwable {
    doTest("outEmpty/statement.before.py", "foo", "outEmpty/statement.after.py");
  }

  public void testStatements() throws Throwable {
    doTest("outEmpty/statements.before.py", "foo", "outEmpty/statements.after.py");
  }

  public void testStatementReturn() throws Throwable {
    doTest("outEmpty/statement_return.before.py", "foo", "outEmpty/statement_return.after.py");
  }

  public void testBinaryExpression() throws Throwable {
    doTest("controlFlow/binary_expr.before.py", "foo", "controlFlow/binary_expr.after.py");
  }

  public void testWhileOutput() throws Throwable {
    doTest("controlFlow/while_output.before.py", "bar", "controlFlow/while_output.after.py");
  }

  public void testNameCollisionClass() throws Throwable {
    doTest("namesCollision/class.before.py", "hello", "Method name clashes with already existing method name");
  }

  public void testNameCollisionFile() throws Throwable {
    doTest("namesCollision/file.before.py", "hello", "Method name clashes with already existing method name");
  }

  public void testNameCollisionSuperClass() throws Throwable {
    doTest("namesCollision/superclass.before.py", "hello", "Method name clashes with already existing method name");
  }

  public void testOutNotEmptyStatements() throws Throwable {
    doTest("outNotEmpty/statements.before.py", "sum_squares", "outNotEmpty/statements.after.py");
  }

  public void testOutNotEmptyStatements2() throws Throwable {
    doTest("outNotEmpty/statements2.before.py", "sum_squares", "outNotEmpty/statements2.after.py");
  }

  public void testComment() throws Throwable {
    doTest("comment.before.py", "bar", "comment.after.py");
  }

  public void testFile() throws Throwable {
    doTest("file.before.py", "bar", "file.after.py");
  }


  public void testMethod() throws Throwable {
    doTest("context/method.before.py", "bar", "context/method.after.py");
  }

  public void testMethodIndent() throws Throwable {
    doTest("context/methodindent.before.py", "bar", "context/methodindent.after.py");
  }

  public void testMethodReturn() throws Throwable {
    doTest("context/methodreturn.before.py", "bar", "context/methodreturn.after.py");
  }

  public void testWrongSelectionIfPart() throws Throwable {
    doTest("wrongSelection/ifpart.before.py", "bar", "Cannot perform extract method using selected element(s)");
  }

  public void testFromImportStatement() throws Throwable {
    doTest("wrongSelection/fromimport.before.py", "bar", "Cannot perform refactoring with from import statement inside code block");
  }

  public void testPy479() throws Throwable {
    doTest("outEmpty/py479.before.py", "bar", "outEmpty/py479.after.py");
  }

  public void testClass() throws Throwable {
    doTest("context/class.before.py", "bar", "context/class.after.py");
  }
}