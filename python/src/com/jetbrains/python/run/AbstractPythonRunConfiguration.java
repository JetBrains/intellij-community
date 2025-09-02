// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.AbstractRunConfiguration;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.testframework.sm.runner.GeneralIdBasedToSMTRunnerEventsConvertor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.jetbrains.python.run.PythonScriptCommandLineState.getExpandedWorkingDir;

/**
 * @author Leonid Shalupov
 */
public abstract class AbstractPythonRunConfiguration<T extends AbstractPythonRunConfiguration<T>> extends AbstractRunConfiguration
  implements AbstractPythonRunConfigurationParams, CommandLinePatcher, RunProfileWithCompileBeforeLaunchOption {
  private String myInterpreterOptions = "";
  private String myWorkingDirectory = "";
  private String mySdkHome = "";
  private Sdk mySdk = null;
  private boolean myUseModuleSdk;
  private boolean myAddContentRoots = true;
  private boolean myAddSourceRoots = true;

  protected PathMappingSettings myMappingSettings;
  /**
   * To prevent "double module saving" child may enable this flag
   * and no module info would be saved
   */
  protected boolean mySkipModuleSerialization;

  public AbstractPythonRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(project, factory);
    getConfigurationModule().setModuleToAnyFirstIfNotSpecified();
  }

  @Override
  public List<Module> getValidModules() {
    return getValidModules(getProject());
  }

  @Override
  public PathMappingSettings getMappingSettings() {
    return myMappingSettings;
  }

  @Override
  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    myMappingSettings = mappingSettings;
  }

  /**
   * @return if config uses {@link GeneralIdBasedToSMTRunnerEventsConvertor} or not
   */
  public boolean isIdTestBased() {
    return false;
  }

  public static List<Module> getValidModules(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    List<Module> result = new ArrayList<>();
    for (Module module : modules) {
      if (PythonSdkUtil.findPythonSdk(module) != null) {
        result.add(module);
      }
    }
    return result;
  }

  public PyCommonOptionsFormData getCommonOptionsFormData() {
    return new PyCommonOptionsFormData() {
      @Override
      public Project getProject() {
        return AbstractPythonRunConfiguration.this.getProject();
      }

      @Override
      public List<Module> getValidModules() {
        return AbstractPythonRunConfiguration.this.getValidModules();
      }

      @Override
      public boolean showConfigureInterpretersLink() {
        return false;
      }
    };
  }

  @Override
  public final @NotNull SettingsEditor<T> getConfigurationEditor() {
    if (Registry.is("python.new.run.config", false) && isNewUiSupported()) {
      // TODO: actually, we should return result of `PythonExtendedConfigurationEditor.create()` call, but it produces side effects
      // investigation needed PY-17716
      return createConfigurationEditor();
    }
    final SettingsEditor<T> runConfigurationEditor = PythonExtendedConfigurationEditor.create(createConfigurationEditor());

    final SettingsEditorGroup<T> group = new SettingsEditorGroup<>();

    // run configuration settings tab:
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), runConfigurationEditor);

    // tabs provided by extensions:
    PythonRunConfigurationExtensionsManager.Companion.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());

    return group;
  }

  protected boolean isNewUiSupported() {
    return false;
  }

  protected abstract SettingsEditor<T> createConfigurationEditor();

  /**
   * <strong>Always call super</strong> when overwriting this method
   */
  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    checkSdk();

    checkExtensions();
  }

  private void checkExtensions() throws RuntimeConfigurationException {
    try {
      PythonRunConfigurationExtensionsManager.Companion.getInstance().validateConfiguration(this, false);
    }
    catch (RuntimeConfigurationException e) {
      throw e;
    }
    catch (Exception ee) {
      throw new RuntimeConfigurationException(ee.getMessage());
    }
  }

  private void checkSdk() throws RuntimeConfigurationError {
    if (PlatformUtils.isPyCharm()) {
      final String path = getInterpreterPath();
      if (StringUtil.isEmptyOrSpaces(path)) {
        throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_valid_sdk"));
      }
    }
    else {
      if (!myUseModuleSdk) {
        if (StringUtil.isEmptyOrSpaces(getSdkHome())) {
          final Sdk projectSdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
          if (projectSdk == null || !PythonSdkUtil.isPythonSdk(projectSdk)) {
            throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_sdk"));
          }
        }
        else if (mySdk == null || !PySdkExtKt.getSdkSeemsValid(mySdk)) {
          throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_valid_sdk"));
        }
      }
      else {
        Sdk sdk = PythonSdkUtil.findPythonSdk(getModule());
        if (sdk == null) {
          throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_module_sdk"));
        }
      }
    }
  }

  @Override
  public String getSdkHome() {
    if (mySdk != null) {
      return mySdk.getHomePath();
    }
    String sdkHome = mySdkHome;
    if (StringUtil.isEmptyOrSpaces(mySdkHome)) {
      final Sdk projectJdk = PythonSdkUtil.findPythonSdk(getModule());
      if (projectJdk != null) {
        sdkHome = projectJdk.getHomePath();
      }
    }
    return sdkHome;
  }

  public @Nullable String getInterpreterPath() {
    String sdkHome;
    if (myUseModuleSdk) {
      Sdk sdk = PythonSdkUtil.findPythonSdk(getModule());
      if (sdk == null) return null;
      sdkHome = sdk.getHomePath();
    }
    else if (mySdk != null) {
      sdkHome = mySdk.getHomePath();
    }
    else {
      sdkHome = getSdkHome();
    }
    return sdkHome;
  }

  @Override
  @Transient
  public @Nullable Sdk getSdk() {
    if (myUseModuleSdk) {
      return PythonSdkUtil.findPythonSdk(getModule());
    }
    else if (mySdk != null) {
      return mySdk;
    }
    else {
      return PythonSdkUtil.findSdkByPath(getSdkHome());
    }
  }

  private @NotNull List<String> myEnvFiles = Collections.emptyList();

  @Override
  public @NotNull List<String> getEnvFilePaths() {
    return myEnvFiles;
  }

  @Override
  public void setEnvFilePaths(@NotNull List<String> envFiles) {
    myEnvFiles = envFiles;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    myInterpreterOptions = JDOMExternalizerUtil.readField(element, "INTERPRETER_OPTIONS");
    readEnvs(element);
    mySdkHome = JDOMExternalizerUtil.readField(element, "SDK_HOME");

    final String sdkName = JDOMExternalizerUtil.readField(element, "SDK_NAME");
    if (sdkName != null) {
      mySdk = PythonSdkUtil.findSdkByKey(sdkName);
    }

    var output = JDOMExternalizerUtil.readField(element, "ENV_FILES");
    myEnvFiles = output != null ? StringUtil.split(output, File.pathSeparator) : Collections.emptyList();

    myWorkingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY");
    myUseModuleSdk = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "IS_MODULE_SDK"));
    final String addContentRoots = JDOMExternalizerUtil.readField(element, "ADD_CONTENT_ROOTS");
    myAddContentRoots = addContentRoots == null || Boolean.parseBoolean(addContentRoots);
    final String addSourceRoots = JDOMExternalizerUtil.readField(element, "ADD_SOURCE_ROOTS");
    myAddSourceRoots = addSourceRoots == null || Boolean.parseBoolean(addSourceRoots);
    if (!mySkipModuleSerialization) {
      getConfigurationModule().readExternal(element);
    }

    setMappingSettings(PathMappingSettings.readExternal(element));
    // extension settings:
    PythonRunConfigurationExtensionsManager.Companion.getInstance().readExternal(this, element);
  }

  protected void readEnvs(Element element) {
    final String parentEnvs = JDOMExternalizerUtil.readField(element, "PARENT_ENVS");
    if (parentEnvs != null) {
      setPassParentEnvs(Boolean.parseBoolean(parentEnvs));
    }
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "ENV_FILES", String.join(File.pathSeparator, myEnvFiles));
    JDOMExternalizerUtil.writeField(element, "INTERPRETER_OPTIONS", myInterpreterOptions);
    writeEnvs(element);
    JDOMExternalizerUtil.writeField(element, "SDK_HOME", mySdkHome);
    if (mySdk != null) {
      JDOMExternalizerUtil.writeField(element, "SDK_NAME", mySdk.getName());
    }
    JDOMExternalizerUtil.writeField(element, "WORKING_DIRECTORY", myWorkingDirectory);
    JDOMExternalizerUtil.writeField(element, "IS_MODULE_SDK", Boolean.toString(myUseModuleSdk));
    JDOMExternalizerUtil.writeField(element, "ADD_CONTENT_ROOTS", Boolean.toString(myAddContentRoots));
    JDOMExternalizerUtil.writeField(element, "ADD_SOURCE_ROOTS", Boolean.toString(myAddSourceRoots));
    if (!mySkipModuleSerialization) {
      getConfigurationModule().writeExternal(element);
    }

    // extension settings:
    PythonRunConfigurationExtensionsManager.Companion.getInstance().writeExternal(this, element);

    PathMappingSettings.writeExternal(element, getMappingSettings());
  }

  protected void writeEnvs(Element element) {
    JDOMExternalizerUtil.writeField(element, "PARENT_ENVS", Boolean.toString(isPassParentEnvs()));
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
  }

  @Override
  public String getInterpreterOptions() {
    return myInterpreterOptions;
  }

  @Override
  public void setInterpreterOptions(String interpreterOptions) {
    myInterpreterOptions = interpreterOptions;
  }

  @Override
  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @Override
  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  @Override
  public void setSdkHome(String sdkHome) {
    mySdkHome = sdkHome;
  }

  @Override
  @Transient
  public void setSdk(@Nullable Sdk sdk) {
    mySdk = sdk;
  }

  @Override
  @Transient
  public @Nullable Module getModule() {
    return getConfigurationModule().getModule();
  }

  @Override
  public boolean isUseModuleSdk() {
    return myUseModuleSdk;
  }

  @Override
  public void setUseModuleSdk(boolean useModuleSdk) {
    myUseModuleSdk = useModuleSdk;
  }

  @Override
  public boolean shouldAddContentRoots() {
    return myAddContentRoots;
  }

  @Override
  public boolean shouldAddSourceRoots() {
    return myAddSourceRoots;
  }

  @Override
  public void setAddSourceRoots(boolean flag) {
    myAddSourceRoots = flag;
  }

  @Override
  public void setAddContentRoots(boolean flag) {
    myAddContentRoots = flag;
  }

  public static void copyParams(AbstractPythonRunConfigurationParams source, AbstractPythonRunConfigurationParams target) {
    target.setEnvFilePaths(source.getEnvFilePaths());
    target.setEnvs(new LinkedHashMap<>(source.getEnvs()));
    target.setInterpreterOptions(source.getInterpreterOptions());
    target.setPassParentEnvs(source.isPassParentEnvs());
    target.setSdkHome(source.getSdkHome());
    target.setSdk(source.getSdk());
    target.setWorkingDirectory(source.getWorkingDirectory());
    target.setModule(source.getModule());
    target.setUseModuleSdk(source.isUseModuleSdk());
    target.setMappingSettings(source.getMappingSettings());
    target.setAddContentRoots(source.shouldAddContentRoots());
    target.setAddSourceRoots(source.shouldAddSourceRoots());
  }

  /**
   * Some setups (e.g. virtualenv) provide a script that alters environment variables before running a python interpreter or other tools.
   * Such settings are not directly stored but applied right before running using this method.
   *
   * @param commandLine what to patch
   */
  @Override
  public void patchCommandLine(GeneralCommandLine commandLine) {
    final String interpreterPath = getInterpreterPath();
    Sdk sdk = getSdk();
    if (sdk != null && interpreterPath != null) {
      patchCommandLineFirst(commandLine, interpreterPath);
      patchCommandLineForVirtualenv(commandLine, sdk);
      patchCommandLineLast(commandLine, interpreterPath);
    }
  }

  /**
   * Patches command line before virtualenv patchers.
   * Default implementation does nothing.
   */
  protected void patchCommandLineFirst(GeneralCommandLine commandLine, String sdkHome) {
    // override
  }

  /**
   * Patches command line after virtualenv patchers.
   * Default implementation does nothing.
   */
  protected void patchCommandLineLast(GeneralCommandLine commandLine, String sdkHome) {
    // override
  }

  /**
   * Alters PATH so that a virtualenv is activated, if present.
   */
  protected void patchCommandLineForVirtualenv(@NotNull GeneralCommandLine commandLine, @NotNull Sdk sdk) {
    PythonSdkType.patchCommandLineForVirtualenv(commandLine, sdk);
  }

  protected void setUnbufferedEnv() {
    Map<String, String> envs = getEnvs();
    // unbuffered I/O is easier for IDE to handle
    PythonEnvUtil.setPythonUnbuffered(envs);
  }

  @Override
  public boolean isExcludeCompileBeforeLaunchOption() {
    final Module module = getModule();
    return module == null || ModuleType.get(module) instanceof PythonModuleTypeBase;
  }

  public boolean canRunWithCoverage() {
    return true;
  }


  /**
   * Note to inheritors: Always check {@link #getWorkingDirectory()} first. You should return it, if it is not empty since
   * user should be able to set dir explicitly. Then, do your guess and return super as last resort.
   *
   * @return working directory to run, never null, does its best to guess which dir to use.
   * Unlike {@link #getWorkingDirectory()} it does not simply take directory from config.
   */
  public @NotNull String getWorkingDirectorySafe() {
    final String result = StringUtil.isEmpty(myWorkingDirectory) ? getProject().getBasePath() : getExpandedWorkingDir(this);
    if (result != null) {
      return result;
    }

    final String firstModuleRoot = getFirstModuleRoot();
    if (firstModuleRoot != null) {
      return firstModuleRoot;
    }
    return new File(".").getAbsolutePath();
  }

  private @Nullable String getFirstModuleRoot() {
    final Module module = getModule();
    if (module == null) {
      return null;
    }
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    return roots.length > 0 ? roots[0].getPath() : null;
  }

  @Override
  public String getModuleName() {
    Module module = getModule();
    return module != null ? module.getName() : null;
  }

  @Override
  public boolean isBuildBeforeLaunchAddedByDefault() {
    return false;
  }

  /**
   * Adds test specs (like method, class, script, etc) to list of runner parameters.
   * <p>
   * To be deprecated.
   * <p>
   * The part of the legacy implementation based on {@link GeneralCommandLine}.
   */
  public void addTestSpecsAsParameters(final @NotNull ParamsGroup paramsGroup, final @NotNull List<String> testSpecs) {
    // By default we simply add them as arguments
    paramsGroup.addParameters(testSpecs);
  }
}
