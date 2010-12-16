/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
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

public class PythonUnitTestConfigurationProducer extends RuntimeConfigurationProducer {
  private PsiElement myPsiElement;

  public PythonUnitTestConfigurationProducer() {
    super(ConfigurationTypeUtil.findConfigurationType(PythonUnitTestConfigurationType.class));
  }

  @Override
  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
        if (! (TestRunnerService.getInstance(element.getProject()).getProjectConfiguration().equals(
          PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME))) return null;
    RunnerAndConfigurationSettings settings;
    /*Module module = location.getModule();

    if (module != null) {
      for (RunnableUnitTestFilter f : Extensions.getExtensions(RunnableUnitTestFilter.EP_NAME)) {
        if (f.isRunnableUnitTest(location.getPsiElement().getContainingFile(), module)) {
          return null;
        }
      }
    }*/

    if (PythonUnitTestRunnableScriptFilter.isIfNameMain(location)) {
      return null;
    }

    settings = createConfigurationFromFolder(location);
    if (settings != null) return settings;

    final PyElement pyElement = PsiTreeUtil.getParentOfType(location.getPsiElement(), PyElement.class);
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

  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromFunction(Location location, PyElement element) {
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    if (pyFunction == null || !PythonUnitTestUtil.isTestCaseFunction(pyFunction)) return null;
    final PyClass containingClass = pyFunction.getContainingClass();
    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from function");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();
    configuration.setMethodName(pyFunction.getName());
    if (containingClass != null) {
      configuration.setClassName(containingClass.getName());
      configuration.setTestType(PythonUnitTestRunConfiguration.TestType.TEST_METHOD);
    }
    else {
      configuration.setTestType(PythonUnitTestRunConfiguration.TestType.TEST_FUNCTION);
    }
    if (!setupConfigurationScript(configuration, pyFunction)) return null;
    configuration.setName(configuration.suggestedName());
    myPsiElement = pyFunction;
    return settings;
  }

  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromClass(Location location, PyElement element) {
    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (pyClass == null || !PythonUnitTestUtil.isTestCaseClass(pyClass)) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(
      PythonUnitTestRunConfiguration.TestType.TEST_CLASS);
    configuration.setClassName(pyClass.getName());
    if (!setupConfigurationScript(configuration, pyClass)) return null;
    configuration.setName(configuration.suggestedName());

    myPsiElement = pyClass;
    return settings;
  }

  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromFolder(Location location) {
    final PsiElement element = location.getPsiElement();

    if (!(element instanceof PsiDirectory)) return null;

    final Module module = location.getModule();
    if (!isPythonModule(module)) return null;

    PsiDirectory dir = (PsiDirectory)element;
    final VirtualFile file = dir.getVirtualFile();
    final String path = file.getPath();

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(PythonUnitTestRunConfiguration.TestType.TEST_FOLDER);
    configuration.setFolderName(path);
    configuration.setWorkingDirectory(path);

    configuration.setName(configuration.suggestedName());
    myPsiElement = dir;
    return settings;
  }


  private static boolean isPythonModule(Module module) {
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
  private RunnerAndConfigurationSettings createConfigurationFromFile(Location location, PsiElement element) {
    PsiElement file = element.getContainingFile();
    if (file == null || !(file instanceof PyFile)) return null;

    final PyFile pyFile = (PyFile)file;
    final List<PyStatement> testCases = PythonUnitTestUtil.getTestCaseClassesFromFile(pyFile);
    if (testCases.isEmpty()) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from file");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(PythonUnitTestRunConfiguration.TestType.TEST_SCRIPT);
    if (!setupConfigurationScript(configuration, pyFile)) return null;

    configuration.setName(configuration.suggestedName());
    myPsiElement = pyFile;
    return settings;
  }

  private RunnerAndConfigurationSettings makeConfigurationSettings(Location location, String name) {
    final RunnerAndConfigurationSettings result =
      RunManager.getInstance(location.getProject()).createRunConfiguration(name, getConfigurationFactory());
    PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)result.getConfiguration();
    configuration.setUseModuleSdk(true);
    configuration.setModule(ModuleUtil.findModuleForPsiElement(location.getPsiElement()));
    return result;
  }

  private static boolean setupConfigurationScript(PythonUnitTestRunConfiguration cfg, PyElement element) {
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
      final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();
      for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
        if (configuration.compareSettings((PythonUnitTestRunConfiguration)existingConfiguration.getConfiguration())) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}