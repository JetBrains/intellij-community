// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import com.jetbrains.python.console.actions.CommandQueueForPythonConsoleService;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import com.jetbrains.python.run.PythonRunParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(
  name = "PyConsoleOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class PyConsoleOptions implements PersistentStateComponent<PyConsoleOptions.State> {
  private final State myState = new State();

  @NotNull
  public PyConsoleSettings getPythonConsoleSettings() {
    return myState.myPythonConsoleState;
  }

  public boolean isShowDebugConsoleByDefault() {
    return myState.myShowDebugConsoleByDefault;
  }

  public void setShowDebugConsoleByDefault(boolean showDebugConsoleByDefault) {
    myState.myShowDebugConsoleByDefault = showDebugConsoleByDefault;
  }

  public boolean isShowVariableByDefault() {
    return myState.myShowVariablesByDefault;
  }

  public void setShowVariablesByDefault(boolean showVariableByDefault) {
    myState.myShowVariablesByDefault = showVariableByDefault;
  }

  public boolean isIpythonEnabled() {
    return myState.myIpythonEnabled;
  }

  public void setIpythonEnabled(boolean enabled) {
    myState.myIpythonEnabled = enabled;
  }

  public boolean isUseExistingConsole() {
    return myState.myUseExistingConsole;
  }

  public void setUseExistingConsole(boolean enabled) {
    myState.myUseExistingConsole = enabled;
  }

  public void setCommandQueueEnabled(boolean selected) {
    myState.myCommandQueueEnabled = selected;
    if (!selected) {
      ApplicationManager.getApplication().getService(CommandQueueForPythonConsoleService.class).disableCommandQueue();
    }
  }

  public boolean isCommandQueueEnabled() {
    return myState.myCommandQueueEnabled;
  }

  public void setAutoCompletionEnabled(boolean selected) {
    myState.myAutoCompletionEnabled = selected;
  }

  public boolean isAutoCompletionEnabled() {
    return myState.myAutoCompletionEnabled;
  }

  public static PyConsoleOptions getInstance(Project project) {
    return project.getService(PyConsoleOptions.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState.myShowDebugConsoleByDefault = state.myShowDebugConsoleByDefault;
    myState.myShowVariablesByDefault = state.myShowVariablesByDefault;
    myState.myPythonConsoleState = state.myPythonConsoleState;
    myState.myIpythonEnabled = state.myIpythonEnabled;
    myState.myUseExistingConsole = state.myUseExistingConsole;
    myState.myCommandQueueEnabled = state.myCommandQueueEnabled;
    myState.myAutoCompletionEnabled = state.myAutoCompletionEnabled;
  }

  public static class State {
    public PyConsoleSettings myPythonConsoleState = new PyConsoleSettings();

    public boolean myShowDebugConsoleByDefault = true;
    public boolean myShowVariablesByDefault = true;
    public boolean myIpythonEnabled = true;
    public boolean myUseExistingConsole = false;
    public boolean myCommandQueueEnabled = PlatformUtils.isDataSpell();
    public boolean myAutoCompletionEnabled = true;
  }

  @Tag("console-settings")
  public static class PyConsoleSettings implements PythonRunParams {
    public String myCustomStartScript = PydevConsoleRunnerImpl.CONSOLE_START_COMMAND;
    public String mySdkHome = null;
    public Sdk mySdk = null;
    public String myInterpreterOptions = "";
    public boolean myUseModuleSdk;
    public String myModuleName = null;
    public Map<String, String> myEnvs = Maps.newHashMap();
    public boolean myPassParentEnvs = true;
    public String myWorkingDirectory = "";
    public boolean myAddContentRoots = true;
    public boolean myAddSourceRoots = true;
    @NotNull
    private PathMappingSettings myMappings = new PathMappingSettings();
    private boolean myUseSoftWraps = false;

    public PyConsoleSettings() {
    }

    public PyConsoleSettings(String myCustomStartScript) {
      this.myCustomStartScript = myCustomStartScript;
    }

    public void apply(AbstractPythonRunConfigurationParams form) {
      mySdkHome = form.getSdkHome();
      mySdk = form.getSdk();
      myInterpreterOptions = form.getInterpreterOptions();
      myEnvs = form.getEnvs();
      myPassParentEnvs = form.isPassParentEnvs();
      myUseModuleSdk = form.isUseModuleSdk();
      myModuleName = form.getModule() == null ? null : form.getModule().getName();
      myWorkingDirectory = form.getWorkingDirectory();

      myAddContentRoots = form.shouldAddContentRoots();
      myAddSourceRoots = form.shouldAddSourceRoots();
      myMappings = form.getMappingSettings() == null ? new PathMappingSettings() : form.getMappingSettings();
    }

    public boolean isModified(AbstractPyCommonOptionsForm form) {
      return !ComparatorUtil.equalsNullable(mySdkHome, form.getSdkHome()) ||
             !myInterpreterOptions.equals(form.getInterpreterOptions()) ||
             !myEnvs.equals(form.getEnvs()) ||
             myPassParentEnvs != form.isPassParentEnvs() ||
             myUseModuleSdk != form.isUseModuleSdk() ||
             myAddContentRoots != form.shouldAddContentRoots() ||
             myAddSourceRoots != form.shouldAddSourceRoots()
             || !ComparatorUtil.equalsNullable(myModuleName, form.getModule() == null ? null : form.getModule().getName())
             || !myWorkingDirectory.equals(form.getWorkingDirectory())
             || !myMappings.equals(form.getMappingSettings());
    }

    public void reset(Project project, AbstractPythonRunConfigurationParams form) {
      form.setEnvs(myEnvs);
      form.setPassParentEnvs(myPassParentEnvs);
      form.setInterpreterOptions(myInterpreterOptions);
      form.setSdkHome(mySdkHome);
      form.setSdk(mySdk);
      form.setUseModuleSdk(myUseModuleSdk);
      form.setAddContentRoots(myAddContentRoots);
      form.setAddSourceRoots(myAddSourceRoots);

      boolean moduleWasAutoselected = false;
      if (form.isUseModuleSdk() != myUseModuleSdk) {
        myUseModuleSdk = form.isUseModuleSdk();
        moduleWasAutoselected = true;
      }

      if (myModuleName != null) {
        form.setModule(ModuleManager.getInstance(project).findModuleByName(myModuleName));
      }

      if (moduleWasAutoselected && form.getModule() != null) {
        myModuleName = form.getModule().getName();
      }

      form.setWorkingDirectory(myWorkingDirectory);

      form.setMappingSettings(myMappings);
    }

    @Attribute("custom-start-script")
    public String getCustomStartScript() {
      return myCustomStartScript;
    }

    @Override
    @Attribute("sdk-home")
    public String getSdkHome() {
      return mySdkHome;
    }

    @Override
    @Nullable
    public Sdk getSdk() {
      return mySdk;
    }

    @Override
    @Attribute("module-name")
    public String getModuleName() {
      return myModuleName;
    }

    @Override
    @Attribute("working-directory")
    public String getWorkingDirectory() {
      return myWorkingDirectory;
    }

    @Override
    @Attribute("is-module-sdk")
    public boolean isUseModuleSdk() {
      return myUseModuleSdk;
    }

    @Override
    @XMap(propertyElementName = "envs", entryTagName = "env")
    public Map<String, String> getEnvs() {
      return myEnvs;
    }

    @Override
    @Attribute("add-content-roots")
    public boolean shouldAddContentRoots() {
      return myAddContentRoots;
    }

    @Override
    @Attribute("add-source-roots")
    public boolean shouldAddSourceRoots() {
      return myAddSourceRoots;
    }

    @Override
    @Attribute("interpreter-options")
    public String getInterpreterOptions() {
      return myInterpreterOptions;
    }

    @NotNull
    @XCollection
    public PathMappingSettings getMappings() {
      return myMappings;
    }

    public void setCustomStartScript(String customStartScript) {
      myCustomStartScript = customStartScript;
    }

    @Override
    public void setSdkHome(String sdkHome) {
      mySdkHome = sdkHome;
    }

    @Override
    public void setSdk(@Nullable Sdk sdk) {
      mySdk = sdk;
    }

    @Override
    public void setModule(Module module) {
      setModuleName(module.getName());
    }

    @Override
    public void setInterpreterOptions(String interpreterOptions) {
      myInterpreterOptions = interpreterOptions;
    }

    @Override
    public void setUseModuleSdk(boolean useModuleSdk) {
      myUseModuleSdk = useModuleSdk;
    }

    @Override
    @Attribute("is-pass-parent-envs")
    public boolean isPassParentEnvs() {
      return myPassParentEnvs;
    }

    @Override
    public void setPassParentEnvs(boolean passParentEnvs) {
      myPassParentEnvs = passParentEnvs;
    }

    public void setModuleName(String moduleName) {
      myModuleName = moduleName;
    }

    @Override
    public void setEnvs(Map<String, String> envs) {
      myEnvs = envs;
    }

    @Nullable
    @Override
    public PathMappingSettings getMappingSettings() {
      return getMappings();
    }

    @Override
    public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {

    }

    @Override
    public void setWorkingDirectory(String workingDirectory) {
      myWorkingDirectory = workingDirectory;
    }

    @Override
    public void setAddContentRoots(boolean addContentRoots) {
      myAddContentRoots = addContentRoots;
    }

    @Override
    public void setAddSourceRoots(boolean addSourceRoots) {
      myAddSourceRoots = addSourceRoots;
    }

    public void setMappings(@Nullable PathMappingSettings mappings) {
      myMappings = mappings != null ? mappings : new PathMappingSettings();
    }

    public boolean isUseSoftWraps() {
      return myUseSoftWraps;
    }

    public void setUseSoftWraps(boolean useSoftWraps) {
      myUseSoftWraps = useSoftWraps;
    }
  }
}

