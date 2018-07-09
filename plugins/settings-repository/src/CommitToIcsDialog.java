// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository;

import com.intellij.configurationStore.StateStorageManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CommitToIcsDialog extends DialogWrapper {
  private final SimpleChangesBrowser browser;
  private final Project project;
  private final String projectId;

  public CommitToIcsDialog(Project project, String projectId, List<Change> projectFileChanges) {
    super(project, true);

    this.project = project;
    this.projectId = projectId;

    browser = new SimpleChangesBrowser(project, true, false);
    browser.setIncludedChanges(projectFileChanges);
    browser.setChangesToDisplay(projectFileChanges);

    setTitle(IcsBundleKt.icsMessage("action.CommitToIcs.text"));
    setOKButtonText(IcsBundleKt.icsMessage("action.CommitToIcs.text"));
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
    StateStorageManager storageManager = ServiceKt.getStateStore(project).getStorageManager();
    TrackingPathMacroSubstitutor macroSubstitutor = storageManager.getMacroSubstitutor();
    assert macroSubstitutor != null;
    IcsManager icsManager = IcsManagerKt.getIcsManager();

    SmartList<String> addToIcs = new SmartList<>();
    for (Change change : changes) {
      VirtualFile file = change.getVirtualFile();
      assert file != null;
      String fileSpec = macroSubstitutor.collapsePath(file.getPath());
      String repoPath = IcsUrlBuilderKt.toRepositoryPath(fileSpec, RoamingType.DEFAULT, projectId);
      addToIcs.add(repoPath);
      if (!icsManager.getRepositoryManager().has(repoPath)) {
        // new, revert local
        // todo
      }
    }
    //icsManager.getRepositoryManager().commit(addToIcs);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return browser;
  }
}
