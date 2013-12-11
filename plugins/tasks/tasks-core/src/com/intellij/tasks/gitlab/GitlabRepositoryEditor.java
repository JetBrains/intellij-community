package com.intellij.tasks.gitlab;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import static com.intellij.tasks.gitlab.GitlabRepository.UNSPECIFIED_PROJECT;

/**
 * @author Mikhail Golubev
 */
public class GitlabRepositoryEditor extends BaseRepositoryEditor<GitlabRepository> {
  private static final Logger LOG = Logger.getInstance(GitlabRepositoryEditor.class);

  private JBLabel myProjectLabel;
  private ComboBox myProjectComboBox;


  public GitlabRepositoryEditor(Project project, GitlabRepository repository, Consumer<GitlabRepository> changeListener) {
    super(project, repository, changeListener);
    myPasswordLabel.setText("Token:");

    // Hide unused login field
    myUsernameLabel.setVisible(false);
    myUserNameText.setVisible(false);

    myTestButton.setEnabled(myRepository.isConfigured());

    myProjectComboBox.setRenderer(new ListCellRendererWrapper<GitlabProject>() {
      @Override
      public void customize(JList list, GitlabProject project, int index, boolean selected, boolean hasFocus) {
        setText(project == null ? "Set server URL and token first" : project.getName());
      }
    });

    // Listeners
    myPasswordText.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myPasswordText.getPassword().length != 0) {
          new FetchProjectsTask().queue();
        }
      }
    });
    installListener(myProjectComboBox);
    myProjectComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          myRepository.setCurrentProject((GitlabProject)e.getItem());
        }
      }
    });
    // Combo box initialization
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
      new FetchProjectsTask() {
        @Override
        public void updateUI() {
          super.updateUI();
          for (int i = 0; i < myProjectComboBox.getItemCount(); i++) {
            GitlabProject project = (GitlabProject)myProjectComboBox.getItemAt(i);
            if (project.getId() == currentProject.getId()) {
              // update project with full info
              myRepository.setCurrentProject(project);
              myProjectComboBox.setSelectedItem(project);
              return;
            }
          }
          myRepository.setCurrentProject(UNSPECIFIED_PROJECT);
        }
      }.queue();
    }
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectLabel = new JBLabel("Project:", SwingConstants.RIGHT);
    myProjectComboBox = new ComboBox(300);
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
    myTestButton.setEnabled(myRepository.isConfigured());
  }

  private class FetchProjectsTask extends Task.Modal {
    @NonNls public static final String TITLE = "Downloading Gitlab projects...";
    private List<GitlabProject> myProjects;

    private FetchProjectsTask() {
      //super(GitlabRepositoryEditor.this.myProject, TITLE);
      super(GitlabRepositoryEditor.this.myProject, TITLE, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        myProjects = myRepository.fetchProjects();
      }
      catch (Exception e) {
        LOG.warn(e);
        myProjectComboBox.removeAllItems();
      }
    }

    @Nullable
    @Override
    public final NotificationInfo notifyFinished() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          updateUI();
        }
      }, ModalityState.current());
      return null;
    }

    @SuppressWarnings("unchecked")
    protected void updateUI() {
      if (myProjects != null) {
        myProjectComboBox.setModel(new DefaultComboBoxModel(myProjects.toArray(new GitlabProject[myProjects.size()])));
        myProjectComboBox.insertItemAt(UNSPECIFIED_PROJECT, 0);
        myProjectComboBox.setSelectedItem(UNSPECIFIED_PROJECT);
      }
    }
  }
}
