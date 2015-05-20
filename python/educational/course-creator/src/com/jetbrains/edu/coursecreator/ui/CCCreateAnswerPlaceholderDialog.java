package com.jetbrains.edu.coursecreator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    if (answerPlaceholder.getHint() != null) {
      setHintText(answerPlaceholder);
    }
    myProject = project;
    String answerPlaceholderTaskText = answerPlaceholder.getTaskText();
    myPanel.setAnswerPlaceholderText(answerPlaceholderTaskText != null ? answerPlaceholderTaskText : "");
    String hintName = answerPlaceholder.getHint();
    myPanel.setHintText(hintName != null ? hintName : "");
    init();
    initValidation();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void setHintText(AnswerPlaceholder answerPlaceholder) {
    String hintText = answerPlaceholder.getHint();

    myPanel.doClick();
    myPanel.setHintText(hintText);
  }

  @Override
  protected void doOKAction() {
    String answerPlaceholderText = myPanel.getAnswerPlaceholderText();
    myAnswerPlaceholder.setTaskText(StringUtil.notNullize(answerPlaceholderText));
    myAnswerPlaceholder.setLength(StringUtil.notNullize(answerPlaceholderText).length());
    myAnswerPlaceholder.setHint(myPanel.getHintText());
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
    return myAnswerPlaceholder.getHint() != null ? null : new ValidationInfo("Type hint");
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }
}
