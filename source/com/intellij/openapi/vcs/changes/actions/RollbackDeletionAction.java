/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:07:51
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.AbstractMissingFilesAction;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.util.List;

public class RollbackDeletionAction extends AbstractMissingFilesAction {
  public RollbackDeletionAction() {
    super(VcsBundle.message("changes.action.rollback.deletion.text"),
          VcsBundle.message("changes.action.rollback.deletion.description"),
          IconLoader.getIcon("/actions/rollback.png"));
  }

  protected void processFiles(final CheckinEnvironment environment, final List<FilePath> files) {
    environment.rollbackMissingFileDeletion(files);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshIoFiles(ChangesUtil.filePathsToFiles(files));
      }
    });
  }
}