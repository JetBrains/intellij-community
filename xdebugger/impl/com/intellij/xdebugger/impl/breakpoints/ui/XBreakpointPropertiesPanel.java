package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class XBreakpointPropertiesPanel<B extends XBreakpoint<?>> {
  private JPanel myMainPanel;
  private JCheckBox mySuspendCheckBox;
  private JRadioButton mySuspendAllRadioButton;
  private JRadioButton mySuspendThreadRadioButton;
  private JRadioButton mySuspendNoneRadioButton;
  private JCheckBox myLogMessageCheckBox;
  private JCheckBox myLogExpressionCheckBox;
  private JPanel myLogExpressionPanel;
  private JCheckBox myConditionCheckBox;
  private JPanel myConditionExpressionPanel;
  private JPanel myCustomConditionsPanelWrapper;
  private JPanel mySuspendPolicyPanel;
  private JPanel myCustomPropertiesPanelWrapper;
  private final B myBreakpoint;
  private final Map<SuspendPolicy, JRadioButton> mySuspendRadioButtons;
  private List<XBreakpointCustomPropertiesPanel<B>> myCustomPanels;
  private XDebuggerExpressionComboBox myLogExpressionComboBox;
  private XDebuggerExpressionComboBox myConditionComboBox;

  public XBreakpointPropertiesPanel(final Project project, @NotNull B breakpoint) {
    myBreakpoint = breakpoint;

    XBreakpointType<B,?> type = XDebuggerUtilImpl.getType(breakpoint);

    mySuspendRadioButtons = new HashMap<SuspendPolicy, JRadioButton>();
    mySuspendRadioButtons.put(SuspendPolicy.ALL, mySuspendAllRadioButton);
    mySuspendRadioButtons.put(SuspendPolicy.THREAD, mySuspendThreadRadioButton);
    mySuspendRadioButtons.put(SuspendPolicy.NONE, mySuspendNoneRadioButton);
    @NonNls String card = type.isSuspendThreadSupported() ? "radioButtons" : "checkbox";
    ((CardLayout)mySuspendPolicyPanel.getLayout()).show(mySuspendPolicyPanel, card);

    XDebuggerEditorsProvider debuggerEditorsProvider = type.getEditorsProvider();
    if (debuggerEditorsProvider != null) {
      ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onCheckboxChanged();
        }
      };

      myLogExpressionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, "breakpointLogExpression");
      JComponent logExpressionComponent = myLogExpressionComboBox.getComponent();
      myLogExpressionPanel.add(logExpressionComponent, BorderLayout.CENTER);
      myLogExpressionComboBox.setEnabled(false);
      myLogExpressionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myLogExpressionCheckBox, logExpressionComponent);

      myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, "breakpointCondition");
      JComponent conditionComponent = myConditionComboBox.getComponent();
      myConditionExpressionPanel.add(conditionComponent, BorderLayout.CENTER);
      myConditionComboBox.setEnabled(false);
      myConditionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myConditionCheckBox, conditionComponent);
    }
    else {
      myLogExpressionCheckBox.setVisible(false);
      myConditionCheckBox.setVisible(false);
    }

    myCustomPanels = new ArrayList<XBreakpointCustomPropertiesPanel<B>>();
    XBreakpointCustomPropertiesPanel<B> customConditionPanel = type.createCustomConditionsPanel();
    if (customConditionPanel != null) {
      myCustomConditionsPanelWrapper.add(customConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customConditionPanel);
    }

    XBreakpointCustomPropertiesPanel<B> customPropertiesPanel = type.createCustomPropertiesPanel();
    if (customPropertiesPanel != null) {
      myCustomPropertiesPanelWrapper.add(customPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customPropertiesPanel);
    }

    loadProperties();
  }

  private void onCheckboxChanged() {
    myLogExpressionComboBox.setEnabled(myLogExpressionCheckBox.isSelected());
    myConditionComboBox.setEnabled(myConditionCheckBox.isSelected());
  }

  private void loadProperties() {
    SuspendPolicy suspendPolicy = myBreakpoint.getSuspendPolicy();
    mySuspendRadioButtons.get(suspendPolicy).setSelected(true);
    mySuspendCheckBox.setSelected(suspendPolicy != SuspendPolicy.NONE);

    myLogMessageCheckBox.setSelected(myBreakpoint.isLogMessage());
    if (myLogExpressionComboBox != null) {
      String logExpression = myBreakpoint.getLogExpression();
      myLogExpressionCheckBox.setSelected(logExpression != null);
      myLogExpressionComboBox.setText(logExpression != null ? logExpression : "");
    }
    if (myConditionComboBox != null) {
      String condition = myBreakpoint.getCondition();
      myConditionCheckBox.setSelected(condition != null);
      myConditionComboBox.setText(condition != null ? condition : "");
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.loadFrom(myBreakpoint);
    }

    onCheckboxChanged();
  }

  public B getBreakpoint() {
    return myBreakpoint;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void saveProperties() {
    myBreakpoint.setSuspendPolicy(getConfiguredSuspendPolicy());
    myBreakpoint.setLogMessage(myLogMessageCheckBox.isSelected());

    if (myLogExpressionComboBox != null) {
      String logExpression = myLogExpressionCheckBox.isSelected() ? myLogExpressionComboBox.getText() : null;
      myBreakpoint.setLogExpression(logExpression);
      myLogExpressionComboBox.saveTextInHistory();
    }

    if (myConditionComboBox != null) {
      String condition = myConditionCheckBox.isSelected() ? myConditionComboBox.getText() : null;
      myBreakpoint.setCondition(condition);
      myConditionComboBox.saveTextInHistory();
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
  }

  private SuspendPolicy getConfiguredSuspendPolicy() {
    if (!myBreakpoint.getType().isSuspendThreadSupported()) {
      return mySuspendCheckBox.isSelected() ? SuspendPolicy.ALL : SuspendPolicy.NONE;
    }

    for (SuspendPolicy policy : mySuspendRadioButtons.keySet()) {
      if (mySuspendRadioButtons.get(policy).isSelected()) {
        return policy;
      }
    }
    return SuspendPolicy.ALL;
  }

  public void dispose() {
    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.dispose();
    }
  }
}
