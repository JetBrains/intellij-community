package com.intellij.tasks.pivotal;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class PivotalTrackerRepositoryEditor extends BaseRepositoryEditor<PivotalTrackerRepository> {
  private JTextField myProjectId;
  private JBLabel myProjectIDLabel;

  public PivotalTrackerRepositoryEditor(final Project project,
                                        final PivotalTrackerRepository repository,
                                        Consumer<? super PivotalTrackerRepository> changeListener) {
    super(project, repository, changeListener);
    myUserNameText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myPasswordLabel.setText(TaskBundle.message("label.api.token"));
    myProjectId.setText(repository.getProjectId());
    myUseHttpAuthenticationCheckBox.setVisible(false);
  }

  @Override
  public void apply() {
    super.apply();
    myRepository.setProjectId(myProjectId.getText().trim());
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectIDLabel = new JBLabel(TaskBundle.message("label.project.id"), SwingConstants.RIGHT);
    myProjectId = new JTextField();
    installListener(myProjectId);
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myProjectIDLabel, myProjectId)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myProjectIDLabel.setAnchor(anchor);
  }
}
