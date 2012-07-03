package com.jetbrains.env.django;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.jetbrains.django.run.DjangoServerRunConfiguration;
import com.jetbrains.django.run.DjangoServerRunConfigurationType;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.testRunner.DjangoTestsRunConfiguration;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;

/**
 * User : catherine
 */
public class DjangoPathTest extends PyEnvTestCase {

  public void testRunserverPath() throws IOException {
    runPythonTest(new DjangoPathTestTask() {

      @Override
      protected void configure(AbstractPythonRunConfiguration config) throws IOException {
        final int[] ports = DjangoTemplateDebuggerTest.findFreePorts(2);
        ((DjangoServerRunConfiguration)config).setPort(ports[1]);
      }

      @Override
      public ConfigurationFactory getFactory() {
        return DjangoServerRunConfigurationType.getInstance().getConfigurationFactories()[0];
      }


      public void testing() throws Exception {
        waitForOutput("Development server is running");

        final String[] splittedOutput = output().split("\\n");
        final ArrayList<String> outputList = Lists.newArrayList();
        for (String s : splittedOutput) {
          if (s.equals("The end of sys.path"))
            break;
          outputList.add(s);
        }

        assertEquals(getTestDataPath(), outputList.get(1));
        assertTrue(outputList.contains(PythonHelpersLocator.getHelpersRoot().getPath()));
        assertEquals(outputList.indexOf(PythonHelpersLocator.getHelpersRoot().getPath()),
                     outputList.lastIndexOf(PythonHelpersLocator.getHelpersRoot().getPath()));
        assertEquals(outputList.indexOf(getTestDataPath()),
                     outputList.lastIndexOf(getTestDataPath()));
      }

    });

  }

  public void testTestPath() throws IOException {
    runPythonTest(new DjangoPathTestTask() {

      @Override
      public ConfigurationFactory getFactory() {
        return DjangoTestsConfigurationType.getInstance().getConfigurationFactories()[0];
      }

      @Override
      protected void configure(AbstractPythonRunConfiguration config) {
        ((DjangoTestsRunConfiguration)config).setTarget("site.SimpleTest");
      }

      public void testing() throws Exception {
        waitForOutput("The end of sys.path");
        final String[] splittedOutput = output().split("\\n");
        final ArrayList<String> outputList = Lists.newArrayList();
        for (String s : splittedOutput) {
          if (s.equals("The end of sys.path"))
            break;
          outputList.add(s);
        }

        assertEquals(getTestDataPath(), outputList.get(1));
        assertTrue(outputList.contains(PythonHelpersLocator.getHelpersRoot().getPath()));
        assertEquals(outputList.indexOf(PythonHelpersLocator.getHelpersRoot().getPath()),
                     outputList.lastIndexOf(PythonHelpersLocator.getHelpersRoot().getPath()));
        assertEquals(outputList.indexOf(getTestDataPath()),
                     outputList.lastIndexOf(getTestDataPath()));
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
        waitForOutput("The end of sys.path");

        final String[] splittedOutput = output().split("\\n");
        final ArrayList<String> outputList = Lists.newArrayList();
        for (String s : splittedOutput) {
          if (s.equals("The end of sys.path"))
            break;
          outputList.add(s);
        }

        assertEquals(getTestDataPath(), outputList.get(1));
        assertTrue(outputList.contains(PythonHelpersLocator.getHelperPath("pycharm/")));
        assertEquals(outputList.indexOf(PythonHelpersLocator.getHelperPath("pycharm/")),
                     outputList.lastIndexOf(PythonHelpersLocator.getHelperPath("pycharm/")));
        assertEquals(outputList.indexOf(getTestDataPath()),
                     outputList.lastIndexOf(getTestDataPath()));
      }
    });

  }
}
