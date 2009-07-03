package com.jetbrains.python.testing.pytest;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class PyTestRunConfigurationType implements LocatableConfigurationType {
  private ConfigurationFactory myPyTestConfigurationFactory = new PyTestRunConfigurationFactory(this);

  public String getDisplayName() {
    return "py.test";
  }

  public String getConfigurationTypeDescription() {
    return "py.test";
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @NotNull
  public String getId() {
    return "py.test";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] { myPyTestConfigurationFactory };
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    PsiElement element = location.getPsiElement();
    PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory) element : element.getContainingFile();
    if (file == null) return null;
    String path = file.getVirtualFile().getPath();

    final RunnerAndConfigurationSettings result =
      RunManager.getInstance(location.getProject()).createRunConfiguration(file.getName(), myPyTestConfigurationFactory);
    PyTestRunConfiguration configuration = (PyTestRunConfiguration)result.getConfiguration();
    configuration.setUseModuleSdk(true);
    configuration.setModule(ModuleUtil.findModuleForPsiElement(element));

    configuration.setTestToRun(path);

    PyFunction pyFunction = findTestFunction(location);
    if (pyFunction != null) {
      String name = pyFunction.getName();
      configuration.setKeywords(name);
      configuration.setName(name + " in " + configuration.getName());
    }
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

  public boolean isConfigurationByLocation(RunConfiguration configuration, Location location) {
    if (!(configuration instanceof PyTestRunConfiguration)) return false;
    PyTestRunConfiguration pyTestRunConfiguration = (PyTestRunConfiguration)configuration;
    final PsiElement element = location.getPsiElement();
    PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory) element : element.getContainingFile();
    if (file == null || !pyTestRunConfiguration.getTestToRun().equals(file.getVirtualFile().getPath())) {
      return false;
    }
    PyFunction testFunction = findTestFunction(location);
    String keyword = testFunction != null ? testFunction.getName() : null;
    return Comparing.equal(pyTestRunConfiguration.getKeywords(), keyword);    
  }

  private static class PyTestRunConfigurationFactory extends ConfigurationFactory {
    protected PyTestRunConfigurationFactory(ConfigurationType type) {
      super(type);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PyTestRunConfiguration("", new RunConfigurationModule(project), this);
    }
  }
}
