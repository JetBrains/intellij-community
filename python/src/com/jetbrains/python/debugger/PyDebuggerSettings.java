package com.jetbrains.python.debugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

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
