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
package com.jetbrains.python.testRunner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.testing.JythonUnitTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyTestRunnerTest extends LightPlatformTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public PyTestRunnerTest() {
    PyTestCase.initPlatformPrefix();
  }

  public void testEmptySuite() throws ExecutionException {
    String[] result = runUTRunner(PathManager.getHomePath(), "true");
    assertEquals("##teamcity[testCount count='0']", result [1]);
  }

  public void testFile() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath(), "true");
    assertEquals(StringUtil.join(result, "\n"), 11, result.length);
    assertEquals("##teamcity[enteredTheMatrix]", result [0]);
    assertEquals("##teamcity[testCount count='2']", result [1]);
    assertTrue(result[2].endsWith("test1.BadTest' name='test1.BadTest']"));
    assertTrue(result[3].endsWith("test1.BadTest.test_fails' name='test_fails']"));
    assertTrue(result [4], result[4].startsWith("##teamcity[testFailed") && result [4].contains("name='test_fails'"));
  }

  private static File getTestDataDir() {
    return new File(PythonTestUtil.getTestDataPath(), "/testRunner/tests");
  }

  public void testClass() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest", "true");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  public void testMethod() throws ExecutionException {
    final File testDir = getTestDataDir();
    File testFile = new File(testDir, "test1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest::test_passes", "true");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  public void testFolder() throws ExecutionException {
    final File testDir = getTestDataDir();
    String[] result = runUTRunner(testDir.getPath(), testDir.getPath() + "/", "true");
    assertEquals(StringUtil.join(result, "\n"), 15, result.length);
  }

  public void testDependent() throws ExecutionException {
    final File testDir = new File(PythonTestUtil.getTestDataPath(), "testRunner");
    String[] result = runUTRunner(testDir.getPath(), new File(testDir, "dependentTests/test_my_class.py").getPath(), "true");
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
  }

  private static String[] runUTRunner(String workDir, String... args) throws ExecutionException {
    File helpersDir = new File(PyTestCase.getHelpersPath());
    File utRunner = new File(helpersDir, "pycharm/utrunner.py");
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
