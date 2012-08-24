package com.jetbrains.python.debugger;

import com.intellij.CommonBundle;
import com.intellij.javascript.debugger.JSDebuggerBundle;
import com.intellij.javascript.debugger.settings.JSDebuggerSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

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
