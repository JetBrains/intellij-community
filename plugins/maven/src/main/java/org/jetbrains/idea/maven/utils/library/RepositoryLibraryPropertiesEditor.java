package org.jetbrains.idea.maven.utils.library;

import com.google.common.base.Strings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.ProjectBundle;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.List;

public class RepositoryLibraryPropertiesEditor extends DialogWrapper {
  @NotNull private final Project project;
  State currentState;
  List<String> versions;
  private VersionKind versionKind;
  private RepositoryLibraryProperties initialProperties;
  private RepositoryLibraryProperties properties;
  private boolean initialDownloadSources;
  private boolean downloadSources;
  private boolean initialDownloadJavaDocs;
  private boolean downloadJavaDocs;
  private RepositoryLibraryDescription repositoryLibraryDescription;
  private ComboBox versionKindSelector;
  private ComboBox versionSelector;
  private JPanel mainPanel;
  private JButton myReloadButton;
  private JPanel versionPanel;
  private JPanel failedToLoadPanel;
  private JPanel loadingPanel;
  private JBCheckBox downloadSourcesCheckBox;
  private JBCheckBox downloadJavaDocsCheckBox;

  public RepositoryLibraryPropertiesEditor(@Nullable Project project,
                                           boolean downloadSources,
                                           boolean downloadJavaDocs,
                                           @NotNull RepositoryLibraryProperties properties) {
    super(project);
    this.downloadJavaDocs = this.initialDownloadJavaDocs = downloadJavaDocs;
    this.downloadSources = this.initialDownloadSources = downloadSources;
    this.project = project == null ? ProjectManager.getInstance().getDefaultProject() : project;
    this.initialProperties = properties;
    this.properties = new RepositoryLibraryProperties();
    this.properties.loadState(properties);
    repositoryLibraryDescription = RepositoryLibraryDescription.findDescription(properties);
    myReloadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reloadVersionsAsync();
      }
    });
    reloadVersionsAsync();
  }

  private static VersionKind getVersionKind(String version) {
    if (Strings.isNullOrEmpty(version)) {
      return VersionKind.Unselected;
    }
    else if (version.equals(RepositoryUtils.ReleaseVersionId)) {
      return VersionKind.Release;
    }
    else if (version.equals(RepositoryUtils.LatestVersionId)) {
      return VersionKind.Latest;
    }
    else {
      return VersionKind.Select;
    }
  }

  private static int getSelection(String selectedVersion, List<String> versions) {
    VersionKind versionKind = getVersionKind(selectedVersion);
    switch (versionKind) {
      case Unselected:
        return -1;
      case Release:
        return JBIterable.from(versions).takeWhile(new Condition<String>() {
          @Override
          public boolean value(String version) {
            return version.endsWith(RepositoryUtils.SnapshotVersionSuffix);
          }
        }).size();
      case Latest:
        return 0;
      case Select:
        if (versions.indexOf(selectedVersion) == -1) {
          versions.add(0, selectedVersion);
        }
        return versions.indexOf(selectedVersion);
    }
    return -1;
  }

  private void initVersionKindSelector() {
    List<String> versionKinds = Arrays.asList(
      ProjectBundle.message("maven.version.kind.selector.release"),
      ProjectBundle.message("maven.version.kind.selector.latest"),
      ProjectBundle.message("maven.version.kind.selector.select"));
    CollectionComboBoxModel<String> versionKindSelectorModel = new CollectionComboBoxModel<String>(versionKinds);
    //noinspection unchecked
    versionKindSelector.setModel(versionKindSelectorModel);
    versionKindSelector.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        VersionKind newVersionKind = getSelectedVersionKind();
        if (newVersionKind != versionKind) {
          versionKind = newVersionKind;
          versionKindChanged();
        }
      }
    });
    setSelectedVersionKind(getVersionKind(properties.getVersion()));
  }

  private void checkOkButtonState() {
    setOKActionEnabled(currentState == State.Loaded
                       && (!properties.equals(initialProperties)
                           || downloadSources != initialDownloadSources
                           || downloadJavaDocs != initialDownloadJavaDocs));
  }

  private void versionKindChanged() {
    versionSelector.setEnabled(versionKind == VersionKind.Select);
    properties.changeVersion(getSelectedVersion());
    int selection = getSelection(properties.getVersion(), versions);
    versionSelector.setSelectedIndex(selection);
    checkOkButtonState();
  }

  private VersionKind getSelectedVersionKind() {
    switch (versionKindSelector.getSelectedIndex()) {
      case 0:
        return VersionKind.Release;
      case 1:
        return VersionKind.Latest;
      case 2:
        return VersionKind.Select;
      default:
        return VersionKind.Unselected;
    }
  }

  private void setSelectedVersionKind(VersionKind versionKind) {
    versionSelector.setEnabled(versionKind == VersionKind.Select);
    switch (versionKind) {
      case Unselected:
        versionKindSelector.setSelectedIndex(-1);
        break;
      case Release:
        versionKindSelector.setSelectedItem(0);
        break;
      case Latest:
        versionKindSelector.setSelectedIndex(1);
        break;
      case Select:
        versionKindSelector.setSelectedIndex(2);
        break;
    }
  }

  private void setState(State state) {
    currentState = state;
    versionPanel.setVisible(state == State.Loaded);
    failedToLoadPanel.setVisible(state == State.FailedToLoad);
    loadingPanel.setVisible(state == State.Loading);
    checkOkButtonState();
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

  private void initVersionsPanel() {
    final int selection = getSelection(properties.getVersion(), versions);
    CollectionComboBoxModel<String> versionSelectorModel = new CollectionComboBoxModel<String>(versions);
    //noinspection unchecked
    versionSelector.setModel(versionSelectorModel);
    versionSelector.setSelectedIndex(selection);
    setState(State.Loaded);
    initVersionKindSelector();
    versionSelector.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        properties.changeVersion(getSelectedVersion());
        checkOkButtonState();
      }
    });
    downloadSourcesCheckBox.setSelected(downloadSources);
    downloadSourcesCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        downloadSources = downloadSourcesCheckBox.isSelected();
        checkOkButtonState();
      }
    });
    downloadJavaDocsCheckBox.setSelected(downloadJavaDocs);
    downloadJavaDocsCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        downloadJavaDocs = downloadJavaDocsCheckBox.isSelected();
        checkOkButtonState();
      }
    });
  }

  private void versionsLoaded(final List<String> versions) {
    this.versions = versions;
    if (versions == null || versions.isEmpty()) {
      versionsFailedToLoad();
      return;
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        initVersionsPanel();
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

  public String getSelectedVersion() {
    switch (versionKind) {
      case Unselected:
        return null;
      case Release:
        return RepositoryUtils.ReleaseVersionId;
      case Latest:
        return RepositoryUtils.LatestVersionId;
      case Select:
        return (String)versionSelector.getSelectedItem();
    }
    return null;
  }

  public RepositoryLibraryProperties getProperties() {
    return properties;
  }

  public boolean downloadSources() {
    return downloadSources;
  }

  public boolean downloadJavaDocs() {
    return downloadJavaDocs;
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

  private enum VersionKind {
    Unselected,
    Release,
    Latest,
    Select
  }

  private enum State {
    Loading,
    FailedToLoad,
    Loaded
  }
}
