package com.intellij.tasks.gitlab;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.impl.TaskUiUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.tasks.gitlab.GitlabRepository.UNSPECIFIED_PROJECT;

/**
 * @author Mikhail Golubev
 */
public class GitlabRepositoryEditor extends BaseRepositoryEditor<GitlabRepository> {

  private JBLabel myProjectLabel;
  private ComboBox myProjectComboBox;


  public GitlabRepositoryEditor(Project project, GitlabRepository repository, Consumer<GitlabRepository> changeListener) {
    super(project, repository, changeListener);
    myPasswordLabel.setText("Token:");

    // Hide unused login field
    myUsernameLabel.setVisible(false);
    myUserNameText.setVisible(false);

    myTestButton.setEnabled(myRepository.isConfigured());

    installListener(myProjectComboBox);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        initialize();
      }
    });
  }

  private void initialize() {
    final GitlabProject currentProject = myRepository.getCurrentProject();
    if (currentProject != null && myRepository.isConfigured()) {
      new FetchProjectsTask().queue();
    }
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectLabel = new JBLabel("Project:", SwingConstants.RIGHT);
    myProjectComboBox = new ComboBox(300);
    myProjectComboBox.setRenderer(new TaskUiUtil.SimpleComboBoxRenderer("Set server URL and token first"));
    myProjectLabel.setLabelFor(myProjectComboBox);
    return new FormBuilder().addLabeledComponent(myProjectLabel, myProjectComboBox).getPanel();
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myProjectLabel.setAnchor(anchor);
  }

  @Override
  protected void afterTestConnection(boolean connectionSuccessful) {
    if (connectionSuccessful) {
      new FetchProjectsTask().queue();
    }
  }

  @Override
  public void apply() {
    super.apply();
    myRepository.setCurrentProject((GitlabProject)myProjectComboBox.getSelectedItem());
    myTestButton.setEnabled(myRepository.isConfigured());
  }

  private class FetchProjectsTask extends TaskUiUtil.ComboBoxUpdater<GitlabProject> {
    private FetchProjectsTask() {
      super(GitlabRepositoryEditor.this.myProject, "Downloading Gitlab projects...", myProjectComboBox);
    }

    @Override
    public GitlabProject getExtraItem() {
      return UNSPECIFIED_PROJECT;
    }

    @Nullable
    @Override
    public GitlabProject getSelectedItem() {
      return myRepository.getCurrentProject();
    }

    @NotNull
    @Override
    protected List<GitlabProject> fetch(@NotNull ProgressIndicator indicator) throws Exception {
      return myRepository.fetchProjects();
    }
  }
}
