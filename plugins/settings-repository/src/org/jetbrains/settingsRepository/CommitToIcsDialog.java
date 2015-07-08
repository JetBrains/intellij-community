package org.jetbrains.settingsRepository;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class CommitToIcsDialog extends DialogWrapper {
  private final ChangesBrowser browser;
  private final Project project;
  private final String projectId;

  public CommitToIcsDialog(Project project, String projectId, List<Change> projectFileChanges) {
    super(project, true);

    this.project = project;
    this.projectId = projectId;

    browser = new ChangesBrowser(project, Collections.<ChangeList>emptyList(), projectFileChanges, null, true, false, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    browser.setChangesToDisplay(projectFileChanges);

    setTitle(IcsBundle.message("action.CommitToIcs.text"));
    setOKButtonText(IcsBundle.message("action.CommitToIcs.text"));
    init();
  }

  @Override
  protected void doOKAction() {
    List<Change> selectedChanges = browser.getSelectedChanges();
    if (!selectedChanges.isEmpty()) {
      commitChanges(selectedChanges);
    }

    super.doOKAction();
  }

  private void commitChanges(List<Change> changes) {
    StateStorageManager storageManager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    TrackingPathMacroSubstitutor macroSubstitutor = storageManager.getMacroSubstitutor();
    assert macroSubstitutor != null;
    IcsManager icsManager = SettingsRepositoryPackage.getIcsManager();

    SmartList<String> addToIcs = new SmartList<String>();
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      assert file != null;
      String fileSpec = macroSubstitutor.collapsePath(file.getPath());
      String repoPath = SettingsRepositoryPackage.buildPath(fileSpec, RoamingType.PER_USER, projectId);
      addToIcs.add(repoPath);
      if (!icsManager.getRepositoryManager().has(repoPath)) {
        // new, revert local
        // todo
      }
    }
    icsManager.getRepositoryManager().commit(addToIcs);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return browser;
  }
}
