package com.intellij.debugger.settings;

import com.intellij.debugger.ui.ClassFilterEditor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: lex
 * Date: Oct 6, 2003
 * Time: 7:37:05 PM
 */
public class DebuggerGeneralConfigurable implements Configurable{
  private JPanel myPanel;
  private JPanel myNodeRepresentationEditorPlace;
  private JPanel mySteppingFiltersEditorPlace;
  private JRadioButton mySocketTransportRadio;
  private JRadioButton myShmemTransportRadio;
  private JCheckBox myFiltersCheckBox;
  private JCheckBox mySkipSyntheticMethodsCheckBox;
  private JCheckBox mySkipConstructorsCheckBox;
  private JCheckBox myHideDebuggerCheckBox;
  private JRadioButton myRunHotswapAlways;
  private JRadioButton myRunHotswapNever;
  private JRadioButton myRunHotswapAsk;
  private StateRestoringCheckBox myForceClassicCheckBox;
  private ClassFilterEditor myFilterEditor;
  private JTextField myValueLookupDelayField;
  private JCheckBox mySkipGettersCheckBox;
  private JCheckBox myCheckBox1;

  public DebuggerGeneralConfigurable(Project project) {
    myFilterEditor = new ClassFilterEditor(project);
    mySteppingFiltersEditorPlace.setLayout(new BorderLayout());
    mySteppingFiltersEditorPlace.add(myFilterEditor, BorderLayout.CENTER);

    ButtonGroup group = new ButtonGroup();
    group.add(mySocketTransportRadio);
    group.add(myShmemTransportRadio);

    group = new ButtonGroup();
    group.add(myRunHotswapAlways);
    group.add(myRunHotswapNever);
    group.add(myRunHotswapAsk);

    myFiltersCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myFilterEditor.setEnabled(myFiltersCheckBox.isSelected());
      }
    });
  }

  public void reset() {
    if (!SystemInfo.isWindows) {
      mySocketTransportRadio.setSelected(true);
      myShmemTransportRadio.setEnabled(false);
    }
    else {
      if (getSettings().DEBUGGER_TRANSPORT == DebuggerSettings.SHMEM_TRANSPORT) {
        myShmemTransportRadio.setSelected(true);
      }
      else {
        mySocketTransportRadio.setSelected(true);
      }
      myShmemTransportRadio.setEnabled(true);
    }
    mySkipGettersCheckBox.setSelected(getSettings().SKIP_GETTERS);
    mySkipSyntheticMethodsCheckBox.setSelected(getSettings().SKIP_SYNTHETIC_METHODS);
    mySkipConstructorsCheckBox.setSelected(getSettings().SKIP_CONSTRUCTORS);
    myValueLookupDelayField.setText(Integer.toString(getSettings().VALUE_LOOKUP_DELAY));
    myHideDebuggerCheckBox.setSelected(getSettings().HIDE_DEBUGGER_ON_PROCESS_TERMINATION);
    myForceClassicCheckBox.setSelected(getSettings().FORCE_CLASSIC_VM);

    myFiltersCheckBox.setSelected(getSettings().TRACING_FILTERS_ENABLED);

    myFilterEditor.setFilters(getSettings().getFilters());
    myFilterEditor.setEnabled(getSettings().TRACING_FILTERS_ENABLED);

    if(DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(getSettings().RUN_HOTSWAP_AFTER_COMPILE)) {
      myRunHotswapAlways.setSelected(true);
    } else if(DebuggerSettings.RUN_HOTSWAP_NEVER.equals(getSettings().RUN_HOTSWAP_AFTER_COMPILE)) {
      myRunHotswapNever.setSelected(true);
    } else {
      myRunHotswapAsk.setSelected(true);
    }
  }

  public void apply() {
    getSettingsTo(getSettings());
  }

  private void getSettingsTo(DebuggerSettings settings) {
    if (myShmemTransportRadio.isSelected()) {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SHMEM_TRANSPORT;
    }
    else if (mySocketTransportRadio.isSelected()) {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    }
    else {
      settings.DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    }
    settings.SKIP_GETTERS = mySkipGettersCheckBox.isSelected();
    settings.SKIP_SYNTHETIC_METHODS = mySkipSyntheticMethodsCheckBox.isSelected();
    settings.SKIP_CONSTRUCTORS = mySkipConstructorsCheckBox.isSelected();
    try {
      settings.VALUE_LOOKUP_DELAY = Integer.parseInt(myValueLookupDelayField.getText().trim());
    }
    catch (NumberFormatException e) {
    }
    settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION = myHideDebuggerCheckBox.isSelected();
    settings.FORCE_CLASSIC_VM = myForceClassicCheckBox.isSelectedWhenSelectable();
    settings.TRACING_FILTERS_ENABLED = myFiltersCheckBox.isSelected();

    myFilterEditor.stopEditing();
    settings.setFilters(myFilterEditor.getFilters());

    if(myRunHotswapAlways.isSelected())
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ALWAYS;
    else if(myRunHotswapNever.isSelected())
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER;
    else
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK;
  }

  public boolean isModified() {
    DebuggerSettings    debuggerSettings    = new DebuggerSettings();

    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(getSettings());
  }

  public String getDisplayName() {
    return "General";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public void disposeUIResources() {
  }

  private DebuggerSettings getSettings() { return DebuggerSettings.getInstance(); }

}
