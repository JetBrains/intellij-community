package com.jetbrains.python.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public abstract class AbstractPythonRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule>
  implements LocatableConfiguration, AbstractPythonRunConfigurationParams {
  private String myInterpreterOptions = "";
  private String myWorkingDirectory = "";
  private String mySdkHome = "";
  private boolean myPassParentEnvs = true;
  private Map<String, String> myEnvs = new HashMap<String, String>();
  private boolean myUseModuleSdk;

  public AbstractPythonRunConfiguration(final String name, final RunConfigurationModule module, final ConfigurationFactory factory) {
    super(name, module, factory);
  }

  public List<Module> getValidModules() {
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    List<Module> result = new ArrayList<Module>();
    for (Module module : modules) {
      if (PythonSdkType.findPythonSdk(module) != null) {
        result.add(module);
      }
    }
    return result;
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

    if (!myUseModuleSdk) {
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
    else {
      Sdk sdk = PythonSdkType.findPythonSdk(getModule());
      if (sdk == null) {
        throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_module_sdk"));
      }
    }
  }

  public String getSdkHome() {
    String sdkHome = mySdkHome;
    if (StringUtil.isEmptyOrSpaces(mySdkHome)) {
      final Sdk projectJdk = ProjectRootManager.getInstance(getProject()).getProjectJdk();
      if (projectJdk != null) {
        sdkHome = projectJdk.getHomePath();
      }
    }
    return sdkHome;
  }

  public String getInterpreterPath() {
    String sdkHome;
    if (myUseModuleSdk) {
      Sdk sdk = PythonSdkType.findPythonSdk(getModule());
      sdkHome = sdk.getHomePath();
    }
    else {
      sdkHome = getSdkHome();
    }
    return PythonSdkType.getInterpreterPath(sdkHome);
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myInterpreterOptions = JDOMExternalizerUtil.readField(element, "INTERPRETER_OPTIONS");
    final String parentEnvs = JDOMExternalizerUtil.readField(element, "PARENT_ENVS");
    if (parentEnvs != null) {
      myPassParentEnvs = Boolean.parseBoolean(parentEnvs);
    }
    mySdkHome = JDOMExternalizerUtil.readField(element, "SDK_HOME");
    myWorkingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY");
    myUseModuleSdk = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "IS_MODULE_SDK"));
    getConfigurationModule().readExternal(element);
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "INTERPRETER_OPTIONS", myInterpreterOptions);
    JDOMExternalizerUtil.writeField(element, "PARENT_ENVS", Boolean.toString(myPassParentEnvs));
    JDOMExternalizerUtil.writeField(element, "SDK_HOME", mySdkHome);
    JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", myWorkingDirectory);
    JDOMExternalizerUtil.writeField(element, "IS_MODULE_SDK", Boolean.toString(myUseModuleSdk));
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
    getConfigurationModule().writeExternal(element);
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

  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  public boolean isUseModuleSdk() {
    return myUseModuleSdk;
  }

  public void setUseModuleSdk(boolean useModuleSdk) {
    myUseModuleSdk = useModuleSdk;
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
    target.setModule(source.getModule());
    target.setUseModuleSdk(source.isUseModuleSdk());
  }
}
