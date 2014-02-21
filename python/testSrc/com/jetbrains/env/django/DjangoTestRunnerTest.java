package com.jetbrains.env.django;

import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.django.facet.DjangoFacetConfiguration;
import com.jetbrains.django.testRunner.DjangoTestsConfigurationType;
import com.jetbrains.django.testRunner.DjangoTestsRunConfiguration;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
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
    boolean add = false;
    for (String s : splittedOutput) {
      if ("sys.path end".equals(s)) add = false;
      if (add) outputList.add(norm(s));
      if ("sys.path start".equals(s)) add = true;
    }
    assertEquals(outputList.indexOf(testDataPath),
                 outputList.lastIndexOf(testDataPath));
  }

  private static String norm(String testDataPath) {
    return FileUtil.toSystemIndependentName(testDataPath);
  }

  private void doTest(@Nullable final String projectRoot, @Nullable final String settings,
                      @Nullable final String markAsSource, @Nullable final String customSettings) {
    final String name = getTestName(false);
    runPythonTest(new DjangoTestRunnerTestTask() {
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
            final List<String> version = StringUtil.split(django.getVersion(), ".");
            if (Integer.parseInt(version.get(1)) >= 6)
              target = "mysite.tests.SimpleTest";
          }
        }
        catch (PyExternalProcessException ignored) {
        }

        ((DjangoTestsRunConfiguration)config).setTarget(target);
        if (customSettings != null)
          ((DjangoTestsRunConfiguration)config).setSettingsFile(getTestDataPath() + customSettings);
          ((DjangoTestsRunConfiguration)config).useCustomSettings(true);
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

  public void testDjango11() {
    doTest();
  }

  public void testDjango12() {
    doTest("", "settings.py", "", null);
  }

  public void testDjango13() {
    doTest();
  }

  private void doTest() {
    doTest(null, null, null, null);
  }

  public void testDjango14() {
    doTest("", "Django14/settings.py", null, null);
  }

  public void testDjango15() {
    doTest("", "Django15/settings.py", null, null);
  }

  public void testRoot13() {
    doTest("/Django13", "settings.py", null, null);
  }

  public void testRoot() {
    doTest("/DjangoRoot", "DjangoRoot/settings.py", null, null);
  }

  public void testRootMarked() {
    doTest("/DjangoRoot", "DjangoRoot/settings.py", "/DjangoRoot", null);
  }

  public void testSameNamedDir() {
    doTest("/web/SameNamedDir", "settings.py", null, null);
  }

  public void testCustomSettings() {
    doTest(null, null, null, "/custom_settings.py");
  }

  public void testInstalledApps() {
    doTest("/InstalledApps", "/config/settings/installedapps.py", null, "/config/settings/installedapps.py");
  }

}
