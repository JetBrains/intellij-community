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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

public class CCCreateAnswerPlaceholderPanel {
  private static final String NEXT_HINT = "Next Hint";
  private static final String PREVIOUS_HINT = "Previous Hint";
  private static final String ADD_HINT = "Add Hint";
  private static final String REMOVE_HINT = "Remove Hint";
  private static final String HINT_PLACEHOLDER = "Type here to add hint";

  private JPanel myPanel;
  private JTextArea myHintTextArea;
  private JPanel myHintsPanel;
  private JBLabel myHintLabel;
  private JPanel actionsPanel;
  private JTextArea myPlaceholderTextArea;
  private List<String> myHints = new ArrayList<>();
  private int myShownHintNumber = 0;

  public CCCreateAnswerPlaceholderPanel(@Nullable String placeholderText, @NotNull List<String> hints) {
    if (hints.isEmpty()) {
      myHints.add(HINT_PLACEHOLDER);
    }
    else {
      myHints.addAll(hints);
    }

    myPlaceholderTextArea.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myPlaceholderTextArea.setText(placeholderText);
    myPlaceholderTextArea.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myPlaceholderTextArea.selectAll();
      }
    });
    myPlaceholderTextArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
    myPlaceholderTextArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
    
    myHintsPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    ((GridLayoutManager)myHintsPanel.getLayout()).setHGap(1);

    myHintTextArea.setFont(UIUtil.getLabelFont());
    myPlaceholderTextArea.setFont(UIUtil.getLabelFont());
    myHintTextArea.addFocusListener(createFocusListenerToSetDefaultHintText());

    actionsPanel.add(createHintToolbarComponent(), BorderLayout.WEST);
    showHint();
  }

  @NotNull
  private FocusAdapter createFocusListenerToSetDefaultHintText() {
    return new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myHintTextArea.getText().equals(HINT_PLACEHOLDER)) {
          myHintTextArea.setForeground(UIUtil.getActiveTextColor());
          myHintTextArea.setText("");
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (myShownHintNumber == 0 && myHintTextArea.getText().isEmpty()) {
          myHintTextArea.setForeground(UIUtil.getInactiveTextColor());
          myHintTextArea.setText(HINT_PLACEHOLDER);
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

  public void showHint() {
    String hintText = myHints.get(myShownHintNumber);
    if (hintText.equals(HINT_PLACEHOLDER)) {
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
    if (myShownHintNumber == 0 && hintText.equals(HINT_PLACEHOLDER)) {
      myHints.remove(0);
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
      super(NEXT_HINT, NEXT_HINT, AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      myShownHintNumber++;
      showHint();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber + 1 < myHints.size());
    }
  }

  private class ShowPrevious extends AnAction {

    public ShowPrevious() {
      super(PREVIOUS_HINT, PREVIOUS_HINT, AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      myShownHintNumber--;
      showHint();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myShownHintNumber - 1 >= 0);
    }
  }

  private class AddHint extends AnAction {

    public AddHint() {
      super(ADD_HINT, ADD_HINT, AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.set(myShownHintNumber, myHintTextArea.getText());
      myHints.add(++myShownHintNumber, "");
      showHint();
    }
  }

  private class RemoveHint extends AnAction {

    public RemoveHint() {
      super(REMOVE_HINT, REMOVE_HINT, AllIcons.General.Remove);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myHints.remove(myShownHintNumber);
      if (myHints.isEmpty()) {
        myHints.add(HINT_PLACEHOLDER);
        myHintTextArea.transferFocus();
        showHint();
      }
      else {
        myShownHintNumber += myShownHintNumber < myHints.size() ? 0 : -1;
        showHint();
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final boolean hasOnlyMockHint = myShownHintNumber == 0 && myHints.get(myShownHintNumber).equals(HINT_PLACEHOLDER);
      final boolean hasMoreHints = myHints.size() > 0 && !hasOnlyMockHint;
      e.getPresentation().setEnabled(hasMoreHints);
    }
  }
}
