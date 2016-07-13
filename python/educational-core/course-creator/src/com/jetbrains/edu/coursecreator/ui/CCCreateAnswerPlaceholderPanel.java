package com.jetbrains.edu.coursecreator.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CCCreateAnswerPlaceholderPanel extends JPanel {

  private JPanel myPanel;
  private JTextArea myHintTextField;
  private JTextField myAnswerPlaceholderText;
  private JBLabel myHintLabel;
  private JPanel actionsPanel;
  private JPanel myHintsPanel;

  private List<String> myHints = new ArrayList<String>() {{
    add("");
  }};
  private int myShownHintNumber = 0;

  public CCCreateAnswerPlaceholderPanel() {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    myHintTextField.setLineWrap(true);
    myHintTextField.setWrapStyleWord(true);
    myHintTextField.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myHintTextField.setFont(myAnswerPlaceholderText.getFont());
    myHintTextField.setText(myHints.get(myShownHintNumber));
    myAnswerPlaceholderText.grabFocus();
    updateHintNumberLabel();
    
    ((GridLayoutManager)myHintsPanel.getLayout()).setHGap(1);

    final DefaultActionGroup addRemoveGroup = new DefaultActionGroup();
    addRemoveGroup.addAll(new AddHint(), new RemoveHint(), new GoBackward(), new GoForward());
    final JComponent addRemoveComponent = ActionManager.getInstance().createActionToolbar("Hint", addRemoveGroup, false).getComponent();
    actionsPanel.add(addRemoveComponent, BorderLayout.WEST);
  }

  private void updateHintNumberLabel() {
    myHintLabel.setText("Hints" + "(" + (myShownHintNumber + 1) + "/" + myHints.size() + "):");
  }

  public void setAnswerPlaceholderText(String answerPlaceholderText) {
    myAnswerPlaceholderText.setText(answerPlaceholderText);
  }

  public void setHintText(String hintTextField) {
    myHintTextField.setText(hintTextField);
  }

  public String getAnswerPlaceholderText() {
    return myAnswerPlaceholderText.getText();
  }

  public List<String> getHints() {
    if (myShownHintNumber == 0 && !myHintTextField.getText().isEmpty()) {
      myHints.set(myShownHintNumber, myHintTextField.getText());
    }
    return myHints;
  }

  public JComponent getPreferredFocusedComponent() {
    return myAnswerPlaceholderText;
  }

  public void setHints(List<String> hints) {
    myHints = hints;
    updateHintNumberLabel();
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here 
  }

  private class GoForward extends AnAction {

    public GoForward() {
      super(AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextField.getText());
      myHintTextField.setText(myHints.get(++myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber + 1 < myHints.size());
    }
  }

  private class GoBackward extends AnAction {

    public GoBackward() {
      super(AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextField.getText());
      myHintTextField.setText(myHints.get(--myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber - 1 >= 0);
    }
  }

  private class AddHint extends AnAction {

    public AddHint() {
      super(AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String hint = "This is the new hint";
      myHints.add(hint);
      myHintTextField.setText(hint);
      myShownHintNumber++;
      updateHintNumberLabel();
    }
  }

  private class RemoveHint extends AnAction {

    public RemoveHint() {
      super(AllIcons.General.Remove);
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
      myHintTextField.setText(myHints.get(myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myHints.size() > 1);
    }
  }
}
