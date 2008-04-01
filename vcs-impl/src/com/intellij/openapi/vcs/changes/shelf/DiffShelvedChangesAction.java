package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.patch.PatchMergeRequestFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class DiffShelvedChangesAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    List<ShelvedChange> shelvedChanges = e.getData(ShelvedChangesViewManager.SHELVED_CHANGE_KEY);
    if ((shelvedChanges == null || shelvedChanges.isEmpty()) && changeLists != null && changeLists.length > 0) {
      shelvedChanges = changeLists [0].getChanges();
    }
    if (shelvedChanges != null && shelvedChanges.size() > 0) {
      ShelvedChange c = shelvedChanges.get(0);
      Change change = c.getChange(project);
      if (isConflictingChange(change)) {
        try {
          if (showConflictingChangeDiff(project, c)) {
            return;
          }
        }
        catch (Exception ex) {
          // ignore and fallback to regular diff
        }
      }
    }
    ActionManager.getInstance().getAction("ChangesView.Diff").actionPerformed(e);
  }

  private static boolean showConflictingChangeDiff(final Project project, final ShelvedChange c) throws PatchSyntaxException, IOException {
    TextFilePatch patch = c.loadFilePatch();
    if (patch == null) return false;

    ApplyPatchContext context = new ApplyPatchContext(project.getBaseDir(), 0, true, true);
    VirtualFile f = patch.findFileToPatch(context);
    if (f == null) return false;

    return ApplyPatchAction.mergeAgainstBaseVersion(project, f, context, patch, ShelvedChangeDiffRequestFactory.INSTANCE) != null;
  }

  private static boolean isConflictingChange(final Change change) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision == null) return false;
    try {
      afterRevision.getContent();
    }
    catch(VcsException e) {
      if (e.getCause() instanceof ApplyPatchException) {
        return true;
      }
    }
    return false;
  }

  public void update(final AnActionEvent e) {
    ActionManager.getInstance().getAction("ChangesView.Diff").update(e);
  }

  private static class ShelvedChangeDiffRequestFactory implements PatchMergeRequestFactory {
    public static final ShelvedChangeDiffRequestFactory INSTANCE = new ShelvedChangeDiffRequestFactory();

    public MergeRequest createMergeRequest(final String leftText, final String rightText, final String originalContent, @NotNull final VirtualFile file,
                                           final Project project) {
      MergeRequest request = DiffRequestFactory.getInstance().create3WayDiffRequest(leftText, rightText, originalContent,
                                                                                    project,
                                                                                    null);
      request.setVersionTitles(new String[] {
        "Current Version",
        "Base Version",
        "Shelved Version"
      });
      request.setWindowTitle("Shelved Change Conflict for" + file.getPresentableUrl());
      return request;
    }
  }
}
