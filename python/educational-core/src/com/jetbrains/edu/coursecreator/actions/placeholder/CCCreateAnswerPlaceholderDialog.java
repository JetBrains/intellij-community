package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.ui.CCCreateAnswerPlaceholderPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CCCreateAnswerPlaceholderDialog extends DialogWrapper {

  private static final String TITLE = "Add Answer Placeholder";
  private final CCCreateAnswerPlaceholderPanel myPanel;
  private final Project myProject;

  public Project getProject() {
    return myProject;
  }

  public CCCreateAnswerPlaceholderDialog(@NotNull final Project project,
                                         String placeholderText,
                                         List<String> hints) {
    super(project, true);
    
    myProject = project;
    myPanel = new CCCreateAnswerPlaceholderPanel(placeholderText, hints);
    setTitle(TITLE);
    setResizable(false);
    init();
    initValidation();
  }

  public String getTaskText() {
    return StringUtil.notNullize(myPanel.getAnswerPlaceholderText());
  }

  public List<String> getHints() {
    return myPanel.getHints();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getMailPanel();
  }

  @Nullable
  @Override
  public ValidationInfo doValidate() {
    return null;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }
}
