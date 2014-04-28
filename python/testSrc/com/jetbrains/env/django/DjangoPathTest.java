package com.jetbrains.env.django;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.django.run.DjangoServerRunConfiguration;
import com.jetbrains.django.run.DjangoServerRunConfigurationType;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.testRunner.DjangoTestsRunConfiguration;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import junit.framework.Assert;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;

/**
 * User : catherine
 */
public class DjangoPathTest extends PyEnvTestCase {

  /**
   * This string is printed after printing sys.path in djangoPath/settings.py
   */
  public static final String THE_END_OF_PROCESS = "Process finished with exit code 0";

  public void testRunserverPath() throws IOException {
    runPythonTest(new DjangoPathTestTask() {

      @Override
      protected void configure(AbstractPythonRunConfiguration config) throws IOException {
        final int[] ports = DjangoTemplateDebuggerTest.findFreePorts(2);
        ((DjangoServerRunConfiguration)config).setPort(ports[1]);
        ((DjangoServerRunConfiguration)config).setRunNoReload(true);
      }

      @Override
      public ConfigurationFactory getFactory() {
        return DjangoServerRunConfigurationType.getInstance().getConfigurationFactories()[0];
      }


      public void testing() throws Exception {
        waitForOutput(THE_END_OF_PROCESS);

        doTest(output(), getTestDataPath());
      }
    });
  }

  private static void doTest(String output, String testDataPath) {
    final String[] splittedOutput = output.split("\\n");
    final ArrayList<String> outputList = Lists.newArrayList();

    boolean add = false;
    for (String s : splittedOutput) {
      if ("sys.path end".equals(s)) add = false;
      if (add) outputList.add(norm(s));
      if ("sys.path start".equals(s)) add = true;
    }
    testDataPath = norm(testDataPath);

    Assert.assertEquals(testDataPath, outputList.get(1));

    assertEquals(outputList.indexOf(testDataPath),
                 outputList.lastIndexOf(testDataPath));
  }

  private static String norm(String testDataPath) {
    return FileUtil.toSystemIndependentName(testDataPath);
  }

  public void testTestPath() throws IOException {
    runPythonTest(new DjangoPathTestTask() {

      @Override
      public ConfigurationFactory getFactory() {
        return DjangoTestsConfigurationType.getInstance().getConfigurationFactories()[0];
      }

      @Override
      protected void configure(AbstractPythonRunConfiguration config) {
        String target = "mysite.SimpleTest";
        try {
          final PyPackage django = ((PyPackageManagerImpl)PyPackageManager.getInstance(config.getSdk())).findPackage("django");
          if (django != null) {
            if (django.matches(PyRequirement.fromStringGuaranteed("django>=1.6a"))) {
              target = "mysite.tests.SimpleTest";
            }
          }
        }
        catch (PyExternalProcessException ignored) {
        }

        ((DjangoTestsRunConfiguration)config).setTarget(target);
      }

      public void testing() throws Exception {
        waitForOutput(THE_END_OF_PROCESS);
        doTest(output(), getTestDataPath());
      }
    });
  }

  public void testManagePath() throws IOException {
    runPythonTest(new DjangoPathTestTask() {

      @Nullable
      @Override
      public ConfigurationFactory getFactory() {
        return null;
      }

      public void testing() throws Exception {
        waitForOutput(THE_END_OF_PROCESS);

        final String[] splittedOutput = output().split("\\n");
        final ArrayList<String> outputList = Lists.newArrayList();
        for (String s : splittedOutput) {
          if (s.equals(THE_END_OF_PROCESS)) {
            break;
          }
          outputList.add(s);
        }

        assertEquals(getTestDataPath(), outputList.get(1));
        assertEquals(outputList.indexOf(getTestDataPath()),
                     outputList.lastIndexOf(getTestDataPath()));
      }
    });
  }
}
