package com.jetbrains.python.testRunner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.testing.JythonUnitTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 */
public class PyDocTestRunnerTest extends LightPlatformTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public PyDocTestRunnerTest() {
    PyLightFixtureTestCase.initPlatformPrefix();
  }

  public void testEmptySuite() throws ExecutionException {
    String[] result = runUTRunner(PathManager.getHomePath());
    assertEquals("##teamcity[testCount count='0']", result [0]);
  }

  public void testFile() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath());
    assertEquals(StringUtil.join(result, "\n"), 13, result.length);
    assertEquals("##teamcity[testCount count='3']", result [0]);
    assertEquals("##teamcity[testSuiteStarted name='test_file.FirstGoodTest']", result [1]);
  }

  private static File getTestDataDir() {
    return new File(PythonTestUtil.getTestDataPath(), "/testRunner/doctests");
  }

  public void testClass() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::FirstGoodTest");
    assertEquals(StringUtil.join(result, "\n"), 5, result.length);
  }

  public void testMethod() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::SecondGoodTest::test_passes");
    assertEquals(StringUtil.join(result, "\n"), 5, result.length);
  }

  public void testFunction() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test_file.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::factorial");
    assertEquals(StringUtil.join(result, "\n"), 5, result.length);
  }

  private static String[] runUTRunner(String workDir, String... args) throws ExecutionException {
    File helpersDir = new File(PathManager.getHomePath(), "python/helpers");
    File utRunner = new File(helpersDir, "pycharm/docrunner.py");
    List<String> allArgs = new ArrayList<String>();
    allArgs.add(utRunner.getPath());
    Collections.addAll(allArgs, args);
    final ProcessOutput output = JythonUnitTestUtil.runJython(workDir, helpersDir.getPath(), ArrayUtil.toStringArray(allArgs));
    assertEquals(output.getStderr(), 0, splitLines(output.getStderr()).length);
    return splitLines(output.getStdout());
  }

  private static String[] splitLines(final String out) {
    List<String> result = new ArrayList<String>();
    final String[] lines = StringUtil.convertLineSeparators(out).split("\n");
    for (String line : lines) {
      if (line.length() > 0 && !line.contains("*sys-package-mgr*")) {
        result.add(line);
      }
    }
    return ArrayUtil.toStringArray(result);
  }
}
