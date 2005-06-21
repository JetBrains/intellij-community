package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.junit.ModuleBasedConfiguration;
import com.intellij.execution.junit.RefactoringListeners;
import com.intellij.execution.junit2.configuration.RunConfigurationModule;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jdom.Element;

import java.util.Collection;

public class ApplicationConfiguration extends SingleClassConfiguration implements RunJavaConfiguration {

  public String MAIN_CLASS_NAME;
  public String VM_PARAMETERS;
  public String PROGRAM_PARAMETERS;
  public String WORKING_DIRECTORY;
  public boolean ALTERNATIVE_JRE_PATH_ENABLED;
  public String ALTERNATIVE_JRE_PATH;

  public ApplicationConfiguration(final String name, final Project project, ApplicationConfigurationType applicationConfigurationType) {
    super(name, new RunConfigurationModule(project, true), applicationConfigurationType.getConfigurationFactories()[0]);
  }

  public RunProfileState getState(final DataContext context,
                                  final RunnerInfo runnerInfo,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationSettings) {
    final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        final JavaParameters params = new JavaParameters();
        JavaParametersUtil.configureModule(getConfigurationModule(), params, JavaParameters.JDK_AND_CLASSES_AND_TESTS, ALTERNATIVE_JRE_PATH_ENABLED ? ALTERNATIVE_JRE_PATH : null);
        JavaParametersUtil.configureConfiguration(params, ApplicationConfiguration.this);
        params.setMainClass(MAIN_CLASS_NAME);
        return params;
      }
    };
    state.setConsoleBuilder(TextConsoleBuidlerFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new ApplicationConfigurable2(getProject());
  }

  public String getGeneratedName() {
    if (MAIN_CLASS_NAME == null) {
      return null;
    }
    return ExecutionUtil.getPresentableClassName(MAIN_CLASS_NAME, getConfigurationModule());
  }

  public void setGeneratedName() {
    setName(getGeneratedName());
  }

  public RefactoringElementListener getRefactoringElementListener(final PsiElement element) {
    return RefactoringListeners.
      getClassOrPackageListener(element, new RefactoringListeners.SingleClassConfigurationAccessor(this));
  }

  public PsiClass getMainClass() {
    return getConfigurationModule().findClass(MAIN_CLASS_NAME);
  }

  public boolean isGeneratedName() {
    if (MAIN_CLASS_NAME == null || MAIN_CLASS_NAME.length() == 0) {
      return ExecutionUtil.isNewName(getName());
    }
    return Comparing.equal(getName(), getGeneratedName());
  }

  public String suggestedName() {
    return ExecutionUtil.shortenName(ExecutionUtil.getShortClassName(MAIN_CLASS_NAME), 6) + ".main()";
  }

  public void setMainClassName(final String qualifiedName) {
    final boolean generatedName = isGeneratedName();
    MAIN_CLASS_NAME = qualifiedName;
    if (generatedName) setGeneratedName();
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    if (ALTERNATIVE_JRE_PATH_ENABLED){
      if (ALTERNATIVE_JRE_PATH == null ||
          ALTERNATIVE_JRE_PATH.length() == 0 ||
          !JavaSdkImpl.checkForJre(ALTERNATIVE_JRE_PATH)){
        throw new RuntimeConfigurationWarning("\'" + ALTERNATIVE_JRE_PATH + "\' is not valid JRE home");
      }
    }
    final RunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass psiClass = configurationModule.checkModuleAndClassName(MAIN_CLASS_NAME, "main class");
    if (ApplicationConfigurationType.findMainMethod(psiClass.findMethodsByName("main", true)) == null) {
      throw new RuntimeConfigurationWarning("Main method not found in class " + MAIN_CLASS_NAME);
    }
  }

  public void setProperty(final int property, final String value) {
    switch (property) {
      case PROGRAM_PARAMETERS_PROPERTY:
        PROGRAM_PARAMETERS = value;
        break;
      case VM_PARAMETERS_PROPERTY:
        VM_PARAMETERS = value;
        break;
      case WORKING_DIRECTORY_PROPERTY:
        WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
        break;
      default:
        throw new RuntimeException("Unknown property: " + property);
    }
  }

  public String getProperty(final int property) {
    switch (property) {
      case PROGRAM_PARAMETERS_PROPERTY:
        return PROGRAM_PARAMETERS;
      case VM_PARAMETERS_PROPERTY:
        return VM_PARAMETERS;
      case WORKING_DIRECTORY_PROPERTY:
        return getWorkingDirectory();
      default:
        throw new RuntimeException("Unknown property: " + property);
    }
  }

  private String getWorkingDirectory() {
    return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
  }

  public Collection<Module> getValidModules() {
    return RunConfigurationModule.getModulesForClass(getProject(), MAIN_CLASS_NAME);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new ApplicationConfiguration(getName(), getProject(), ApplicationConfigurationType.getInstance());
  }

  public void readExternal(final Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
    readModule(element);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeModule(element);
  }
}