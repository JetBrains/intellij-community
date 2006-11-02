/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:08:21
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

import java.util.List;

public class ScheduleForRemovalAction extends AbstractMissingFilesAction {
  public ScheduleForRemovalAction() {
    super(VcsBundle.message("changes.action.remove.text"), VcsBundle.message("changes.action.remove.description"),
          IconLoader.getIcon("/actions/exclude.png"));
  }

  protected void processFiles(final CheckinEnvironment environment, final List<FilePath> files) {
    environment.scheduleMissingFileForDeletion(files);
  }
}