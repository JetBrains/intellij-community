package com.jetbrains.python.debugger;

import com.intellij.javascript.debugger.impl.JSDebugProcess;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class PyDebuggerSettings extends XDebuggerSettings<PyDebuggerSettings.PyDebuggerSettingsState> {
  private PyDebuggerSettingsState myState = new PyDebuggerSettingsState();

  public PyDebuggerSettings() {
    super("python");
  }

  public static PyDebuggerSettings getInstance() {
    return getInstance(PyDebuggerSettings.class);
  }

  @NotNull
  public Configurable createConfigurable() {
    return new PyDebuggerConfigurable(this);
  }

  @NotNull
  public PyDebuggerSettingsState getState() {
    return myState;
  }

  public void loadState(final PyDebuggerSettingsState state) {
    myState = state;
  }

  public static class PyDebuggerSettingsState {
    private boolean myAttachToSubprocess = true;
    private boolean mySaveCallSignatures = false;

    @Tag("attach-to-subprocess")
    public boolean isAttachToSubprocess() {
      return myAttachToSubprocess;
    }

    public void setAttachToSubprocess(boolean attachToSubprocess) {
      myAttachToSubprocess = attachToSubprocess;
    }

    @Tag("save-call-signatures")
    public boolean isSaveCallSignatures() {
      return mySaveCallSignatures;
    }

    public void setSaveCallSignatures(boolean saveCallSignatures) {
      mySaveCallSignatures = saveCallSignatures;
    }
  }
}
