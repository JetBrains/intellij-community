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
package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.openapi.components.*;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;

import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
@State(
  name = "PyConsoleOptionsProvider",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class PyConsoleOptions implements PersistentStateComponent<PyConsoleOptions.State> {
  private State myState = new State();

  public PyConsoleSettings getPythonConsoleSettings() {
    return myState.myPythonConsoleState;
  }

  public boolean isShowDebugConsoleByDefault() {
    return myState.myShowDebugConsoleByDefault;
  }

  public void setShowDebugConsoleByDefault(boolean showDebugConsoleByDefault) {
    myState.myShowDebugConsoleByDefault = showDebugConsoleByDefault;
  }

  public boolean isShowSeparatorLine() {
    return myState.myShowSeparatorLine;
  }

  public void setShowSeparatorLine(boolean showSeparatorLine) {
    myState.myShowSeparatorLine = showSeparatorLine;
  }


  public static PyConsoleOptions getInstance(Project project) {
    return ServiceManager.getService(project, PyConsoleOptions.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState.myShowDebugConsoleByDefault = state.myShowDebugConsoleByDefault;
    myState.myShowSeparatorLine = state.myShowSeparatorLine;
    myState.myPythonConsoleState = state.myPythonConsoleState;
  }

  public static class State {
    public PyConsoleSettings myPythonConsoleState = new PyConsoleSettings();

    public boolean myShowDebugConsoleByDefault = false;
    public boolean myShowSeparatorLine = true;
  }

  @Tag("console-settings")
  public static class PyConsoleSettings {
    public String myCustomStartScript = "";
    public String mySdkHome = null;
    public String myInterpreterOptions = "";
    public boolean myUseModuleSdk;
    public String myModuleName = null;
    public Map<String, String> myEnvs = Maps.newHashMap();
    public String myWorkingDirectory = "";
    public boolean myAddContentRoots = true;
    public boolean myAddSourceRoots;
    private List<PathMappingSettings.PathMapping> myMappings;

    public void apply(AbstractPyCommonOptionsForm form) {
      mySdkHome = form.getSdkHome();
      myInterpreterOptions = form.getInterpreterOptions();
      myEnvs = form.getEnvs();
      myUseModuleSdk = form.isUseModuleSdk();
      myModuleName = form.getModule() == null ? null : form.getModule().getName();
      myWorkingDirectory = form.getWorkingDirectory();

      myAddContentRoots = form.addContentRoots();
      myAddSourceRoots = form.addSourceRoots();
      myMappings = form.getMappingSettings() != null ? form.getMappingSettings().getPathMappings() : null;
    }

    public boolean isModified(AbstractPyCommonOptionsForm form) {
      return !ComparatorUtil.equalsNullable(mySdkHome, form.getSdkHome()) ||
             !myInterpreterOptions.equals(form.getInterpreterOptions()) ||
             !myEnvs.equals(form.getEnvs()) ||
             myUseModuleSdk != form.isUseModuleSdk() ||
             myAddContentRoots != form.addContentRoots() ||
             myAddSourceRoots != form.addSourceRoots()
             || !ComparatorUtil.equalsNullable(myModuleName, form.getModule() == null ? null : form.getModule().getName())
             || !myWorkingDirectory.equals(form.getWorkingDirectory())
             || (myMappings != null && !new PathMappingSettings(myMappings).equals(form.getMappingSettings()));
    }

    public void reset(Project project, AbstractPyCommonOptionsForm form) {
      form.setEnvs(myEnvs);
      form.setInterpreterOptions(myInterpreterOptions);
      form.setSdkHome(mySdkHome);
      form.setUseModuleSdk(myUseModuleSdk);
      form.addContentRoots(myAddContentRoots);
      form.addSourceRoots(myAddSourceRoots);
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

      form.setMappingSettings(myMappings != null ? new PathMappingSettings(myMappings) : null);
    }

    @Attribute("custom-start-script")
    public String getCustomStartScript() {
      return myCustomStartScript;
    }

    @Attribute("sdk-home")
    public String getSdkHome() {
      return mySdkHome;
    }

    @Attribute("module-name")
    public String getModuleName() {
      return myModuleName;
    }

    @Attribute("working-directory")
    public String getWorkingDirectory() {
      return myWorkingDirectory;
    }

    @Attribute("is-module-sdk")
    public boolean isUseModuleSdk() {
      return myUseModuleSdk;
    }

    @Attribute("envs")
    public Map<String, String> getEnvs() {
      return myEnvs;
    }

    @Attribute("add-content-roots")
    public boolean addContentRoots() {
      return myAddContentRoots;
    }

    @Attribute("add-source-roots")
    public boolean addSourceRoots() {
      return myAddSourceRoots;
    }
  }
}

