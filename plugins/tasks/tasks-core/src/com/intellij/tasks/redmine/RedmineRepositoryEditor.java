package com.intellij.tasks.redmine;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.impl.TaskUiUtil;
import com.intellij.tasks.redmine.model.RedmineProject;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 * @author Dennis.Ushakov
 */
public class RedmineRepositoryEditor extends BaseRepositoryEditor<RedmineRepository> {
  private ComboBox myProjectCombo;
  private JTextField myAPIKey;
  private JCheckBox myAllAssigneesCheckBox;
  private JBLabel myProjectLabel;
  private JBLabel myAPIKeyLabel;

  public RedmineRepositoryEditor(final Project project, final RedmineRepository repository, Consumer<RedmineRepository> changeListener) {
    super(project, repository, changeListener);

    myTestButton.setEnabled(myRepository.isConfigured());
    myAPIKey.setText(repository.getAPIKey());
    myAllAssigneesCheckBox.setSelected(!repository.isAssignedToMe());

    installListener(myProjectCombo);
    installListener(myAPIKey);
    installListener(myAllAssigneesCheckBox);

    toggleCredentialsVisibility();

    UIUtil.invokeLaterIfNeeded(() -> initialize());
  }

  @Override
  protected void afterTestConnection(boolean connectionSuccessful) {
    if (connectionSuccessful) {
      new FetchProjectsTask().queue();
    }
    else {
      myProjectCombo.removeAllItems();
    }
  }

  private void initialize() {
    final RedmineProject currentProject = myRepository.getCurrentProject();
    if (currentProject != null && myRepository.isConfigured()) {
      new FetchProjectsTask().queue();
    }
    else {
      myProjectCombo.removeAllItems();
    }
  }

  @Override
  public void apply() {
    super.apply();
    RedmineProjectItem selected = (RedmineProjectItem)myProjectCombo.getSelectedItem();
    myRepository.setCurrentProject(selected != null ? selected.myProject : null);
    myRepository.setAPIKey(myAPIKey.getText().trim());
    myRepository.setAssignedToMe(!myAllAssigneesCheckBox.isSelected());
    myTestButton.setEnabled(myRepository.isConfigured());
    toggleCredentialsVisibility();
  }

  private void toggleCredentialsVisibility() {
    myPasswordLabel.setVisible(myRepository.isUseHttpAuthentication());
    myPasswordText.setVisible(myRepository.isUseHttpAuthentication());

    myUsernameLabel.setVisible(myRepository.isUseHttpAuthentication());
    myUserNameText.setVisible(myRepository.isUseHttpAuthentication());

    myAPIKeyLabel.setVisible(!myRepository.isUseHttpAuthentication());
    myAPIKey.setVisible(!myRepository.isUseHttpAuthentication());
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectLabel = new JBLabel("Project:", SwingConstants.RIGHT);
    myProjectCombo = new ComboBox(300);
    //myProjectCombo.setRenderer(new TaskUiUtil.SimpleComboBoxRenderer("Set URL and password/token first"));
    myProjectCombo.setRenderer(new ListCellRendererWrapper<RedmineProjectItem>() {
      @Override
      public void customize(JList list, RedmineProjectItem value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          setText("Set URL and password/token first");
        }
        else {
          if (myProjectCombo.isPopupVisible()) {
            //if (value.myLevel == 0 && value.myProject != RedmineRepository.UNSPECIFIED_PROJECT) {
              //setFont(UIUtil.getListFont().deriveFont(Font.BOLD));
            //}
            setText(StringUtil.repeat("   ", value.myLevel) + value.myProject.getName());
          }
          else {
            // Do not indent selected project
            setText(value.myProject.getName());
          }
        }
      }
    });

    myAPIKeyLabel = new JBLabel("API Token:", SwingConstants.RIGHT);
    myAPIKey = new JPasswordField();

    myAllAssigneesCheckBox = new JBCheckBox("Include issues not assigned to me");
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myAPIKeyLabel, myAPIKey)
      .addLabeledComponent(myProjectLabel, myProjectCombo)
      .addComponentToRightColumn(myAllAssigneesCheckBox)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myProjectLabel.setAnchor(anchor);
    myAPIKeyLabel.setAnchor(anchor);
  }

  private static class RedmineProjectItem {
    public final RedmineProject myProject;
    public final int myLevel;

    public RedmineProjectItem(@NotNull RedmineProject project, int level) {
      myProject = project;
      myLevel = level;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null) return false;

      if (o instanceof RedmineProject) {
        return myProject.equals(o);
      }
      else if (o instanceof RedmineProjectItem) {
        return myProject.equals(((RedmineProjectItem)o).myProject);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return myProject.hashCode();
    }
  }

  private class FetchProjectsTask extends TaskUiUtil.ComboBoxUpdater<RedmineProjectItem> {
    private FetchProjectsTask() {
      super(RedmineRepositoryEditor.this.myProject, "Downloading Redmine projects...", myProjectCombo);
    }

    @Override
    public RedmineProjectItem getExtraItem() {
      return new RedmineProjectItem(RedmineRepository.UNSPECIFIED_PROJECT, 0);
    }

    @Nullable
    @Override
    public RedmineProjectItem getSelectedItem() {
      RedmineProject currentProject = myRepository.getCurrentProject();
      return currentProject != null ? new RedmineProjectItem(currentProject, -1) : null;
    }

    @NotNull
    @Override
    protected List<RedmineProjectItem> fetch(@NotNull ProgressIndicator indicator) throws Exception {
      // Seems that Redmine always return its project hierarchy in DFS order.
      // So it's easy to find level of each project using stack of parents.
      Stack<RedmineProject> parents = new Stack<>();
      List<RedmineProjectItem> items = new ArrayList<>();
      for (RedmineProject project : myRepository.fetchProjects()) {
        RedmineProject parentProject = project.getParent();
        if (parentProject == null) {
          items.add(new RedmineProjectItem(project, 0));
          parents.clear();
        }
        else {
          while (!parents.isEmpty() && !parents.peek().equals(parentProject)) {
            parents.pop();
          }
          items.add(new RedmineProjectItem(project, parents.size()));
        }
        parents.push(project);
      }
      return items;
    }
  }
}
