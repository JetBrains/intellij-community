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

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

import java.util.Collections;
import java.util.List;

public class ScheduleForRemovalAction extends AbstractMissingFilesAction {
  protected List<VcsException> processFiles(final AbstractVcs vcs, final List<FilePath> files) {
    CheckinEnvironment environment = vcs.getCheckinEnvironment();
    if (environment == null) return Collections.emptyList();
    return environment.scheduleMissingFileForDeletion(files);
  }
}