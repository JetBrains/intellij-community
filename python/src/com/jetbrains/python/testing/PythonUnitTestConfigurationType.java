package com.jetbrains.python.testing;

import com.intellij.execution.LocatableConfigurationType;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import static com.jetbrains.python.testing.PythonUnitTestRunConfiguration.TestType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestConfigurationType implements LocatableConfigurationType {
  private final PythonUnitTestConfigurationFactory myConfigurationFactory = new PythonUnitTestConfigurationFactory(this);

  public static PythonUnitTestConfigurationType getInstance() {
    for (ConfigurationType configType : Extensions.getExtensions(CONFIGURATION_TYPE_EP)) {
      if (configType instanceof PythonUnitTestConfigurationType) {
        return (PythonUnitTestConfigurationType)configType;
      }
    }
    assert false;
    return null;
  }

  private final static Icon ICON = IconLoader.getIcon("/com/jetbrains/python/python.png");

  private RunnerAndConfigurationSettings makeConfigurationSettings(Location location, String name) {
    final RunnerAndConfigurationSettings result =
      RunManager.getInstance(location.getProject()).createRunConfiguration(name, myConfigurationFactory);
    PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration) result.getConfiguration();
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
  
  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromFunction(Location location, PyElement element) {
    PyFunction pyFunction = PyUtil.getElementOrContaining(element, PyFunction.class);
    if (pyFunction == null || !PythonUnitTestUtil.isTestCaseFunction(pyFunction)) return null;

    final PyClass containingClass = pyFunction.getContainingClass();
    if (containingClass == null) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from function");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(TestType.TEST_METHOD);
    configuration.setMethodName(pyFunction.getName());
    configuration.setClassName(containingClass.getName());
    if (!setupConfigurationScript(configuration, pyFunction)) return null;

    configuration.setName(configuration.suggestedName());

    return settings;
  }

  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromClass(Location location, PyElement element) {
    PyClass pyClass = PyUtil.getElementOrParent(element, PyClass.class);
    if (pyClass == null || !PythonUnitTestUtil.isTestCaseClass(pyClass))  return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(TestType.TEST_CLASS);
    configuration.setClassName(pyClass.getName());
    if (!setupConfigurationScript(configuration, pyClass)) return null;

    configuration.setName(configuration.suggestedName());

    return settings;
  }

  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromFolder(Location location) {
    final PsiElement element = location.getPsiElement();

    if (!(element instanceof PsiDirectory)) return null;

    PsiDirectory dir = (PsiDirectory)element;
    final VirtualFile file = dir.getVirtualFile();
    final String path = file.getPath();

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from class");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(TestType.TEST_FOLDER);
    configuration.setFolderName(path);
    configuration.setWorkingDirectory(path);

    configuration.setName(configuration.suggestedName());

    return settings;
  }

  @Nullable
  private RunnerAndConfigurationSettings createConfigurationFromFile(Location location, PyElement element) {
    PsiElement file = element.getContainingFile();
    if (file == null || !(file instanceof PyFile)) return null;

    final PyFile pyFile = (PyFile)file;
    final List<PyClass> testCases = PythonUnitTestUtil.getTestCaseClassesFromFile(pyFile);
    if (testCases.isEmpty()) return null;

    final RunnerAndConfigurationSettings settings = makeConfigurationSettings(location, "tests from file");
    final PythonUnitTestRunConfiguration configuration = (PythonUnitTestRunConfiguration)settings.getConfiguration();

    configuration.setTestType(TestType.TEST_SCRIPT);
    if (!setupConfigurationScript(configuration, pyFile)) return null;
    
    configuration.setName(configuration.suggestedName());

    return settings;
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    RunnerAndConfigurationSettings settings;

    settings = createConfigurationFromFolder(location);
    if (settings != null) return settings;

    final PyElement pyElement = PyUtil.getCoveringPyElement(location.getPsiElement());
    if (pyElement == null) return null;

    settings = createConfigurationFromFunction(location, pyElement);
    if (settings != null) return settings;

    settings = createConfigurationFromClass(location, pyElement);
    if (settings != null) return settings;

    settings = createConfigurationFromFile(location, pyElement);
    if (settings != null) return settings;

    return null;
  }

  public boolean isConfigurationByLocation(RunConfiguration configuration, Location location) {
    final RunnerAndConfigurationSettings settings = createConfigurationByLocation(location);
    if (settings == null) return false;

    return ((PythonUnitTestRunConfiguration)configuration).compareSettings((PythonUnitTestRunConfiguration)settings.getConfiguration());
  }

  private static class PythonUnitTestConfigurationFactory extends ConfigurationFactory {
    protected PythonUnitTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonUnitTestRunConfiguration(new RunConfigurationModule(project), this, "");
    }
  }

  public String getDisplayName() {
    return PyBundle.message("runcfg.unittest.display_name");
  }

  public String getConfigurationTypeDescription() {
    return PyBundle.message("runcfg.unittest.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  @NotNull
  @NonNls
  public String getId() {
    return "PythonUnitTestConfigurationType";
  }
}
