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
  private static final ColumnInfo<JSDebuggerSettings.SteppingFilterState, Boolean> myEnabledColumn =
    new ColumnInfo<JSDebuggerSettings.SteppingFilterState, Boolean>("1") {
      public Boolean valueOf(JSDebuggerSettings.SteppingFilterState steppingFilterState) {
        return steppingFilterState.isEnabled();
      }

      @Override
      public void setValue(JSDebuggerSettings.SteppingFilterState steppingFilterState, Boolean value) {
        steppingFilterState.setEnabled(value);
      }

      @Override
      public boolean isCellEditable(JSDebuggerSettings.SteppingFilterState steppingFilterState) {
        return true;
      }

      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }
    };
  private static final ColumnInfo<JSDebuggerSettings.SteppingFilterState, String> URL_PATTERN_COLUMN =
    new ColumnInfo<JSDebuggerSettings.SteppingFilterState, String>("2") {
      public String valueOf(JSDebuggerSettings.SteppingFilterState steppingFilterState) {
        return steppingFilterState.getUrlPattern();
      }

      @Override
      public void setValue(JSDebuggerSettings.SteppingFilterState steppingFilterState, String value) {
        steppingFilterState.setUrlPattern(value);
      }

      @Override
      public boolean isCellEditable(JSDebuggerSettings.SteppingFilterState steppingFilterState) {
        return true;
      }
    };

  private final PyDebuggerSettings mySettings;
  private JPanel myMainPanel;
  private JCheckBox myAttachToSubprocess;
  private ListTableModel<PyDebuggerSettings.SteppingFilterState> myFiltersTableModel;


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
    return myAttachToSubprocess.isSelected() != state.isAttachToSubprocess();
  }

  public void apply() throws ConfigurationException {
    PyDebuggerSettings.PyDebuggerSettingsState state = mySettings.getState();

    state.setAttachToSubprocess(myAttachToSubprocess.isSelected());
  }

  public void reset() {
    final PyDebuggerSettings.PyDebuggerSettingsState state = mySettings.getState();
    myAttachToSubprocess.setSelected(state.isAttachToSubprocess());
  }

  public void disposeUIResources() {
  }
}
