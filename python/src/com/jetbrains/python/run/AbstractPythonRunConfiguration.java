package com.jetbrains.python.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.options.SettingsEditor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractPythonRunConfiguration extends RuntimeConfiguration
  implements LocatableConfiguration, AbstractPythonRunConfigurationParams {
  private String myInterpreterOptions = "";
  private String myWorkingDirectory = "";
  private String mySdkHome = "";
  private boolean myPassParentEnvs;
  private Map<String, String> myEnvs = new HashMap<String, String>();

  public AbstractPythonRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, project, factory);
  }

  @Nullable
  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider configurationInfoProvider) {
    return null;
  }

  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(final ProgramRunner runner) {
    return null;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(getSdkHome())) {
      final Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
      if (projectSdk == null || !(projectSdk.getSdkType() instanceof PythonSdkType)) {
        throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_sdk"));
      }
    }
    else if (!PythonSdkType.getInstance().isValidSdkHome(getSdkHome())) {
      throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_valid_sdk"));
    }
  }

  public String getSdkHome() {
    String sdkHome = mySdkHome;
    if (StringUtil.isEmptyOrSpaces(mySdkHome)) {
      final Sdk projectJdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
      assert projectJdk != null;
      sdkHome = projectJdk.getHomePath();
    }
    return sdkHome;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myInterpreterOptions = JDOMExternalizerUtil.readField(element, "INTERPRETER_OPTIONS");
    myPassParentEnvs = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "PASS_PARENT_ENVS"));
    mySdkHome = JDOMExternalizerUtil.readField(element, "SDK_HOME");
    myWorkingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY");
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "INTERPRETER_OPTIONS", myInterpreterOptions);
    JDOMExternalizerUtil.writeField(element, "PASS_PARENT_ENVS", Boolean.toString(myPassParentEnvs));
    JDOMExternalizerUtil.writeField(element, "SDK_HOME", mySdkHome);
    JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", myWorkingDirectory);
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
  }

  public Map<String, String> getEnvs() {
    return myEnvs;
  }

  public void setEnvs(final Map<String, String> envs) {
    myEnvs = envs;
  }

  public String getInterpreterOptions() {
    return myInterpreterOptions;
  }

  public void setInterpreterOptions(String interpreterOptions) {
    myInterpreterOptions = interpreterOptions;
  }

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public void setSdkHome(String sdkHome) {
    mySdkHome = sdkHome;
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    myPassParentEnvs = passParentEnvs;
  }

  public static void copyParams(AbstractPythonRunConfigurationParams source, AbstractPythonRunConfigurationParams target) {
    target.setEnvs(new HashMap<String, String>(source.getEnvs()));
    target.setInterpreterOptions(source.getInterpreterOptions());
    target.setPassParentEnvs(source.isPassParentEnvs());
    target.setSdkHome(source.getSdkHome());
    target.setWorkingDirectory(source.getWorkingDirectory());
  }
}
