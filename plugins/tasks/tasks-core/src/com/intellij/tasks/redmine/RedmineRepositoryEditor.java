package com.intellij.tasks.redmine;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.impl.TaskUiUtil;
import com.intellij.tasks.redmine.model.RedmineProject;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Mikhail Golubev
 * @author Dennis.Ushakov
 */
public class RedmineRepositoryEditor extends BaseRepositoryEditor<RedmineRepository> {
  private ComboBox myProjectCombo;
  private JTextField myAPIKey;
  private JBLabel myProjectLabel;
  private JBLabel myAPIKeyLabel;

  public RedmineRepositoryEditor(final Project project, final RedmineRepository repository, Consumer<RedmineRepository> changeListener) {
    super(project, repository, changeListener);

    myTestButton.setEnabled(myRepository.isConfigured());
    myAPIKey.setText(repository.getAPIKey());

    installListener(myProjectCombo);
    installListener(myAPIKey);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        initialize();
      }
    });
  }

  @Override
  protected void afterTestConnection(boolean connectionSuccessful) {
    if (connectionSuccessful) {
      new FetchProjectsTask().queue();
    }
  }

  private void initialize() {
    final RedmineProject currentProject = myRepository.getCurrentProject();
    if (currentProject != null && myRepository.isConfigured()) {
      new FetchProjectsTask().queue();
    }
  }

  @Override
  public void apply() {
    super.apply();
    myRepository.setCurrentProject((RedmineProject)myProjectCombo.getSelectedItem());
    myRepository.setAPIKey(myAPIKey.getText().trim());
    myTestButton.setEnabled(myRepository.isConfigured());
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectLabel = new JBLabel("Project:", SwingConstants.RIGHT);
    myProjectCombo = new ComboBox(300);
    myProjectCombo.setRenderer(new TaskUiUtil.SimpleComboBoxRenderer("Set URL and password/token first"));
    myAPIKeyLabel = new JBLabel("API Token:", SwingConstants.RIGHT);
    myAPIKey = new JTextField();
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myAPIKeyLabel, myAPIKey)
      .addLabeledComponent(myProjectLabel, myProjectCombo)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myProjectLabel.setAnchor(anchor);
    myAPIKeyLabel.setAnchor(anchor);
  }

  private class FetchProjectsTask extends TaskUiUtil.ComboBoxUpdater<RedmineProject> {
    private FetchProjectsTask() {
      super(RedmineRepositoryEditor.this.myProject, "Downloading Redmine projects...", myProjectCombo);
    }

    @Override
    public RedmineProject getExtraItem() {
      return RedmineRepository.UNSPECIFIED_PROJECT;
    }

    @Nullable
    @Override
    public RedmineProject getSelectedItem() {
      return myRepository.getCurrentProject();
    }

    @NotNull
    @Override
    protected List<RedmineProject> fetch(@NotNull ProgressIndicator indicator) throws Exception {
      return myRepository.fetchProjects();
    }
  }
}
