/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2006
 * Time: 17:08:11
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ApplyPatchAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final ApplyPatchDialog dialog = new ApplyPatchDialog(project);
    dialog.show();    
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    final List<FilePatch> patches = dialog.getPatches();
    for(FilePatch patch: patches) {
      VirtualFile fileToPatch = patch.findFileToPatch(dialog.getBaseDirectory(), dialog.getStripLeadingDirectories());
      if (fileToPatch != null) {
        FileType fileType = fileToPatch.getFileType();
        if (fileType == StdFileTypes.UNKNOWN) {
          fileType = FileTypeChooser.associateFileType(fileToPatch.getPresentableName());
          if (fileType == null) {
            return;
          }
        }
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            for(FilePatch patch: patches) {
              applySinglePatch(project, patch, dialog.getBaseDirectory(), dialog.getStripLeadingDirectories());
            }
          }
        }, "apply patch", null);
      }
    });
  }

  private static void applySinglePatch(final Project project, final FilePatch patch, final VirtualFile baseDirectory,
                                       final int stripLeadingDirectories) {
    VirtualFile file = patch.findFileToPatch(baseDirectory, stripLeadingDirectories);
    if (file == null) {
      Messages.showErrorDialog(project, "Cannot find file to patch: " + patch.getBeforeName(), "Apply Patch");
      return;
    }

    try {
      patch.apply(file);
    }
    catch(ApplyPatchException ex) {
      boolean appliedAnyway = false;
      if (!patch.isNewFile() && !patch.isDeletedFile()) {
        CharSequence content = findMatchingContent(project, patch, file);
        if (content != null) {
          try {
            String patchedContent = patch.applyModifications(content);
            showMergeDialog(project, patch, file, content, patchedContent);
            appliedAnyway = true;
          }
          catch (ApplyPatchException e) {
            appliedAnyway = false;
          }
        }
      }
      if (!appliedAnyway) {
        Messages.showErrorDialog(project, "Failed to apply patch because of conflicts: " + patch.getBeforeName(),
                                 VcsBundle.message("patch.apply.dialog.title"));
      }
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
  }

  private static void showMergeDialog(final Project project, final FilePatch patch, final VirtualFile file, final CharSequence content,
                                      final String patchedContent) {
    final DiffRequestFactory diffRequestFactory = PeerFactory.getInstance().getDiffRequestFactory();
    CharSequence fileContent = LoadTextUtil.loadText(file);
    final MergeRequest request = diffRequestFactory.createMergeRequest(fileContent.toString(), patchedContent, content.toString(), file,
                                                                       project, ActionButtonPresentation.createApplyButton());
    request.setVersionTitles(new String[] {
      VcsBundle.message("patch.apply.conflict.local.version"),
      VcsBundle.message("patch.apply.conflict.merged.version"),
      VcsBundle.message("patch.apply.conflict.patched.version")
    });
    request.setWindowTitle(VcsBundle.message("patch.apply.conflict.title", file.getPresentableUrl()));
    DiffManager.getInstance().getDiffTool().show(request);
  }

  @Nullable
  private static CharSequence findMatchingContent(final Project project, final FilePatch patch, final VirtualFile file) {
    final PatchBaseVersionProvider[] baseVersionProviders = project.getComponents(PatchBaseVersionProvider.class);
    for(PatchBaseVersionProvider provider: baseVersionProviders) {
      final CharSequence content = provider.getBaseVersionContent(file, patch.getBeforeVersionId());
      if (content != null) {
        return content;
      }
    }
    return null;
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}