package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.ProjectBundle;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class RepositoryLibraryPropertiesEditor extends DialogWrapper {
  @NotNull private final Project project;
  private RepositoryLibraryProperties properties;
  private RepositoryLibraryDescription repositoryLibraryDescription;
  private ComboBox versionSelector;
  private JBCheckBox useLatest;
  private JPanel mainPanel;
  private JButton myReloadButton;
  private JPanel versionPanel;
  private JPanel failedToLoadPanel;
  private JPanel loadingPanel;
  private JPanel versionSelectorPanel;

  public RepositoryLibraryPropertiesEditor(@Nullable Project project,
                                           RepositoryLibraryProperties properties) {
    super(project);
    this.project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    this.properties = properties;
    repositoryLibraryDescription = RepositoryLibraryDescription.findDescription(properties);
    useLatest.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        setVersionsVisibility();
      }
    });
    myReloadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reloadVersionsAsync();
      }
    });
    reloadVersionsAsync();
  }

  private void setState(State state) {
    versionPanel.setVisible(state == State.Loaded);
    failedToLoadPanel.setVisible(state == State.FailedToLoad);
    loadingPanel.setVisible(state == State.Loading);
    setOKActionEnabled(state == State.Loaded);
  }

  private void reloadVersionsAsync() {
    setState(State.Loading);
    Task task = new Task.Backgroundable(
      project,
      ProjectBundle.message("maven.loading.library.version.hint", repositoryLibraryDescription.getDisplayName()),
      false) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<String> versions = RepositoryAttachHandler.retrieveVersions(
            project,
            properties.getGroupId(),
            properties.getArtifactId(),
            repositoryLibraryDescription.getRemoteRepositories());
          versionsLoaded(versions);
        }
        catch (Exception e) {
          versionsFailedToLoad();
        }
      }
    };
    ProgressManager.getInstance().run(task);
  }

  private void versionsLoaded(final List<String> versions) {
    if (versions == null || versions.isEmpty()) {
      versionsFailedToLoad();
      return;
    }
    String selectedVersion = getSelectedVersion();
    if (!selectedVersion.equals(RepositoryUtils.LatestVersionId)
        && versions.indexOf(selectedVersion) == -1) {
      versions.add(0, selectedVersion);
    }
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        CollectionComboBoxModel<String> versionSelectorModel = new CollectionComboBoxModel<String>(versions);
        //noinspection unchecked
        versionSelector.setModel(versionSelectorModel);
        if (properties.getVersion().equals(RepositoryUtils.LatestVersionId)) {
          useLatest.setSelected(true);
        }
        else {
          useLatest.setSelected(false);
          versionSelector.setSelectedItem(properties.getVersion());
        }
        setVersionsVisibility();
        setState(State.Loaded);
      }
    });
  }

  private void versionsFailedToLoad() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        setState(State.FailedToLoad);
      }
    });
  }

  private void setVersionsVisibility() {
    if (getSelectedVersion().equals(RepositoryUtils.LatestVersionId)) {
      useLatest.setSelected(true);
      versionSelectorPanel.setVisible(false);
    }
    else {
      useLatest.setSelected(false);
      versionSelectorPanel.setVisible(true);
      versionSelector.setSelectedItem(getSelectedVersion());
    }
  }

  public String getSelectedVersion() {
    return useLatest.isSelected() || versionSelector.getSelectedItem() == null
           ? RepositoryUtils.LatestVersionId
           : (String)versionSelector.getSelectedItem();
  }

  public RepositoryLibraryProperties getProperties() {
    return new RepositoryLibraryProperties(
      repositoryLibraryDescription.getGroupId(),
      repositoryLibraryDescription.getArtifactId(),
      getSelectedVersion());
  }

  public JPanel getMainPanel() {
    return mainPanel;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return mainPanel;
  }

  @Override
  public void init() {
    super.init();
  }

  private enum State {
    Loading,
    FailedToLoad,
    Loaded
  }
}
