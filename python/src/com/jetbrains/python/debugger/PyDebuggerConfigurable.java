package com.jetbrains.python.debugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author traff
 */
public class PyDebuggerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final PyDebuggerSettings mySettings;
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private JCheckBox mySaveSignatures;

  public PyDebuggerConfigurable(final PyDebuggerSettings settings) {
    mySettings = settings;
  }

  public String getDisplayName() {
    return "Python";
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.python";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public boolean isModified() {
    final PyDebuggerSettings.PyDebuggerSettingsState state = mySettings.getState();
    return myAttachToSubprocess.isSelected() != state.isAttachToSubprocess() || mySaveSignatures.isSelected() != state.isSaveCallSignatures();
  }

  public void apply() throws ConfigurationException {
    PyDebuggerSettings.PyDebuggerSettingsState state = mySettings.getState();

    state.setAttachToSubprocess(myAttachToSubprocess.isSelected());
    state.setSaveCallSignatures(mySaveSignatures.isSelected());
  }

  public void reset() {
    final PyDebuggerSettings.PyDebuggerSettingsState state = mySettings.getState();
    myAttachToSubprocess.setSelected(state.isAttachToSubprocess());
    mySaveSignatures.setSelected(state.isSaveCallSignatures());
  }

  public void disposeUIResources() {
  }
}
