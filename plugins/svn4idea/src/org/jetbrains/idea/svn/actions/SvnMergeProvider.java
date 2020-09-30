// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class SvnMergeProvider implements MergeProvider {
  private static final @NonNls String TEXT_MIME_TYPE_PREFIX = "text/";
  private static final @NonNls String WORKING_MARKER = "working";

  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(SvnMergeProvider.class);

  public SvnMergeProvider(final Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public MergeData loadRevisions(@NotNull final VirtualFile file) throws VcsException {
    final MergeData data = new MergeData();
    File oldFile = null;
    File newFile = null;
    File workingFile = null;
    boolean mergeCase = false;
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    Info info = vcs.getInfo(file);

    if (info != null) {
      oldFile = info.getConflictOldFile();
      newFile = info.getConflictNewFile();
      workingFile = info.getConflictWrkFile();
      mergeCase = workingFile == null || workingFile.getName().contains(WORKING_MARKER);
      // for debug
      if (workingFile == null) {
        LOG
          .info("Null working file when merging text conflict for " + file.getPath() + " old file: " + oldFile + " new file: " + newFile);
      }
      if (mergeCase) {
        // this is merge case
        oldFile = info.getConflictNewFile();
        newFile = info.getConflictOldFile();
        workingFile = info.getConflictWrkFile();
      }
      data.LAST_REVISION_NUMBER = new SvnRevisionNumber(info.getRevision());
    }
    else {
      throw new VcsException(message("error.could.not.get.info.for.path", file.getPath()));
    }
    if (oldFile == null || newFile == null || workingFile == null) {
      ByteArrayOutputStream bos = getBaseRevisionContents(vcs, file);
      data.ORIGINAL = bos.toByteArray();
      data.LAST = bos.toByteArray();
      data.CURRENT = readFile(virtualToIoFile(file));
    }
    else {
      data.ORIGINAL = readFile(oldFile);
      data.LAST = readFile(newFile);
      data.CURRENT = readFile(workingFile);
    }
    if (mergeCase) {
      final ByteArrayOutputStream contents = getBaseRevisionContents(vcs, file);
      if (!Arrays.equals(contents.toByteArray(), data.ORIGINAL)) {
        // swap base and server: another order of merge arguments
        byte[] original = data.ORIGINAL;
        data.ORIGINAL = data.LAST;
        data.LAST = original;
      }
    }
    return data;
  }

  private ByteArrayOutputStream getBaseRevisionContents(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      byte[] contents = SvnUtil.getFileContents(vcs, Target.on(virtualToIoFile(file)), Revision.BASE, Revision.UNDEFINED);
      bos.write(contents);
    }
    catch (VcsException | IOException e) {
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

  @Override
  public void conflictResolvedForFile(@NotNull VirtualFile file) {
    // TODO: Add possibility to resolve content conflicts separately from property conflicts.
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File path = virtualToIoFile(file);
    try {
      // TODO: Probably false should be passed to "resolveTree", but previous logic used true implicitly
      vcs.getFactory(path).createConflictClient().resolve(path, Depth.EMPTY, false, true, true);
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

  @Override
  public boolean isBinary(@NotNull final VirtualFile file) {
    SvnVcs vcs = SvnVcs.getInstance(myProject);

    try {
      File ioFile = virtualToIoFile(file);
      PropertyClient client = vcs.getFactory(ioFile).createPropertyClient();

      PropertyValue value = client.getProperty(Target.on(ioFile), SvnPropertyKeys.SVN_MIME_TYPE, false, Revision.WORKING);
      if (value != null && isBinaryMimeType(value.toString())) {
        return true;
      }
    }
    catch (SvnBindException e) {
      LOG.warn(e);
    }

    return false;
  }

  private static boolean isBinaryMimeType(@NotNull String mimeType) {
    return !mimeType.startsWith(TEXT_MIME_TYPE_PREFIX);
  }
}
