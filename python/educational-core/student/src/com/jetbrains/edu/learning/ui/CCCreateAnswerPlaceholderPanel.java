package com.jetbrains.edu.learning.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import org.jetbrains.annotations.NotNull;

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
  private JPanel myHintsPanel;
  private JBLabel myHintLabel;
  private JPanel actionsPanel;
  private JTextArea myPlaceholderTextArea;
  private List<String> myHints = new ArrayList<String>() {
  };
  private int myShownHintNumber = 0;

  public CCCreateAnswerPlaceholderPanel(@NotNull final AnswerPlaceholder answerPlaceholder) {
    if (answerPlaceholder.getHints().isEmpty()) {
      myHints.add(ourFirstHintText);
    }
    else {
      myHints.addAll(answerPlaceholder.getHints());
    }

    myPlaceholderTextArea.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myHintsPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    ((GridLayoutManager)myHintsPanel.getLayout()).setHGap(1);

    myHintTextArea.setFont(myPlaceholderTextArea.getFont());
    myHintTextArea.addFocusListener(createFocusListenerToSetDefaultHintText());

    actionsPanel.add(createHintToolbarComponent(), BorderLayout.WEST);
    showHint(myHints.get(myShownHintNumber));
  }

  @NotNull
  private FocusAdapter createFocusListenerToSetDefaultHintText() {
    return new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myHintTextArea.getText().equals(ourFirstHintText)) {
          myHintTextArea.setForeground(UIUtil.getActiveTextColor());
          myHintTextArea.setText("");
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (myShownHintNumber == 0 && myHintTextArea.getText().isEmpty()) {
          myHintTextArea.setForeground(UIUtil.getInactiveTextColor());
          myHintTextArea.setText(ourFirstHintText);
        }
      }
    };
  }

  private JComponent createHintToolbarComponent() {
    final DefaultActionGroup addRemoveGroup = new DefaultActionGroup();
    addRemoveGroup.addAll(new AddHint(), new RemoveHint(), new ShowNext(), new ShowPrevious());
    return ActionManager.getInstance().createActionToolbar("Hint", addRemoveGroup, false).getComponent();
  }

  private void updateHintNumberLabel() {
    if (myHints.size() > 1) {
      final String color = String.valueOf(ColorUtil.toHex(UIUtil.getHeaderInactiveColor()));
      myHintLabel.setText(UIUtil.toHtml("Hint" + " <font color=\"" + color + "\">(" + (myShownHintNumber + 1) + "/" + myHints.size() + ")</font>:"));
    }
    else {
      myHintLabel.setText("Hint: ");
    }
  }

  public void showAnswerPlaceholderText(String answerPlaceholderText) {
    myPlaceholderTextArea.setText(answerPlaceholderText);
  }

  public void showHint(String hintText) {
    if (myHints.get(myShownHintNumber).equals(ourFirstHintText)) {
      myHintTextArea.setForeground(UIUtil.getInactiveTextColor());
    }
    else {
      myHintTextArea.setForeground(UIUtil.getActiveTextColor());
    }

    myHintTextArea.setText(hintText);
    updateHintNumberLabel();
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

  public JPanel getMailPanel() {
    return myPanel;
  }

  private class ShowNext extends AnAction {

    public ShowNext() {
      super("Next Hint", "Next Hint", AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      showHint(myHints.get(++myShownHintNumber));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber + 1 < myHints.size());
    }
  }

  private class ShowPrevious extends AnAction {

    public ShowPrevious() {
      super("Previous Hint", "Previous Hint", AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      showHint(myHints.get(--myShownHintNumber));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber - 1 >= 0);
    }
  }

  private class AddHint extends AnAction {

    public AddHint() {
      super("Add Hint", "Add Hint", AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.add("");
      myShownHintNumber++;
      showHint("");
    }
  }

  private class RemoveHint extends AnAction {

    public RemoveHint() {
      super("Remove Hint", "Remove Hint", AllIcons.General.Remove);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.remove(myShownHintNumber);
      myShownHintNumber += myShownHintNumber < myHints.size() ? 0 : -1;
      showHint(myHints.get(myShownHintNumber));
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myHints.size() > 1);
    }
  }
}
