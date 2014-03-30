/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.configuration.AbstractRunConfiguration;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonModuleTypeBase;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public abstract class AbstractPythonRunConfiguration<T extends AbstractRunConfiguration> extends AbstractRunConfiguration
  implements LocatableConfiguration, AbstractPythonRunConfigurationParams, CommandLinePatcher {
  private String myInterpreterOptions = "";
  private String myWorkingDirectory = "";
  private String mySdkHome = "";
  private boolean myUseModuleSdk;
  private boolean myAddContentRoots = true;
  private boolean myAddSourceRoots = true;
  protected PathMappingSettings myMappingSettings;

  public AbstractPythonRunConfiguration(Project project, final ConfigurationFactory factory) {
    super(project, factory);
    getConfigurationModule().init();
  }

  public List<Module> getValidModules() {
    return getValidModules(getProject());
  }

  public PathMappingSettings getMappingSettings() {
    return myMappingSettings;
  }

  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    myMappingSettings = mappingSettings;
  }

  public static List<Module> getValidModules(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    List<Module> result = Lists.newArrayList();
    for (Module module : modules) {
      if (PythonSdkType.findPythonSdk(module) != null) {
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

  @NotNull
  @Override
  public final SettingsEditor<T> getConfigurationEditor() {
    final SettingsEditor<T> runConfigurationEditor = createConfigurationEditor();

    final SettingsEditorGroup<T> group = new SettingsEditorGroup<T>();

    // run configuration settings tab:
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), runConfigurationEditor);

    // tabs provided by extensions:
    //noinspection unchecked
    PythonRunConfigurationExtensionsManager.getInstance().appendEditors(this, (SettingsEditorGroup)group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<T>());

    return group;
  }

  protected abstract SettingsEditor<T> createConfigurationEditor();

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    checkSdk();

    checkExtensions();
  }

  private void checkExtensions() throws RuntimeConfigurationException {
    try {
      PythonRunConfigurationExtensionsManager.getInstance().validateConfiguration(this, false);
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

  public Sdk getSdk() {
    if (myUseModuleSdk) {
      return PythonSdkType.findPythonSdk(getModule());
    }
    else {
      return PythonSdkType.findSdkByPath(getSdkHome());
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myInterpreterOptions = JDOMExternalizerUtil.readField(element, "INTERPRETER_OPTIONS");
    readEnvs(element);
    mySdkHome = JDOMExternalizerUtil.readField(element, "SDK_HOME");
    myWorkingDirectory = JDOMExternalizerUtil.readField(element, "WORKING_DIRECTORY");
    myUseModuleSdk = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "IS_MODULE_SDK"));
    final String addContentRoots = JDOMExternalizerUtil.readField(element, "ADD_CONTENT_ROOTS");
    myAddContentRoots = addContentRoots == null || Boolean.parseBoolean(addContentRoots);
    final String addSourceRoots = JDOMExternalizerUtil.readField(element, "ADD_SOURCE_ROOTS");
    myAddSourceRoots = addSourceRoots == null|| Boolean.parseBoolean(addSourceRoots);
    getConfigurationModule().readExternal(element);

    setMappingSettings(PathMappingSettings.readExternal(element));
    // extension settings:
    PythonRunConfigurationExtensionsManager.getInstance().readExternal(this, element);
  }

  protected void readEnvs(Element element) {
    final String parentEnvs = JDOMExternalizerUtil.readField(element, "PARENT_ENVS");
    if (parentEnvs != null) {
      setPassParentEnvs(Boolean.parseBoolean(parentEnvs));
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
    JDOMExternalizerUtil.writeField(element, "ADD_CONTENT_ROOTS", Boolean.toString(myAddContentRoots));
    JDOMExternalizerUtil.writeField(element, "ADD_SOURCE_ROOTS", Boolean.toString(myAddSourceRoots));
    getConfigurationModule().writeExternal(element);

    // extension settings:
    PythonRunConfigurationExtensionsManager.getInstance().writeExternal(this, element);

    PathMappingSettings.writeExternal(element, getMappingSettings());
  }

  protected void writeEnvs(Element element) {
    JDOMExternalizerUtil.writeField(element, "PARENT_ENVS", Boolean.toString(isPassParentEnvs()));
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
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

  @Override
  public boolean addContentRoots() {
    return myAddContentRoots;
  }

  @Override
  public boolean addSourceRoots() {
    return myAddSourceRoots;
  }

  @Override
  public void addSourceRoots(boolean add) {
    myAddSourceRoots = add;
  }

  @Override
  public void addContentRoots(boolean add) {
    myAddContentRoots = add;
  }

  public static void copyParams(AbstractPythonRunConfigurationParams source, AbstractPythonRunConfigurationParams target) {
    target.setEnvs(new HashMap<String, String>(source.getEnvs()));
    target.setInterpreterOptions(source.getInterpreterOptions());
    target.setPassParentEnvs(source.isPassParentEnvs());
    target.setSdkHome(source.getSdkHome());
    target.setWorkingDirectory(source.getWorkingDirectory());
    target.setModule(source.getModule());
    target.setUseModuleSdk(source.isUseModuleSdk());
    target.setMappingSettings(source.getMappingSettings());
    target.addContentRoots(source.addContentRoots());
    target.addSourceRoots(source.addSourceRoots());
  }

  /**
   * Some setups (e.g. virtualenv) provide a script that alters environment variables before running a python interpreter or other tools.
   * Such settings are not directly stored but applied right before running using this method.
   *
   * @param commandLine what to patch
   */
  public void patchCommandLine(GeneralCommandLine commandLine) {
    final String interpreterPath = getInterpreterPath();
    Sdk sdk = getSdk();
    if (sdk != null && interpreterPath != null) {
      patchCommandLineFirst(commandLine, interpreterPath);
      patchCommandLineForVirtualenv(commandLine, interpreterPath);
      patchCommandLineForBuildout(commandLine, interpreterPath);
      patchCommandLineLast(commandLine, interpreterPath);
    }
  }

  /**
   * Patches command line before virtualenv and buildout patchers.
   * Default implementation does nothing.
   *
   * @param commandLine
   * @param sdk_home
   */
  protected void patchCommandLineFirst(GeneralCommandLine commandLine, String sdk_home) {
    // override
  }

  /**
   * Patches command line after virtualenv and buildout patchers.
   * Default implementation does nothing.
   *
   * @param commandLine
   * @param sdk_home
   */
  protected void patchCommandLineLast(GeneralCommandLine commandLine, String sdk_home) {
    // override
  }

  /**
   * Gets called after {@link #patchCommandLineForVirtualenv(com.intellij.openapi.projectRoots.SdkType, com.intellij.openapi.projectRoots.SdkType)}
   * Does nothing here, real implementations should use alter running script name or use engulfer.
   *
   * @param commandLine
   * @param sdkHome
   */
  protected void patchCommandLineForBuildout(GeneralCommandLine commandLine, String sdkHome) {
  }

  /**
   * Alters PATH so that a virtualenv is activated, if present.
   *
   * @param commandLine
   * @param sdkHome
   */
  protected void patchCommandLineForVirtualenv(GeneralCommandLine commandLine, String sdkHome) {
    PythonSdkType.patchCommandLineForVirtualenv(commandLine, sdkHome, isPassParentEnvs());
  }

  protected void setUnbufferedEnv() {
    Map<String, String> envs = getEnvs();
    // unbuffered I/O is easier for IDE to handle
    PythonEnvUtil.setPythonUnbuffered(envs);
  }

  @Override
  public boolean excludeCompileBeforeLaunchOption() {
    final Module module = getModule();
    return module != null ? ModuleType.get(module) instanceof PythonModuleTypeBase : true;
  }

  public boolean canRunWithCoverage() {
    return true;
  }

  public String getTestSpec(Location location) {
    PsiElement element = location.getPsiElement();
    PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
    final VirtualFile virtualFile = location.getVirtualFile();
    if (virtualFile != null) {
      String path = virtualFile.getCanonicalPath();
      if (pyClass != null)
        path += "::" + pyClass.getName();
      if (pyFunction != null)
        path += "::" + pyFunction.getName();
      return path;
    }
    return null;
  }
}
