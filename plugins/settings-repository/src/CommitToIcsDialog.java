/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
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

    browser = new ChangesBrowser(project, Collections.emptyList(), projectFileChanges, null, true, false, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
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
    StateStorageManager storageManager = ServiceKt.getStateStore(project).getStateStorageManager();
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
    icsManager.getRepositoryManager().commit(addToIcs);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return browser;
  }
}
