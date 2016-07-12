package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class CCCreateAnswerPlaceholderDialog extends DialogWrapper {

  private static final String ourTitle = "Add Answer Placeholder";
  private final AnswerPlaceholder myAnswerPlaceholder;
  private final CCCreateAnswerPlaceholderPanel myPanel;
  private final Project myProject;

  public Project getProject() {
    return myProject;
  }

  public CCCreateAnswerPlaceholderDialog(@NotNull final Project project,
                                         @NotNull final AnswerPlaceholder answerPlaceholder) {
    super(project, true);
    setTitle(ourTitle);
    myAnswerPlaceholder = answerPlaceholder;
    myPanel = new CCCreateAnswerPlaceholderPanel();
    if (!answerPlaceholder.getHints().isEmpty()) {
      setHintText(answerPlaceholder);
    }
    myProject = project;
    String answerPlaceholderTaskText = answerPlaceholder.getTaskText();
    myPanel.setAnswerPlaceholderText(answerPlaceholderTaskText != null ? answerPlaceholderTaskText : "");
    init();
    initValidation();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void setHintText(AnswerPlaceholder answerPlaceholder) {
    List<String> hintTexts = answerPlaceholder.getHints();
    myPanel.setHintText(hintTexts.get(0));
    myPanel.setHints(hintTexts);
  }

  @Override
  protected void doOKAction() {
    String answerPlaceholderText = myPanel.getAnswerPlaceholderText();
    myAnswerPlaceholder.setTaskText(StringUtil.notNullize(answerPlaceholderText));
    myAnswerPlaceholder.setLength(StringUtil.notNullize(answerPlaceholderText).length());
    final List<String> hints = myPanel.getHints();
    if (hints.size() == 1 && hints.get(0).isEmpty()) {
      myAnswerPlaceholder.setHints(Collections.emptyList());
    }
    else {
      myAnswerPlaceholder.setHints(hints);
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public ValidationInfo doValidate() {
    return !myPanel.getHints().isEmpty() ? null : new ValidationInfo("Type hint");
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }
}
