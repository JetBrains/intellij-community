package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PythonRunConfiguration extends RunConfigurationBase implements LocatableConfiguration {
  public String SCRIPT_NAME;
  public String PARAMETERS;
  public String WORKING_DIRECTORY;
  public String SDK_HOME;
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

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    CommandLineState state = new CommandLineState(env) {
      protected OSProcessHandler startProcess() throws ExecutionException {
        GeneralCommandLine commandLine = new GeneralCommandLine();

        String sdkHome = SDK_HOME;
        if (sdkHome == null) {
          final Sdk projectJdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
          assert projectJdk != null;
          sdkHome = projectJdk.getHomePath();
        }

        commandLine.setExePath(PythonSdkType.getInterpreterPath(sdkHome));

        commandLine.addParameter(SCRIPT_NAME);
        commandLine.getParametersList().addParametersString(PARAMETERS);
        if (WORKING_DIRECTORY != null && WORKING_DIRECTORY.length() > 0) {
          commandLine.setWorkDirectory(WORKING_DIRECTORY);
        }
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
    if (SDK_HOME == null) {
      final Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
      if (projectSdk == null || !(projectSdk.getSdkType() instanceof PythonSdkType)) {
        throw new RuntimeConfigurationException("Please, specify Python SDK");
      }
    }
    else if (!PythonSdkType.getInstance().isValidSdkHome(SDK_HOME)) {
      throw new RuntimeConfigurationException("Please select a valid Python interpeter");
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

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), suggestedName());
  }

  public String suggestedName() {
    String name = new File(SCRIPT_NAME).getName();
    if (name.endsWith(".py")) {
      return name.substring(0, name.length()-3);
    }
    return name;
  }
}
