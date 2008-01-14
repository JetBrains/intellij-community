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
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class RevertChangesAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
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

    List<FilePatch> patches;
    try {
      patches = PatchBuilder.buildPatch(changesList, baseDir.getPresentableUrl(), false, true);
    }
    catch (VcsException ex) {
      Messages.showErrorDialog(project, "Failed to revert changes: " + ex.getMessage(), VcsBundle.message("revert.changes.title"));
      return;
    }
    ApplyPatchContext context = new ApplyPatchContext(baseDir, 0, true, false);
    ApplyPatchAction.applyPatch(project, patches, context, chooser.getSelectedList());
  }
  
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length > 0);
  }
}
