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

public class CCCreateAnswerPlaceholderPanel extends JPanel {

  private JPanel myPanel;
  private JTextArea myHintTextField;
  private JTextField myAnswerPlaceholderText;
  private JBLabel myHintLabel;
  private JPanel actionsPanel;
  private JPanel myHintsPanel;

  private List<String> myHints = new ArrayList<String>() {{
    add(ourDefaultHintText);
  }};
  private int myShownHintNumber = 0;
  private static String ourDefaultHintText = "To add hint type text here";

  public CCCreateAnswerPlaceholderPanel() {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    myHintTextField.setLineWrap(true);
    myHintTextField.setWrapStyleWord(true);
    myHintTextField.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myHintTextField.setFont(myAnswerPlaceholderText.getFont());
    if (myHints.get(myShownHintNumber).equals(ourDefaultHintText)) {
      myHintTextField.setForeground(UIUtil.getInactiveTextColor());
    }
    myHintTextField.setText(myHints.get(myShownHintNumber));
    myHintTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myHintTextField.getText().equals(ourDefaultHintText)) {
          myHintTextField.setForeground(UIUtil.getActiveTextColor());
          myHintTextField.setText("");
        }
      }
    });
    
    myAnswerPlaceholderText.grabFocus();
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
    myAnswerPlaceholderText.setText(answerPlaceholderText);
  }

  public void setHintText(String hintTextField) {
    myHintTextField.setForeground(UIUtil.getActiveTextColor());
    myHintTextField.setText(hintTextField);
  }

  public String getAnswerPlaceholderText() {
    return myAnswerPlaceholderText.getText();
  }

  public List<String> getHints() {
    final String hintText = myHintTextField.getText();
    if (myShownHintNumber == 0 && hintText.equals(ourDefaultHintText)) {
      myHints.set(myShownHintNumber, "");
    }
    else {
      myHints.set(myShownHintNumber, hintText);
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

  private class GoForward extends AnAction {

    public GoForward() {
      super(AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextField.getText());
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
      super(AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextField.getText());
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
      super(AllIcons.General.Add);
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
      setHintText(myHints.get(myShownHintNumber));
      updateHintNumberLabel();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myHints.size() > 1);
    }
  }
}
