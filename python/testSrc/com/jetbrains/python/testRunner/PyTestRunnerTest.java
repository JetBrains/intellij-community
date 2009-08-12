package com.jetbrains.python.testRunner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.SystemProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyTestRunnerTest extends LightPlatformTestCase {
  public void testEmptySuite() throws ExecutionException {
    String[] result = runUTRunner(PathManager.getHomePath());
    assertEquals("##teamcity[testCount count='0']", result [0]);
  }

  public void testFile() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    File testFile = new File(testDir, "unittest1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath());
    assertEquals(StringUtil.join(result, "\n"), 6, result.length);
    assertEquals("##teamcity[testCount count='2']", result [0]);
    assertEquals("##teamcity[testStarted location='python_uttestid://unittest1.BadTest.test_fails' name='test_fails (unittest1.BadTest)']", result[1]);
    assertTrue(result [2], result[2].startsWith("##teamcity[testFailed") && result [2].contains("name='test_fails (unittest1.BadTest)'"));
  }

  public void testClass() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    File testFile = new File(testDir, "unittest1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest");
    assertEquals(StringUtil.join(result, "\n"), 3, result.length);
  }

  public void testMethod() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    File testFile = new File(testDir, "unittest1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest::test_passes");
    assertEquals(StringUtil.join(result, "\n"), 3, result.length);
  }

  public void testFolder() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    String[] result = runUTRunner(testDir.getPath(), testDir.getPath() + "/");
    assertEquals(StringUtil.join(result, "\n"), 8, result.length);
  }

  public void testDependent() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner");
    String[] result = runUTRunner(testDir.getPath(), new File(testDir, "dependentTests/my_class_test.py").getPath());
    assertEquals(StringUtil.join(result, "\n"), 3, result.length);
  }

  private static String[] runUTRunner(String workDir, String... args) throws ExecutionException {
    File helpersDir = new File(PathManager.getHomePath(), "plugins/python/helpers");
    File utRunner = new File(helpersDir, "pycharm/utrunner.py");
    List<String> allArgs = new ArrayList<String>();
    allArgs.add(utRunner.getPath());
    Collections.addAll(allArgs, args);
    final ProcessOutput output = runJython(workDir, helpersDir.getPath(), allArgs.toArray(new String[allArgs.size()]));
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
    return result.toArray(new String[result.size()]);
  }

  private static ProcessOutput runJython(String workDir, String pythonPath, String... args) throws ExecutionException {
    final SimpleJavaSdkType sdkType = new SimpleJavaSdkType();
    final Sdk ideaJdk = sdkType.createJdk("tmp", SystemProperties.getJavaHome());
    SimpleJavaParameters parameters = new SimpleJavaParameters();
    parameters.setJdk(ideaJdk);
    parameters.setMainClass("org.python.util.jython");

    File jythonJar = new File(PathManager.getHomePath(), "plugins/python/lib/jython.jar");
    parameters.getClassPath().add(jythonJar.getPath());

    parameters.getProgramParametersList().add("-Dpython.path=" + pythonPath + ";" + workDir);
    parameters.getProgramParametersList().addAll(args);
    parameters.setWorkingDirectory(workDir);

    final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(sdkType.getVMExecutablePath(ideaJdk), parameters, false);
    final CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine.createProcess());
    return processHandler.runProcess();
  }
}
