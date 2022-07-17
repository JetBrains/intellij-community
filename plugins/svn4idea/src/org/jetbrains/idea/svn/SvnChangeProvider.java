// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnExceptionWrapper;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.util.containers.ContainerUtil.map2SetNotNull;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

public class SvnChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance(SvnChangeProvider.class);
  public static final @NonNls String PROPERTY_LAYER = "Property";

  @NotNull private final SvnVcs myVcs;

  public SvnChangeProvider(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  @Override
  public void getChanges(@NotNull VcsDirtyScope dirtyScope, @NotNull ChangelistBuilder builder, @NotNull ProgressIndicator progress,
                         @NotNull ChangeListManagerGate addGate) throws VcsException {
    try {
      final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, progress);
      final NestedCopiesBuilder nestedCopiesBuilder = new NestedCopiesBuilder(myVcs);
      final EventDispatcher<StatusReceiver> statusReceiver = EventDispatcher.create(StatusReceiver.class);
      statusReceiver.addListener(context);
      statusReceiver.addListener(nestedCopiesBuilder);

      final SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(myVcs, statusReceiver.getMulticaster(), progress);

      for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
        walker.go(path, Depth.INFINITY);
      }

      for (FilePath path : map2SetNotNull(dirtyScope.getDirtyFilesNoExpand(), it -> it.isDirectory() ? it : it.getParentPath())) {
        walker.go(path, Depth.IMMEDIATES);
      }

      statusReceiver.getMulticaster().finish();

      processCopiedAndDeleted(context, dirtyScope);
      processUnsaved(dirtyScope, addGate, context);

      myVcs.getSvnFileUrlMappingImpl().acceptNestedData(nestedCopiesBuilder.getCopies());
    } catch (SvnExceptionWrapper e) {
      LOG.info(e);
      throw new VcsException(e.getCause());
    }
  }

  private static void processUnsaved(@NotNull VcsDirtyScope dirtyScope,
                                     @NotNull ChangeListManagerGate addGate,
                                     @NotNull SvnChangeProviderContext context) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();

    for (Document unsavedDocument : fileDocumentManager.getUnsavedDocuments()) {
      final VirtualFile file = fileDocumentManager.getFile(unsavedDocument);
      if (file != null && dirtyScope.belongsTo(VcsUtil.getFilePath(file)) && fileDocumentManager.isFileModified(file)) {
        final FileStatus status = addGate.getStatus(file);
        if (status == null || FileStatus.NOT_CHANGED.equals(status)) {
          context.addModifiedNotSavedChange(file);
        }
      }
    }
  }

  private void processCopiedAndDeleted(@NotNull SvnChangeProviderContext context, @Nullable VcsDirtyScope dirtyScope) {
    for (SvnChangedFile copiedFile : context.getCopiedFiles()) {
      context.checkCanceled();
      processCopiedFile(copiedFile, context, dirtyScope);
    }
    for (SvnChangedFile deletedFile : context.getDeletedFiles()) {
      context.checkCanceled();
      context.processStatus(deletedFile.getFilePath(), deletedFile.getStatus());
    }
  }

  public void getChanges(@NotNull FilePath path, boolean recursive, @NotNull ChangelistBuilder builder) throws SvnBindException {
    final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, null);
    SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(myVcs, context, ProgressManager.getInstance().getProgressIndicator());
    walker.go(path, recursive ? Depth.INFINITY : Depth.IMMEDIATES);
    processCopiedAndDeleted(context, null);
  }

  private void processCopiedFile(@NotNull SvnChangedFile copiedFile,
                                 @NotNull SvnChangeProviderContext context,
                                 @Nullable VcsDirtyScope dirtyScope) {
    Url copyFromURL = Objects.requireNonNull(copiedFile.getCopyFromURL());
    SvnChangedFile movedFromFile = find(context.getDeletedFiles(), it -> copyFromURL.equals(it.getStatus().getUrl()));

    if (movedFromFile != null) {
      Set<SvnChangedFile> movedFiles = new HashSet<>();
      String changeListName = SvnUtil.getChangelistName(copiedFile.getStatus());

      applyMovedChange(context, copiedFile.getFilePath(), dirtyScope, movedFiles, movedFromFile, copiedFile.getStatus(), changeListName);

      for (SvnChangedFile deletedChild : context.getDeletedFiles()) {
        if (movedFromFile == deletedChild) continue;

        Url childUrl = deletedChild.getStatus().getUrl();
        if (childUrl == null) continue;

        if (isAncestor(copyFromURL, childUrl)) {
          String relativePath = getRelativeUrl(copyFromURL, childUrl);
          File newPath = new File(copiedFile.getFilePath().getIOFile(), relativePath);
          FilePath newFilePath = VcsUtil.getFilePath(newPath);

          if (!context.isDeleted(newFilePath)) {
            applyMovedChange(context, newFilePath, dirtyScope, movedFiles, deletedChild, context.getTreeConflictStatus(newPath),
                             changeListName);
          }
        }
      }

      List<SvnChangedFile> deletedFiles = context.getDeletedFiles();
      for (SvnChangedFile file : movedFiles) {
        deletedFiles.remove(file);
      }
    }

    boolean foundRename = movedFromFile != null;

    // handle the case when the deleted file wasn't included in the dirty scope - try searching for the local copy
    // by building a relative url
    if (!foundRename && copiedFile.getStatus().getUrl() != null) {
      File wcPath = myVcs.getSvnFileUrlMapping().getLocalPath(copyFromURL);

      if (wcPath != null) {
        Status status;
        try {
          status = myVcs.getFactory(wcPath).createStatusClient().doStatus(wcPath, false);
        }
        catch (SvnBindException ex) {
          LOG.info(ex);
          status = null;
        }
        if (status != null && status.is(StatusType.STATUS_DELETED)) {
          FilePath filePath = VcsUtil.getFilePath(wcPath, status.isDirectory());
          SvnContentRevision beforeRevision = SvnContentRevision.createBaseRevision(myVcs, filePath, status.getRevision());
          ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());

          context.getBuilder().processChangeInList(context.createMovedChange(beforeRevision, afterRevision, copiedFile.getStatus(), status),
                                                   SvnUtil.getChangelistName(status), SvnVcs.getKey());
          foundRename = true;
        }
      }
    }

    if (!foundRename) {
      LOG.info("Rename not found for " + copiedFile.getFilePath().getPresentableUrl());
      context.processStatus(copiedFile.getFilePath(), copiedFile.getStatus());
    }
  }

  private void applyMovedChange(@NotNull SvnChangeProviderContext context,
                                @NotNull FilePath oldPath,
                                @Nullable final VcsDirtyScope dirtyScope,
                                @NotNull Set<SvnChangedFile> movedFiles,
                                @NotNull SvnChangedFile deletedFile,
                                @Nullable Status copiedStatus,
                                @Nullable String clName) {
    FilePath filePath = VcsUtil.getFilePath(deletedFile.getStatus().getFile(), deletedFile.getFilePath().isDirectory());
    SvnContentRevision beforeRevision = SvnContentRevision.createBaseRevision(myVcs, filePath, deletedFile.getStatus().getRevision());
    ContentRevision afterRevision = CurrentContentRevision.create(oldPath);
    Change change = context.createMovedChange(beforeRevision, afterRevision, copiedStatus, deletedFile.getStatus());
    boolean isUnder = dirtyScope == null || ReadAction.compute(() -> ChangeListManagerImpl.isUnder(change, dirtyScope));

    if (isUnder) {
      context.getBuilder().removeRegisteredChangeFor(oldPath);
      context.getBuilder().processChangeInList(change, clName, SvnVcs.getKey());
      movedFiles.add(deletedFile);
    }
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  @Override
  public void doCleanup(@NotNull List<VirtualFile> files) {
    new CleanupWorker(myVcs, files).execute();
  }
}
