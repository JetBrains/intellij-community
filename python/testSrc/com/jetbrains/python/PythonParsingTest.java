package com.jetbrains.python;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.openapi.application.PathManager;

/**
 * @author yole
 */
public class PythonParsingTest extends ParsingTestCase {
  public PythonParsingTest() {
    super("", "py");
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData";
  }

  public void testHelloWorld() throws Exception {
    doTest(true);
  }

  public void testConditionalExpression() throws Exception {
    doTest(true);
  }

  public void testConditionalParenLambda() throws Exception {
    doTest(true);
  }

  public void testLambdaComprehension() throws Exception {
    doTest(true);
  }

  public void testLambdaConditional() throws Exception {
    doTest(true);
  }

  public void testTryExceptFinally() throws Exception {
    doTest(true);
  }

  public void testTryFinally() throws Exception {
    doTest(true);
  }

  public void testYieldStatement() throws Exception {
    doTest(true);
  }

  public void testYieldInAssignment() throws Exception {
    doTest(true);
  }

  public void testYieldInAugAssignment() throws Exception {
    doTest(true);
  }

  public void testYieldInParentheses() throws Exception {
    doTest(true);
  }

  public void _testYieldAsArgument() throws Exception {
    // this is a strange case: PEP 342 says this syntax is valid, but
    // Python 2.5 doesn't accept it. let's stick with Python behavior for now
    doTest(true);
  }

  public void testWithStatement() throws Exception {
    doTest(true);
  }
}
