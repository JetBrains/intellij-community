package com.jetbrains.python.console;

import com.google.common.collect.Maps;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;

import java.util.Map;

/**
 * @author traff
 */
@State(
  name = "PyConsoleOptionsProvider",
  storages = {
    @Storage(file = "$WORKSPACE_FILE$")
  }
)
public class PyConsoleOptionsProvider implements PersistentStateComponent<PyConsoleOptionsProvider.State> {
  private State myState = new State();

  private final Project myProject;

  public PyConsoleOptionsProvider(Project project) {
    myProject = project;
  }

  public PyConsoleSettings getPythonConsoleSettings() {
    return myState.myPythonConsoleState;
  }

  public PyConsoleSettings getDjangoConsoleSettings() {
    return myState.myDjangoConsoleState;
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


  public static PyConsoleOptionsProvider getInstance(Project project) {
    return ServiceManager.getService(project, PyConsoleOptionsProvider.class);
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
    myState.myDjangoConsoleState = state.myDjangoConsoleState;
    myState.myPythonConsoleState.myProject = myProject;
    myState.myDjangoConsoleState.myProject = myProject;
  }

  public static class State {
    public PyConsoleSettings myPythonConsoleState = new PyConsoleSettings();
    public PyConsoleSettings myDjangoConsoleState = new PyConsoleSettings();

    public boolean myShowDebugConsoleByDefault = false;
    public boolean myShowSeparatorLine = true;
  }

  public static class PyConsoleSettings {
    public String myCustomStartScript = "";
    public String mySdkHome = null;
    public String myInterpreterOptions = "";
    public boolean myUseModuleSdk;
    public String myModuleName = null;
    public Map<String, String> myEnvs = Maps.newHashMap();
    public String myWorkingDirectory = "";
    @Transient
    private Project myProject;

    public String getCustomStartScript() {
      return myCustomStartScript;
    }

    public String getSdkHome() {
      return mySdkHome;
    }

    public void apply(AbstractPyCommonOptionsForm form) {
      mySdkHome = form.getSdkHome();
      myInterpreterOptions = form.getInterpreterOptions();
      myEnvs = form.getEnvs();
      myUseModuleSdk = form.isUseModuleSdk();
      myModuleName = form.getModule() == null ? null : form.getModule().getName();
      myWorkingDirectory = form.getWorkingDirectory();
    }

    public boolean isModified(AbstractPyCommonOptionsForm form) {
      return !ComparatorUtil.equalsNullable(mySdkHome, form.getSdkHome()) ||
             !myInterpreterOptions.equals(form.getInterpreterOptions()) ||
             !myEnvs.equals(form.getEnvs()) ||
             myUseModuleSdk != form.isUseModuleSdk()
             || !ComparatorUtil.equalsNullable(myModuleName, form.getModule() == null ? null : form.getModule().getName())
             || !myWorkingDirectory.equals(form.getWorkingDirectory());
    }

    public void reset(AbstractPyCommonOptionsForm form) {
      form.setEnvs(myEnvs);
      form.setInterpreterOptions(myInterpreterOptions);
      form.setSdkHome(mySdkHome);
      form.setUseModuleSdk(myUseModuleSdk);
      boolean moduleWasAutoselected = false;
      if (form.isUseModuleSdk() != myUseModuleSdk) {
        myUseModuleSdk = form.isUseModuleSdk();
        moduleWasAutoselected = true;
      }
      form.setModule(myModuleName == null ? null : ModuleManager.getInstance(myProject).findModuleByName(myModuleName));
      if (moduleWasAutoselected) {
        myModuleName = form.getModule().getName();
      }
      form.setWorkingDirectory(form.getWorkingDirectory());
    }

    public String getModuleName() {
      return myModuleName;
    }

    public String getWorkingDirectory() {
      return myWorkingDirectory;
    }

    public boolean isUseModuleSdk() {
      return myUseModuleSdk;
    }
  }
}

