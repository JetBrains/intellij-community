package com.jetbrains.python;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.application.PathManager;

/**
 * @author yole
 */
public class PythonHighlightingTest extends DaemonAnalyzerTestCase {
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/highlighting/";
  }

  public void testReturnOutsideOfFunction() throws Exception {
    doTest();
  }

  public void testContinueInFinallyBlock() throws Exception {
    doTest();
  }

  public void testReturnWithArgumentsInGenerator() throws Exception {
    doTest();
  }

  public void testYieldOutsideOfFunction() throws Exception {
    doTest();
  }

  public void testMalformedStringUnterminated() throws Exception {
    doTest();
  }

  public void testMalformedStringEscaped() throws Exception {
    doTest();
  }

  public void testStringEscapedOK() throws Exception {
    doTest();
  }

  public void testDuplicateClassName() throws Exception {
    doTest();
  }

  public void _testMethodParams() throws Exception {
    doTest();
  }


  private void doTest() throws Exception {
    doTest(getTestName(true) + ".py", true, true);
  }
}
