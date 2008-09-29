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
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider");

  private final Project myProject;
  private final VirtualFile myFile;
  private final String myVersionId;
  private final Pattern myRevisionPattern;

  private AbstractVcs myVcs;

  public DefaultPatchBaseVersionProvider(final Project project, final VirtualFile file, final String versionId) {
    myProject = project;
    myFile = file;
    myVersionId = versionId;
    myVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(myFile);
    if (myVcs != null) {
      final String vcsPattern = myVcs.getRevisionPattern();
      if (vcsPattern != null) {
        myRevisionPattern = Pattern.compile("\\(revision (" + vcsPattern + ")\\)");
        return;
      }
    }
    myRevisionPattern = null;
  }

  public void getBaseVersionContent(FilePath filePath, Processor<CharSequence> processor) throws VcsException {
    if (myVcs == null) {
      return;
    }

    VcsRevisionNumber revision = null;
    if (myRevisionPattern != null) {
      final Matcher matcher = myRevisionPattern.matcher(myVersionId);
      if (matcher.find()) {
        revision = myVcs.parseRevisionNumber(matcher.group(1));
      }
    }

    Date versionDate = null;
    if (revision == null) {
      try {
        versionDate = new Date(myVersionId);
      }
      catch(IllegalArgumentException ex) {
        return;
      }
    }
    try {
      final VcsHistorySession session = myVcs.getVcsHistoryProvider().createSessionFor(filePath);
      if (session == null) return;
      final List<VcsFileRevision> list = session.getRevisionList();
      if (list == null) return;
      for(VcsFileRevision fileRevision: list) {
        boolean found;
        if (revision != null) {
          found = fileRevision.getRevisionNumber().compareTo(revision) <= 0;
        }
        else {
          found = fileRevision.getRevisionDate().before(versionDate);
        }

        if (found) {
          fileRevision.loadContent();
          processor.process(LoadTextUtil.getTextByBinaryPresentation(fileRevision.getContent(), myFile, false));
          // TODO: try to download more than one version
          break;
        }
      }
    }
    catch(IOException e) {
      LOG.error(e);
    }
  }

  public boolean canProvideContent() {
    if (myVcs == null) {
      return false;
    }
    if ((myRevisionPattern != null) && myRevisionPattern.matcher(myVersionId).matches()) {
      return true;
    }
    try {
      Date.parse(myVersionId);
    }
    catch(IllegalArgumentException ex) {
      return false;
    }
    return true;
  }
}