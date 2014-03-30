/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author lesya
 * @author yole
 */
public class SvnMergeProvider implements MergeProvider {

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.actions.SvnMergeProvider");

  public SvnMergeProvider(final Project project) {
    myProject = project;
  }

  @NotNull
  public MergeData loadRevisions(final VirtualFile file) throws VcsException {
    final MergeData data = new MergeData();
    VcsRunnable runnable = new VcsRunnable() {
      public void run() throws VcsException {
        File oldFile = null;
        File newFile = null;
        File workingFile = null;
        boolean mergeCase = false;
        SvnVcs vcs = SvnVcs.getInstance(myProject);
        SVNInfo info = vcs.getInfo(file);

        if (info != null) {
          oldFile = info.getConflictOldFile();
          newFile = info.getConflictNewFile();
          workingFile = info.getConflictWrkFile();
          mergeCase = workingFile == null || workingFile.getName().contains("working");
          // for debug
          if (workingFile == null) {
            LOG.info("Null working file when merging text conflict for " + file.getPath() + " old file: " + oldFile + " new file: " + newFile);
          }
          if (mergeCase) {
            // this is merge case
            oldFile = info.getConflictNewFile();
            newFile = info.getConflictOldFile();
            workingFile = info.getConflictWrkFile();
          }
          data.LAST_REVISION_NUMBER = new SvnRevisionNumber(info.getRevision());
        } else {
          throw new VcsException("Could not get info for " + file.getPath());
        }
        if (oldFile == null || newFile == null || workingFile == null) {
          ByteArrayOutputStream bos = getBaseRevisionContents(vcs, file);
          data.ORIGINAL = bos.toByteArray();
          data.LAST = bos.toByteArray();
          data.CURRENT = readFile(new File(file.getPath()));
        }
        else {
          data.ORIGINAL = readFile(oldFile);
          data.LAST = readFile(newFile);
          data.CURRENT = readFile(workingFile);
        }
        if (mergeCase) {
          final ByteArrayOutputStream contents = getBaseRevisionContents(vcs, file);
          if (! Arrays.equals(contents.toByteArray(), data.ORIGINAL)) {
            // swap base and server: another order of merge arguments
            byte[] original = data.ORIGINAL;
            data.ORIGINAL = data.LAST;
            data.LAST = original;
          }
        }
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, VcsBundle.message("multiple.file.merge.loading.progress.title"), false, myProject);

    return data;
  }

  private ByteArrayOutputStream getBaseRevisionContents(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      byte[] contents = SvnUtil.getFileContents(vcs, SvnTarget.fromFile(new File(file.getPath())), SVNRevision.BASE, SVNRevision.UNDEFINED);
      bos.write(contents);
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return bos;
  }

  private static byte[] readFile(File workingFile) throws VcsException {
    try {
      return FileUtil.loadFileBytes(workingFile);
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  public void conflictResolvedForFile(VirtualFile file) {
    // TODO: Add possibility to resolve content conflicts separately from property conflicts.
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File path = new File(file.getPath());
    try {
      // TODO: Probably false should be passed to "resolveTree", but previous logic used true implicitly
      vcs.getFactory(path).createConflictClient().resolve(path, SVNDepth.EMPTY, false, true, true);
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
    // the .mine/.r## files have been deleted
    final VirtualFile parent = file.getParent();
    if (parent != null) {
      parent.refresh(true, false);
    }
  }

  public boolean isBinary(@NotNull final VirtualFile file) {
    SvnVcs vcs = SvnVcs.getInstance(myProject);

    try {
      File ioFile = new File(file.getPath());
      PropertyClient client = vcs.getFactory(ioFile).createPropertyClient();

      SVNPropertyData svnPropertyData = client.getProperty(SvnTarget.fromFile(ioFile), SVNProperty.MIME_TYPE, false, SVNRevision.WORKING);
      if (svnPropertyData != null && SVNProperty.isBinaryMimeType(SVNPropertyValue.getPropertyAsString(svnPropertyData.getValue()))) {
        return true;
      }
    }
    catch (VcsException e) {
      LOG.warn(e);
    }

    return false;
  }
}
