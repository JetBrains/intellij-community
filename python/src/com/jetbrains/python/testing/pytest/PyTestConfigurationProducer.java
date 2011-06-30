/*
 * User: anna
 * Date: 13-May-2010
 */
package com.jetbrains.python.testing.pytest;

import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.testing.TestRunnerService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class PyTestConfigurationProducer extends RuntimeConfigurationProducer {
  private PsiElement myPsiElement;

  public PyTestConfigurationProducer() {
    super(ConfigurationTypeUtil.findConfigurationType(PyTestRunConfigurationType.class));
  }

  @Override
  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    if (! (TestRunnerService.getInstance(element.getProject()).getProjectConfiguration().equals(
           PythonTestConfigurationsModel.PY_TEST_NAME))) return null;

    PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory)element : element.getContainingFile();
    if (file == null) return null;
    myPsiElement = file;
    String path = file.getVirtualFile().getPath();

    if (file instanceof PyFile || file instanceof PsiDirectory) {
      final List<PyStatement> testCases = PyTestUtil.getPyTestCasesFromFile(file);
      if (testCases.isEmpty()) return null;
    } else return null;
    
    final RunnerAndConfigurationSettings result =
      RunManager.getInstance(location.getProject()).createRunConfiguration(file.getName(), getConfigurationFactory());
    PyTestRunConfiguration configuration = (PyTestRunConfiguration)result.getConfiguration();
    configuration.setUseModuleSdk(true);
    configuration.setModule(ModuleUtil.findModuleForPsiElement(myPsiElement));

    final String scriptPath = configuration.getRunnerScriptPath();
    if (scriptPath == null || !new File(scriptPath).exists()) {
      return null;
    }

    configuration.setTestToRun(path);

    PyFunction pyFunction = findTestFunction(location);
    if (pyFunction != null) {
      String name = pyFunction.getName();
      configuration.setKeywords(name);
      configuration.setName(name + " in " + configuration.getName());
      myPsiElement = pyFunction;
    }
    configuration.setName(configuration.suggestedName());
    return result;
  }

  @Nullable
  private static PyFunction findTestFunction(Location location) {
    PyFunction function = PsiTreeUtil.getParentOfType(location.getPsiElement(), PyFunction.class);
    if (function != null) {
      String name = function.getName();
      if (name != null && name.startsWith("test")) {
        return function;
      }
    }
    return null;
  }

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
      final RunConfiguration configuration = existingConfiguration.getConfiguration();
      PyTestRunConfiguration pyTestRunConfiguration = (PyTestRunConfiguration)configuration;
      final PsiElement element = location.getPsiElement();
      PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory)element : element.getContainingFile();
      if (file == null || !pyTestRunConfiguration.getTestToRun().equals(file.getVirtualFile().getPath())) {
        continue;
      }
      PyFunction testFunction = findTestFunction(location);
      String keyword = testFunction != null ? testFunction.getName() : null;
      if (Comparing.equal(pyTestRunConfiguration.getKeywords(), keyword)) {
        return existingConfiguration;
      }
    }
    return null;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}