package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Map;

public class IgnoreWhiteSpacesAction extends ComboBoxAction {
  private final Map<ComparisonPolicy, AnAction> myActions = new HashMap<ComparisonPolicy, AnAction>();
  private static final ComparisonPolicy[] ourActionOrder = new ComparisonPolicy[]{
    ComparisonPolicy.DEFAULT,
    ComparisonPolicy.TRIM_SPACE,
    ComparisonPolicy.IGNORE_SPACE};
  private final DiffPanelEx myDiffPanel;

  public IgnoreWhiteSpacesAction(DiffPanelEx diffPanel) {
    myActions.put(ComparisonPolicy.DEFAULT, new IgnoringPolicyAction("Do not ignore", ComparisonPolicy.DEFAULT));
    myActions.put(ComparisonPolicy.TRIM_SPACE, new IgnoringPolicyAction("Leading and trailing", ComparisonPolicy.TRIM_SPACE));
    myActions.put(ComparisonPolicy.IGNORE_SPACE, new IgnoringPolicyAction("All", ComparisonPolicy.IGNORE_SPACE));
    myDiffPanel = diffPanel;
  }

  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (int i = 0; i < ourActionOrder.length; i++) {
      ComparisonPolicy comparisonPolicy = ourActionOrder[i];
      actionGroup.add(myActions.get(comparisonPolicy));
    }
    return actionGroup;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DiffPanelEx diffPanel = myDiffPanel;
    if (diffPanel != null && diffPanel.getComponent().isDisplayable()) {
      AnAction actoin = myActions.get(diffPanel.getComparisonPolicy());
      Presentation templatePresentation = actoin.getTemplatePresentation();
      presentation.setIcon(templatePresentation.getIcon());
      presentation.setText(templatePresentation.getText());
      presentation.setEnabled(true);
    } else {
      presentation.setIcon(null);
      presentation.setText("<Not avaliable>");
      presentation.setEnabled(false);
    }
  }

  private class IgnoringPolicyAction extends AnAction {
    private final ComparisonPolicy myPolicy;

    public IgnoringPolicyAction(String text, ComparisonPolicy policy) {
      super(text);
      myPolicy = policy;
    }

    public void actionPerformed(AnActionEvent e) {
      myDiffPanel.setComparisonPolicy(myPolicy);
    }
  }
}
