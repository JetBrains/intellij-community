/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 21.11.2006
 * Time: 18:38:44
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.util.Processor;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class DefaultPatchBaseVersionProvider implements PatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider");

  private Project myProject;

  public DefaultPatchBaseVersionProvider(final Project project) {
    myProject = project;
  }

  public void getBaseVersionContent(VirtualFile virtualFile, String versionId, Processor<CharSequence> processor) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    if (vcs == null) {
      return;
    }
    Date versionDate;
    try {
      versionDate = new Date(versionId);
    }
    catch(IllegalArgumentException ex) {
      return;
    }
    final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(virtualFile);
    try {
      final VcsHistorySession session = vcs.getVcsHistoryProvider().createSessionFor(filePath);
      final List<VcsFileRevision> list = session.getRevisionList();
      for(VcsFileRevision fileRevision: list) {
        if (fileRevision.getRevisionDate().before(versionDate)) {
          fileRevision.loadContent();
          final CharSequence content = LoadTextUtil.getTextByBinaryPresentation(fileRevision.getContent(), virtualFile, false);
          // TODO: try to download more than one version
          processor.process(content);
          break;
        }
      }
    }
    catch(IOException e) {
      LOG.error(e);
    }
    catch (VcsException e) {
      LOG.error(e);
    }
  }

  public boolean canProvideContent(VirtualFile virtualFile, String versionId) {
    if (ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile) == null) {
      return false;
    }
    try {
      Date.parse(versionId);
    }
    catch(IllegalArgumentException ex) {
      return false;
    }
    return true;
  }
}