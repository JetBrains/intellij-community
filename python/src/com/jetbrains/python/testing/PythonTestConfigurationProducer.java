/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.facet.PythonFacetSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract public class PythonTestConfigurationProducer extends RuntimeConfigurationProducer {
  protected PsiElement myPsiElement;

  public PythonTestConfigurationProducer (final Class configurationTypeClass) {
    super(ConfigurationTypeUtil.findConfigurationType(configurationTypeClass));
  }

  @Override
  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    if (! isAvailable(location)) return null;
    RunnerAndConfigurationSettings settings;

    if (PythonUnitTestRunnableScriptFilter.isIfNameMain(location)) {
      return null;
    }

    settings = createConfigurationFromFolder(location);
    if (settings != null) return settings;

    final PyElement pyElement = PsiTreeUtil.getParentOfType(location.getPsiElement(), PyElement.class, false);
    if (pyElement != null) {
      settings = createConfigurationFromFunction(location, pyElement);
      if (settings != null) return settings;

      settings = createConfigurationFromClass(location, pyElement);
      if (settings != null) return settings;
    }

    settings = createConfigurationFromFile(location, location.getPsiElement());
    if (settings != null) return settings;

    return null;
  }

  protected boolean isAvailable(Location location) {
    return false;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromFunction(Location location, PyElement element) {
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (! isTestFunction(pyFunction)) return null;
    final PyClass containingClass = pyFunction.getContainingClass();

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from function");
    final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)settings.getConfiguration();
    configuration.setMethodName(pyFunction.getName());

    RunConfiguration conf = findConfiguration(location, pyFunction, 1);
    if (conf instanceof AbstractPythonTestRunConfiguration) {
      configuration.setEnvs(((AbstractPythonTestRunConfiguration)conf).getEnvs());
    }
    if (containingClass != null) {
      configuration.setClassName(containingClass.getName());
      configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_METHOD);
    }
    else {
      configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION);
    }
    if (!setupConfigurationScript(configuration, pyFunction)) return null;
    configuration.setName(configuration.suggestedName());
    myPsiElement = pyFunction;
    return settings;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromClass(Location location, PyElement element) {
    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (!isTestClass(pyClass)) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)settings.getConfiguration();

    RunConfiguration conf = findConfiguration(location, pyClass, 2);
    if (conf instanceof AbstractPythonTestRunConfiguration) {
      configuration.setEnvs(((AbstractPythonTestRunConfiguration)conf).getEnvs());
    }
    configuration.setTestType(
      AbstractPythonTestRunConfiguration.TestType.TEST_CLASS);
    configuration.setClassName(pyClass.getName());
    if (!setupConfigurationScript(configuration, pyClass)) return null;
    configuration.setName(configuration.suggestedName());

    myPsiElement = pyClass;
    return settings;
  }

  protected boolean isTestClass(PyClass pyClass) {
    if (pyClass == null || !PythonUnitTestUtil.isTestCaseClass(pyClass)) return false;
    return true;
  }

  protected boolean isTestFunction(PyFunction pyFunction) {
    if (pyFunction == null || !PythonUnitTestUtil.isTestCaseFunction(pyFunction)) return false;
    return true;
  }

  protected boolean isTestFile(PsiElement file) {
    if (file == null || !(file instanceof PyFile)) return false;
    return true;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromFolder(Location location) {
    final PsiElement element = location.getPsiElement();

    if (!(element instanceof PsiDirectory)) return null;

    final Module module = location.getModule();
    if (!isPythonModule(module)) return null;

    PsiDirectory dir = (PsiDirectory)element;
    final VirtualFile file = dir.getVirtualFile();
    final String path = file.getPath();

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
    configuration.setFolderName(path);
    configuration.setWorkingDirectory(path);

    configuration.setName(configuration.suggestedName());
    myPsiElement = dir;
    return settings;
  }


  protected static boolean isPythonModule(Module module) {
    if (module == null) {
      return false;
    }
    if (module.getModuleType() instanceof PythonModuleTypeBase) {
      return true;
    }
    final Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : allFacets) {
      if (facet.getConfiguration() instanceof PythonFacetSettings) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationFromFile(Location location, PsiElement element) {
    PsiElement file = element.getContainingFile();
    if (!isTestFile(file)) return null;

    final PyFile pyFile = (PyFile)file;
    final List<PyStatement> testCases = PythonUnitTestUtil.getTestCaseClassesFromFile(pyFile);
    if (testCases.isEmpty()) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from file");
    final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)settings.getConfiguration();

    RunConfiguration conf = findConfiguration(location, pyFile, 3);
    if (conf instanceof AbstractPythonTestRunConfiguration) {
      configuration.setEnvs(((AbstractPythonTestRunConfiguration)conf).getEnvs());
    }
    configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT);
    if (!setupConfigurationScript(configuration, pyFile)) return null;

    configuration.setName(configuration.suggestedName());
    myPsiElement = pyFile;
    return settings;
  }

  protected RunnerAndConfigurationSettings makeConfigurationSettings(Location location, String name) {
    final RunnerAndConfigurationSettings result =
      RunManager.getInstance(location.getProject()).createRunConfiguration(name, getConfigurationFactory());
    AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)result.getConfiguration();
    configuration.setUseModuleSdk(true);
    configuration.setModule(ModuleUtil.findModuleForPsiElement(location.getPsiElement()));
    return result;
  }

  protected static boolean setupConfigurationScript(AbstractPythonTestRunConfiguration cfg, PyElement element) {
    final PyFile containingFile = PyUtil.getContainingPyFile(element);
    if (containingFile == null) return false;
    final VirtualFile vFile = containingFile.getVirtualFile();
    if (vFile == null) return false;
    final VirtualFile parent = vFile.getParent();
    if (parent == null) return false;

    cfg.setScriptName(containingFile.getName());
    cfg.setWorkingDirectory(parent.getPath());

    return true;
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final RunnerAndConfigurationSettings settings = createConfigurationByElement(location, null);
    if (settings != null) {
      final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)settings.getConfiguration();
      for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
        if (configuration.compareSettings((AbstractPythonTestRunConfiguration)existingConfiguration.getConfiguration())) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }

  @Nullable
  private RunConfiguration findConfiguration(Location location, PyElement element, int level) {
    final RunManager runManager = RunManager.getInstance(element.getProject());
    final RunnerAndConfigurationSettings[] existingConfigurations = runManager.getConfigurationSettings(getConfigurationType());

    RunnerAndConfigurationSettings settings;
    RunConfiguration configuration;
    if (level == 1) {                     // target is function
      PyClass cl = ((PyFunction)element).getContainingClass();
      if (cl != null)
        settings = createConfigurationFromClass(location, cl);
      else
        settings = createConfigurationFromClass(location, element);
      configuration = checkSettings(settings, existingConfigurations);
      if (configuration != null)
        return configuration;
    }
    if (level == 1 || level == 2) {       // target is function or class
      settings = createConfigurationFromFile(location, element);
      configuration = checkSettings(settings, existingConfigurations);
      if (configuration != null)
        return configuration;
    }
    PsiElement e = location.getPsiElement();
    PsiDirectory dir = e.getContainingFile().getContainingDirectory();
    if (dir != null) {                  // target is function or class or file
      PsiLocation<PsiDirectory> l = new PsiLocation<PsiDirectory>(e.getProject(), dir);
      settings = createConfigurationFromFolder(l);
      configuration = checkSettings(settings, existingConfigurations);
      if (configuration != null)
        return configuration;
    }
    return null;
  }

  @Nullable
  private static RunConfiguration checkSettings(RunnerAndConfigurationSettings settings,
                                  RunnerAndConfigurationSettings[] existingConfigurations) {
    if (settings != null) {
      final AbstractPythonTestRunConfiguration configuration = (AbstractPythonTestRunConfiguration)settings.getConfiguration();
      for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
        if (configuration.compareSettings((AbstractPythonTestRunConfiguration)existingConfiguration.getConfiguration())) {
          return existingConfiguration.getConfiguration();
        }
      }
    }
    return null;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}