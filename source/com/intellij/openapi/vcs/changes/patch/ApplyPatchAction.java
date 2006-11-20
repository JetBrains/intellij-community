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
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

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
              try {
                patch.apply(dialog.getBaseDirectory(), dialog.getStripLeadingDirectories());
              }
              catch (Exception ex) {
                LOG.error(ex);
                Messages.showErrorDialog(project, "Error applying patch: " + ex.getMessage(), "Apply Patch");
              }
            }
          }
        }, "apply patch", null);
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}