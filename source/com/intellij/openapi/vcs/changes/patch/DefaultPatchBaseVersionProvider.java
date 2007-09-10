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
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultPatchBaseVersionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider");

  private Project myProject;

  @NonNls private static final Pattern ourRevisionPattern = Pattern.compile("\\(revision (\\d+)\\)");

  public DefaultPatchBaseVersionProvider(final Project project) {
    myProject = project;
  }

  public void getBaseVersionContent(VirtualFile virtualFile, FilePath filePath, String versionId, Processor<CharSequence> processor) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(filePath);
    if (vcs == null) {
      return;
    }

    VcsRevisionNumber revision = null;
    final Matcher matcher = ourRevisionPattern.matcher(versionId);
    if (matcher.find()) {
      revision = vcs.parseRevisionNumber(matcher.group(1));
    }

    Date versionDate = null;
    if (revision == null) {
      try {
        versionDate = new Date(versionId);
      }
      catch(IllegalArgumentException ex) {
        return;
      }
    }
    try {
      final VcsHistorySession session = vcs.getVcsHistoryProvider().createSessionFor(filePath);
      if (session == null) return;
      final List<VcsFileRevision> list = session.getRevisionList();
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
          processor.process(LoadTextUtil.getTextByBinaryPresentation(fileRevision.getContent(), virtualFile, false));
          // TODO: try to download more than one version
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
    if (ourRevisionPattern.matcher(versionId).matches()) {
      return true;
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