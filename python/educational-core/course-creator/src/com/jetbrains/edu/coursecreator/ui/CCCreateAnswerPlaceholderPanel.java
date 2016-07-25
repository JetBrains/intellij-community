package com.jetbrains.edu.coursecreator.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

public class CCCreateAnswerPlaceholderPanel {
  private static String ourFirstHintText = "Type here to add hint";
  
  private JPanel myPanel;
  private JTextArea myHintTextArea;
  private JBLabel myHintLabel;
  private JPanel actionsPanel;
  private JPanel myHintsPanel;
  private JTextArea myPlaceholderTextArea;
  private int myShownHintNumber = 0;

  private List<String> myHints = new ArrayList<String>() {{
    add(ourFirstHintText);
  }};

  public CCCreateAnswerPlaceholderPanel() {
    myHintTextArea.setLineWrap(true);
    myHintTextArea.setWrapStyleWord(true);
    myPlaceholderTextArea.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myHintsPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myHintTextArea.setFont(myPlaceholderTextArea.getFont());
    if (myHints.get(myShownHintNumber).equals(ourFirstHintText)) {
      myHintTextArea.setForeground(UIUtil.getInactiveTextColor());
    }
    myHintTextArea.setText(myHints.get(myShownHintNumber));
    myHintTextArea.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myHintTextArea.getText().equals(ourFirstHintText)) {
          myHintTextArea.setForeground(UIUtil.getActiveTextColor());
          myHintTextArea.setText("");
        }
      }
    });

    myPlaceholderTextArea.grabFocus();
    updateHintNumberLabel();

    ((GridLayoutManager)myHintsPanel.getLayout()).setHGap(1);

    final DefaultActionGroup addRemoveGroup = new DefaultActionGroup();
    addRemoveGroup.addAll(new AddHint(), new RemoveHint(), new GoForward(), new GoBackward());
    final JComponent addRemoveComponent = ActionManager.getInstance().createActionToolbar("Hint", addRemoveGroup, false).getComponent();
    actionsPanel.add(addRemoveComponent, BorderLayout.WEST);
  }

  private void updateHintNumberLabel() {
    if (myHints.size() > 1) {
      myHintLabel.setText("Hint" + "(" + (myShownHintNumber + 1) + "/" + myHints.size() + "):");
    }
    else {
      myHintLabel.setText("Hint: ");
    }
  }

  public void setAnswerPlaceholderText(String answerPlaceholderText) {
    myPlaceholderTextArea.setText(answerPlaceholderText);
  }

  public void setHintText(String hintTextField) {
    myHintTextArea.setForeground(UIUtil.getActiveTextColor());
    myHintTextArea.setText(hintTextField);
  }

  public String getAnswerPlaceholderText() {
    return myPlaceholderTextArea.getText();
  }

  public List<String> getHints() {
    final String hintText = myHintTextArea.getText();
    if (myShownHintNumber == 0 && hintText.equals(ourFirstHintText)) {
      myHints.set(myShownHintNumber, "");
    }
    else {
      myHints.set(myShownHintNumber, hintText);
    }

    return myHints;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPlaceholderTextArea;
  }

  public void setHints(List<String> hints) {
    myHints = hints;
    updateHintNumberLabel();
  }

  public JPanel getMailPanel() {
    return myPanel;
  }

  private class GoForward extends AnAction {

    public GoForward() {
      super("Forward", "Forward", AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      setHintText(myHints.get(++myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber + 1 < myHints.size());
    }
  }

  private class GoBackward extends AnAction {

    public GoBackward() {
      super("Back", "Back", AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      setHintText(myHints.get(--myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber - 1 >= 0);
    }
  }

  private class AddHint extends AnAction {

    public AddHint() {
      super("Add New Hint", "Add New Hint", AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.add("");
      setHintText("");
      myShownHintNumber++;
      updateHintNumberLabel();
    }
  }

  private class RemoveHint extends AnAction {

    public RemoveHint() {
      super("Remove Hint", "Remove Hint", AllIcons.General.Remove);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.remove(myShownHintNumber);
      if (myHints.size() == 1) {
        myShownHintNumber = 0;
      }
      else {
        myShownHintNumber += myShownHintNumber + 1 < myHints.size() ? 1 : -1;
      }
      setHintText(myHints.get(myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myHints.size() > 1);
    }
  }
}
