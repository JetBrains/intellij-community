// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.utils.MavenUtil;

import static com.intellij.ide.impl.TrustedProjects.getTrustedState;

public class UntrustedMavenProjectNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  public static final Key<EditorNotificationPanel> KEY = Key.create("UntrustedMavenProjectNotification");

  @Override
  public @NotNull Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (!MavenProjectsManager.getInstance(project).isMavenizedProject()) {
      return null;
    }
    ThreeState state = getTrustedState(project);
    if (state == ThreeState.YES) return null;

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(SyncBundle.message("maven.sync.not.trusted.description"));
    panel.createActionLabel(SyncBundle.message("maven.sync.trust.project"), () -> {
      boolean trust = MavenUtil.isProjectTrustedEnoughToImport(project, true, true);
      if (trust) {
        MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles();
      }
    }, false);
    return panel;
  }
}
