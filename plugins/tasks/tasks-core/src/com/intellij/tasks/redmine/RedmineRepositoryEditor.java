package com.intellij.tasks.redmine;

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
public class RedmineRepositoryEditor extends BaseRepositoryEditor<RedmineRepository> {
  private JTextField myProjectId;
  private JTextField myAPIKey;
  private JBLabel myProjectIDLabel;
  private JBLabel myAPIKeyLabel;

  public RedmineRepositoryEditor(final Project project, final RedmineRepository repository, Consumer<RedmineRepository> changeListener) {
    super(project, repository, changeListener);
    myProjectId.setText(repository.getProjectId());
    myAPIKey.setText(repository.getAPIKey());
  }

  @Override
  public void apply() {
    myRepository.setProjectId(myProjectId.getText().trim());
    myRepository.setAPIKey(myAPIKey.getText().trim());
    super.apply();
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
