package com.intellij.tasks.pivotal;

import com.intellij.openapi.project.Project;
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
  private JTextField myAPIKey;
  private JBLabel myProjectIDLabel;
  private JBLabel myAPIKeyLabel;

  public PivotalTrackerRepositoryEditor(final Project project,
                                    final PivotalTrackerRepository repository,
                                    Consumer<PivotalTrackerRepository> changeListener) {
    super(project, repository, changeListener);
    myUserNameText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myPasswordText.setVisible(false);
    myPasswordLabel.setVisible(false);

    myProjectId.setText(repository.getProjectId());
    myAPIKey.setText(repository.getAPIKey());
    myUseHttpAuthenticationCheckBox.setVisible(false);
  }

  @Override
  public void apply() {
    super.apply();
    myRepository.setProjectId(myProjectId.getText().trim());
    myRepository.setAPIKey(myAPIKey.getText().trim());
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectIDLabel = new JBLabel("Project ID:", SwingConstants.RIGHT);
    myProjectId = new JTextField();
    installListener(myProjectId);
    myAPIKeyLabel = new JBLabel("API Token:", SwingConstants.RIGHT);
    myAPIKey = new JTextField();
    installListener(myAPIKey);
    return FormBuilder.createFormBuilder().addLabeledComponent(myProjectIDLabel, myProjectId).addLabeledComponent(myAPIKeyLabel, myAPIKey)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myProjectIDLabel.setAnchor(anchor);
    myAPIKeyLabel.setAnchor(anchor);
  }
}
