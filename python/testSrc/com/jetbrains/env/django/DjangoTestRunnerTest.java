package com.jetbrains.env.django;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.django.facet.DjangoFacetConfiguration;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.testRunner.DjangoTestsRunConfiguration;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
public class DjangoTestRunnerTest extends PyEnvTestCase {

  private static void doTest(@NotNull final String output, @NotNull final String testDataPath) {
    final String[] splittedOutput = output.split("\\n");
    final ArrayList<String> outputList = Lists.newArrayList();
    for (String s : splittedOutput) {
      outputList.add(norm(s));
    }
    assertEquals(outputList.indexOf(testDataPath),
                 outputList.lastIndexOf(testDataPath));
  }

  private static String norm(String testDataPath) {
    return FileUtil.toSystemIndependentName(testDataPath);
  }

  private void doTest(final String name, @Nullable final String projectRoot, @Nullable final String settings,
                      @Nullable final String markAsSource) {
    runPythonTest(new DjangoTestRunnerTestTask() {
      @Override
      public ConfigurationFactory getFactory() {
        return DjangoTestsConfigurationType.getInstance().getConfigurationFactories()[0];
      }

      @Override
      protected void configure(AbstractPythonRunConfiguration config) {
        ((DjangoTestsRunConfiguration)config).setTarget("myapp.SimpleTest");
      }

      @Override
      protected List<String> getContentRoots() {
        return markAsSource == null ? Lists.<String>newArrayList() : Lists.newArrayList(markAsSource);
      }

      @Override
      public void before() throws Exception {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          public void run() {
            final Module module = myFixture.getModule();
            final DjangoFacet djangoFacet = DjangoFacet.getInstance(module);
            if (djangoFacet != null && projectRoot != null && settings != null) {
              final DjangoFacetConfiguration configuration = djangoFacet.getConfiguration();
              configuration.setProjectRootPath(getTestDataPath() + projectRoot);
              configuration.setSettingsFilePath(settings);
            }
          }
        });
      }

      public void testing() throws Exception {
        waitForOutput("Process finished with exit code 0");
        doTest(output(), norm(getTestDataPath()));
      }

      @Override
      String getTestName() {
        return name;
      }
    });
  }

  public void testDjango13() {
    doTest(getTestName(false), null, null, null);
  }

  public void testDjango14() {
    doTest(getTestName(false), "", "Django14/settings.py", null);
  }

  public void testDjango15() {
    doTest(getTestName(false), "", "Django15/settings.py", null);
  }

  public void testRoot13() {
    doTest(getTestName(false), "/Django13", "settings.py", null);
  }

  public void testRoot() {
    doTest(getTestName(false), "/DjangoRoot", "DjangoRoot/settings.py", null);
  }

  public void testRootMarked() {
    doTest(getTestName(false), "/DjangoRoot", "DjangoRoot/settings.py", "/DjangoRoot");
  }

}
