package com.jetbrains.python.run;

import com.google.common.collect.Maps;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkFlavor;
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
  implements LocatableConfiguration, AbstractPythonRunConfigurationParams, CommandLinePatcher {
  private String myInterpreterOptions = "";
  private String myWorkingDirectory = "";
  private String mySdkHome = "";
  private boolean myPassParentEnvs = true;
  private Map<String, String> myEnvs = Maps.newHashMap();
  private boolean myUseModuleSdk;

  public AbstractPythonRunConfiguration(final String name, final RunConfigurationModule module, final ConfigurationFactory factory) {
    super(name, module, factory);
    module.init();
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

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (PlatformUtils.isPyCharm()) {
      final String path = getInterpreterPath();
      if (path == null) {
        throw new RuntimeConfigurationError("Please select a valid Python interpreter");
      }
    }
    else {
      if (!myUseModuleSdk) {
        if (StringUtil.isEmptyOrSpaces(getSdkHome())) {
          final Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
          if (projectSdk == null || !(projectSdk.getSdkType() instanceof PythonSdkType)) {
            throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_sdk"));
          }
        }
        else if (!PythonSdkType.getInstance().isValidSdkHome(getSdkHome())) {
          throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_valid_sdk"));
        }
      }
      else {
        Sdk sdk = PythonSdkType.findPythonSdk(getModule());
        if (sdk == null) {
          throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_module_sdk"));
        }
      }
    }
  }

  public String getSdkHome() {
    String sdkHome = mySdkHome;
    if (StringUtil.isEmptyOrSpaces(mySdkHome)) {
      final Sdk projectJdk = PythonSdkType.findPythonSdk(getModule());
      if (projectJdk != null) {
        sdkHome = projectJdk.getHomePath();
      }
    }
    return sdkHome;
  }

  @Nullable
  public String getInterpreterPath() {
    String sdkHome;
    if (myUseModuleSdk) {
      Sdk sdk = PythonSdkType.findPythonSdk(getModule());
      if (sdk == null) return null;
      sdkHome = sdk.getHomePath();
    }
    else {
      sdkHome = getSdkHome();
    }
    return sdkHome;
  }

  @Nullable
  public PythonSdkFlavor getSdkFlavor() {
    final String path = getInterpreterPath();
    return path == null ? null : PythonSdkFlavor.getFlavor(path);
  } 

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myInterpreterOptions = JDOMExternalizerUtil.readField(element, "INTERPRETER_OPTIONS");
    readEnvs(element);
    mySdkHome = JDOMExternalizerUtil.readField(element, "SDK_HOME");
    myWorkingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY");
    myUseModuleSdk = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "IS_MODULE_SDK"));
    getConfigurationModule().readExternal(element);
  }

  protected void readEnvs(Element element) {
    final String parentEnvs = JDOMExternalizerUtil.readField(element, "PARENT_ENVS");
    if (parentEnvs != null) {
      myPassParentEnvs = Boolean.parseBoolean(parentEnvs);
    }
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "INTERPRETER_OPTIONS", myInterpreterOptions);
    writeEnvs(element);
    JDOMExternalizerUtil.writeField(element, "SDK_HOME", mySdkHome);
    JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", myWorkingDirectory);
    JDOMExternalizerUtil.writeField(element, "IS_MODULE_SDK", Boolean.toString(myUseModuleSdk));
    getConfigurationModule().writeExternal(element);
  }

  protected void writeEnvs(Element element) {
    JDOMExternalizerUtil.writeField(element, "PARENT_ENVS", Boolean.toString(myPassParentEnvs));
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

  /**
   * Some setups (e.g. virtualenv) provide a script that alters environment variables before running a python interpreter or other tools.
   * Such settings are not directly stored but applied right before running using this method.
   * @param commandLine what to patch
   */
  public void patchCommandLine(GeneralCommandLine commandLine) {
    final String sdk_home = getSdkHome();
    Sdk sdk = PythonSdkType.findPythonSdk(getModule());
    if (sdk != null && sdk_home != null) {
      SdkType sdk_type = sdk.getSdkType();
      patchCommandLineFirst(commandLine, sdk_home, sdk_type);
      patchCommandLineForVirtualenv(commandLine, sdk_home, sdk_type);
      patchCommandLineForBuildout(commandLine, sdk_home, sdk_type);
      patchCommandLineLast(commandLine, sdk_home, sdk_type);
    }
  }

  /**
   * Patches command line before virtualenv and buildout patchers.
   * Default implementation does nothing.
   * @param commandLine
   * @param sdk_home
   * @param sdk_type
   */
  protected void patchCommandLineFirst(GeneralCommandLine commandLine, String sdk_home, SdkType sdk_type) {
    // override
  }

  /**
   * Patches command line after virtualenv and buildout patchers.
   * Default implementation does nothing.
   * @param commandLine
   * @param sdk_home
   * @param sdk_type
   */
  protected void patchCommandLineLast(GeneralCommandLine commandLine, String sdk_home, SdkType sdk_type) {
    // override
  }

  /**
   * Gets called after {@link #patchCommandLineForVirtualenv(com.intellij.execution.configurations.GeneralCommandLine, String, com.intellij.openapi.projectRoots.SdkType)}
   * Does nothing here, real implementations should use alter running script name or use engulfer.
   * @param commandLine
   * @param sdk_home
   * @param sdk_type
   */
  protected void patchCommandLineForBuildout(GeneralCommandLine commandLine, String sdk_home, SdkType sdk_type) {
  }

  /**
   * Alters PATH so that a virtualenv is activated, if present.
   * @param commandLine
   * @param sdk_home
   * @param sdk_type
   */
  protected void patchCommandLineForVirtualenv(GeneralCommandLine commandLine, String sdk_home, SdkType sdk_type) {
    PythonSdkType.patchCommandLineForVirtualenv(commandLine, sdk_home, isPassParentEnvs());
  }

  protected void setUnbufferedEnv() {
    Map<String, String> envs = getEnvs();
    // unbuffered I/O is easier for IDE to handle
    PythonEnvUtil.setPythonUnbuffered(envs);
  }

  @Override
  public boolean excludeCompileBeforeLaunchOption() {
    final Module module = getModule();
    return module != null ? module.getModuleType() instanceof PythonModuleTypeBase : true;
  }
}
