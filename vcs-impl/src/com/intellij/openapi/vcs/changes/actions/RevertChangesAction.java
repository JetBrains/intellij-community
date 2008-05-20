package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class RevertChangesAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN);
    final List<Change> changesList = new ArrayList<Change>();
    Collections.addAll(changesList, changes);

    String defaultName = null;
    final ChangeList[] changeLists = e.getData(VcsDataKeys.CHANGE_LISTS);
    if (changeLists != null && changeLists.length > 0) {
      defaultName = VcsBundle.message("revert.changes.default.name", changeLists[0].getName());
    }

    ChangeListChooser chooser = new ChangeListChooser(project, ChangeListManager.getInstance(project).getChangeLists(), null,
                                                      "Select Target Changelist", defaultName);
    chooser.show();
    if (!chooser.isOK()) return;

    final IndirectlyModifiedPathsGetterImpl indirectlyModifiedPathsGetter = new IndirectlyModifiedPathsGetterImpl(project, changesList);
    List<FilePatch> patches;
    try {
      patches = PatchBuilder.buildPatch(changesList, baseDir.getPresentableUrl(), false, true);
    }
    catch (VcsException ex) {
      Messages.showErrorDialog(project, "Failed to revert changes: " + ex.getMessage(), VcsBundle.message("revert.changes.title"));
      return;
    }
    ApplyPatchContext context = new ApplyPatchContext(baseDir, 0, true, false);

    ApplyPatchAction.applyPatch(project, patches, context, chooser.getSelectedList(), indirectlyModifiedPathsGetter);
  }

  /**
   * Thing specific to sequential patch creation and immediate apply: affected directories can be taken
   * from change lists used for patch creation and taken into account when moving stuff to selected CL
   *
   * seems not applicable to usual use with apply-patch 
   */
  private static class IndirectlyModifiedPathsGetterImpl implements ApplyPatchAction.IndirectlyModifiedPathsGetter {
    private final List<Change> myChangesList;
    private final Set<VirtualFile> existedDirs;

    private IndirectlyModifiedPathsGetterImpl(final Project project, final List<Change> changesList) {
      myChangesList = changesList;

      // already modified before patch creation directories won't be moved
      final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
      final List<VirtualFile> paths = changeListManager.getAffectedFiles();
      existedDirs = new HashSet<VirtualFile>();
      for (VirtualFile vf : paths) {
        if (vf.isDirectory()) {
          existedDirs.add(vf);
        }
      }
    }

    public void appendPaths(final ChangeListManager changeListManager, final List<Change> changes) {
      final Collection<File> skippedDirectories = new ArrayList<File>();

      for (Change c : myChangesList) {
        final ContentRevision beforeRevision = c.getBeforeRevision();
        final ContentRevision afterRevision = c.getAfterRevision();

        if (beforeRevision != null && beforeRevision.getFile().isDirectory()) {
          skippedDirectories.add(beforeRevision.getFile().getIOFile());
        } else if (afterRevision != null && afterRevision.getFile().isDirectory()) {
          skippedDirectories.add(afterRevision.getFile().getIOFile());
        }
      }

      final List<VirtualFile> paths = changeListManager.getAffectedFiles();

      for (VirtualFile virtualFile : paths) {
        if (virtualFile.isDirectory() && (! existedDirs.contains(virtualFile)) &&
            (skippedDirectories.contains(new File(virtualFile.getPath())))) {
          final Change change = changeListManager.getChange(virtualFile);
          if (change != null) {
            changes.add(change);
          }
        }
      }
    }
  }

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length > 0);
  }
}
