package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PythonRunConfiguration extends RunConfigurationBase {
  public String SCRIPT_NAME;
  public String PARAMETERS;
  public String WORKING_DIRECTORY;
  public boolean PASS_PARENT_ENVS;
  private Map<String, String> myEnvs = new HashMap<String, String>();

  protected PythonRunConfiguration(Project project, ConfigurationFactory configurationFactory, String name) {
    super(project, configurationFactory, name);
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonRunConfigurationEditor();
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider configurationInfoProvider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(final ProgramRunner runner) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public RunProfileState getState(DataContext dataContext,
                                  Executor executor,
                                  RunnerSettings runnerSettings,
                                  ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws ExecutionException {
    CommandLineState state = new CommandLineState(runnerSettings, configurationPerRunnerSettings) {
      protected OSProcessHandler startProcess() throws ExecutionException {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        final Sdk projectJdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
        assert projectJdk != null;

        commandLine.setExePath(PythonSdkType.getInterpreterPath(projectJdk.getHomePath()));

        commandLine.addParameter(SCRIPT_NAME);
        commandLine.getParametersList().addParametersString(PARAMETERS);
        commandLine.setWorkDirectory(WORKING_DIRECTORY);
        commandLine.setEnvParams(getEnvs());
        commandLine.setPassParentEnvs(PASS_PARENT_ENVS);

        return new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
      }
    };
    TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(getProject());
    consoleBuilder.addFilter(new PythonTracebackFilter(getProject()));
    state.setConsoleBuilder(consoleBuilder);
    return state;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    final Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
    if (projectSdk == null || !(projectSdk.getSdkType() instanceof PythonSdkType)) {
      throw new RuntimeConfigurationException("Please, specify Python SDK");
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
  }

  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  public void setEnvs(final Map<String, String> envs) {
    myEnvs = envs;
  }
}
