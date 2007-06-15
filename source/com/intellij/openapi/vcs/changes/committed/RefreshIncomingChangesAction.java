package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class RefreshIncomingChangesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      doRefresh(project);
    }
  }

  public static void doRefresh(final Project project) {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(project);
    if (!cache.hasCachesForAnyRoot() && !CacheSettingsDialog.showSettingsDialog(project)) {
      return;
    }
    cache.refreshAllCachesAsync(true);
    cache.refreshIncomingChangesAsync();
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && !CommittedChangesCache.getInstance(project).isRefreshingIncomingChanges());
  }
}