package com.jetbrains.python.testRunner;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.Key;
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
  private static class Output {
    private final String stdout;
    private final String stderr;

    public Output(String stdout, String stderr) {
      this.stdout = stdout;
      this.stderr = stderr;
    }
  }

  public void testEmptySuite() throws ExecutionException {
    String[] result = runUTRunner(PathManager.getHomePath());
    assertEquals("##teamcity[testCount count='0']", result [0]);
  }

  public void testFile() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    File testFile = new File(testDir, "unittest1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath());
    assertEquals(6, result.length);
    assertEquals("##teamcity[testCount count='2']", result [0]);
    assertEquals("##teamcity[testStarted location='python_uttestid://unittest1.BadTest.test_fails' name='test_fails (unittest1.BadTest)']", result[1]);
    assertTrue(result [2], result[2].startsWith("##teamcity[testFailed name='test_fails (unittest1.BadTest)' details="));
  }

  public void testClass() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    File testFile = new File(testDir, "unittest1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest");
    assertEquals(3, result.length);
  }

  public void testMethod() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    File testFile = new File(testDir, "unittest1.py");
    String[] result = runUTRunner(testDir.getPath(), testFile.getPath() + "::GoodTest::test_passes");
    assertEquals(3, result.length);
  }

  public void testFolder() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner/tests");
    String[] result = runUTRunner(testDir.getPath(), testDir.getPath() + "/");
    assertEquals(8, result.length);
  }

  public void testDependent() throws ExecutionException {
    final File testDir = new File(PathManager.getHomePath(), "plugins/python/testData/testRunner");
    String[] result = runUTRunner(testDir.getPath(), new File(testDir, "dependentTests/my_class_test.py").getPath());
    assertEquals(3, result.length);
  }

  private static String[] runUTRunner(String workDir, String... args) throws ExecutionException {
    File helpersDir = new File(PathManager.getHomePath(), "plugins/python/helpers");
    File utRunner = new File(helpersDir, "pycharm/utrunner.py");
    List<String> allArgs = new ArrayList<String>();
    allArgs.add(utRunner.getPath());
    Collections.addAll(allArgs, args);
    final Output output = runJython(workDir, helpersDir.getPath(), allArgs.toArray(new String[allArgs.size()]));
    assertEquals("", output.stderr);
    return output.stdout.split("\n");
  }

  private static Output runJython(String workDir, String pythonPath, String... args) throws ExecutionException {
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

    final StringBuilder stdout = new StringBuilder();
    final StringBuilder stderr = new StringBuilder();

    final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(sdkType.getVMExecutablePath(ideaJdk), parameters, false);
    final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
      @Override
      public void notifyTextAvailable(String text, Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          stdout.append(text);
        }
        else if (outputType == ProcessOutputTypes.STDERR) {
          stderr.append(text);
        }
      }
    };
    processHandler.startNotify();
    processHandler.waitFor();

    return new Output(StringUtil.convertLineSeparators(stdout.toString(), "\n"),
      StringUtil.convertLineSeparators(stderr.toString(), "\n"));
  }
}
