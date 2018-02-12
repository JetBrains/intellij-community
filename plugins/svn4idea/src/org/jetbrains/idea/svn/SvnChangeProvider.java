// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
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
import java.util.Set;

import static com.intellij.util.ObjectUtils.notNull;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;
import static org.jetbrains.idea.svn.SvnUtil.isAncestor;

/**
 * @author max
 * @author yole
 */
public class SvnChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangeProvider");
  public static final String PROPERTY_LAYER = "Property";

  @NotNull private final SvnVcs myVcs;
  @NotNull private final VcsContextFactory myFactory;
  @NotNull private final SvnFileUrlMappingImpl mySvnFileUrlMapping;

  public SvnChangeProvider(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myFactory = VcsContextFactory.SERVICE.getInstance();
    mySvnFileUrlMapping = (SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping();
  }

  public void getChanges(@NotNull VcsDirtyScope dirtyScope, @NotNull ChangelistBuilder builder, @NotNull ProgressIndicator progress,
                         @NotNull ChangeListManagerGate addGate) throws VcsException {
    final SvnScopeZipper zipper = new SvnScopeZipper(dirtyScope);
    zipper.run();

    final MultiMap<FilePath, FilePath> nonRecursiveMap = zipper.getNonRecursiveDirs();

    try {
      final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, progress);
      final NestedCopiesBuilder nestedCopiesBuilder = new NestedCopiesBuilder(myVcs, mySvnFileUrlMapping);
      final EventDispatcher<StatusReceiver> statusReceiver = EventDispatcher.create(StatusReceiver.class);
      statusReceiver.addListener(context);
      statusReceiver.addListener(nestedCopiesBuilder);

      final SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(myVcs, statusReceiver.getMulticaster(), progress);

      for (FilePath path : zipper.getRecursiveDirs()) {
        walker.go(path, Depth.INFINITY);
      }

      walker.setNonRecursiveScope(nonRecursiveMap);
      for (FilePath path : nonRecursiveMap.keySet()) {
        walker.go(path, Depth.IMMEDIATES);
      }

      statusReceiver.getMulticaster().finish();

      processCopiedAndDeleted(context, dirtyScope);
      processUnsaved(dirtyScope, addGate, context);

      mySvnFileUrlMapping.acceptNestedData(nestedCopiesBuilder.getCopies());
    } catch (SvnExceptionWrapper e) {
      LOG.info(e);
      throw new VcsException(e.getCause());
    }
  }

  private static void processUnsaved(@NotNull VcsDirtyScope dirtyScope,
                                     @NotNull ChangeListManagerGate addGate,
                                     @NotNull SvnChangeProviderContext context) throws SvnBindException {
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

  private void processCopiedAndDeleted(@NotNull SvnChangeProviderContext context, @Nullable VcsDirtyScope dirtyScope)
    throws SvnBindException {
    for(SvnChangedFile copiedFile: context.getCopiedFiles()) {
      context.checkCanceled();
      processCopiedFile(copiedFile, context, dirtyScope);
    }
    for(SvnChangedFile deletedFile: context.getDeletedFiles()) {
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
                                 @Nullable VcsDirtyScope dirtyScope) throws SvnBindException {
    boolean foundRename = false;
    final Status copiedStatus = copiedFile.getStatus();
    Url copyFromURL = notNull(copiedFile.getCopyFromURL());
    final Set<SvnChangedFile> deletedToDelete = new HashSet<>();

    for (SvnChangedFile deletedFile : context.getDeletedFiles()) {
      final Status deletedStatus = deletedFile.getStatus();
      if (Comparing.equal(copyFromURL, deletedStatus.getURL())) {
        final String clName = SvnUtil.getChangelistName(copiedFile.getStatus());
        applyMovedChange(context, copiedFile.getFilePath(), dirtyScope, deletedToDelete, deletedFile, copiedStatus, clName);
        for (SvnChangedFile deletedChild : context.getDeletedFiles()) {
          final Status childStatus = deletedChild.getStatus();
          final Url childUrl = childStatus.getURL();
          if (childUrl == null) {
            continue;
          }
          if (isAncestor(copyFromURL, childUrl)) {
            String relativePath = getRelativeUrl(copyFromURL, childUrl);
            File newPath = new File(copiedFile.getFilePath().getIOFile(), relativePath);
            FilePath newFilePath = myFactory.createFilePathOn(newPath);
            if (!context.isDeleted(newFilePath)) {
              applyMovedChange(context, newFilePath, dirtyScope, deletedToDelete, deletedChild, context.getTreeConflictStatus(newPath),
                               clName);
            }
          }
        }
        foundRename = true;
        break;
      }
    }

    final List<SvnChangedFile> deletedFiles = context.getDeletedFiles();
    for (SvnChangedFile file : deletedToDelete) {
      deletedFiles.remove(file);
    }

    // handle the case when the deleted file wasn't included in the dirty scope - try searching for the local copy
    // by building a relative url
    if (!foundRename && copiedStatus.getURL() != null) {
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
          final FilePath filePath = myFactory.createFilePathOn(wcPath, false);
          final SvnContentRevision beforeRevision = SvnContentRevision.createBaseRevision(myVcs, filePath, status.getRevision());
          final ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());
          context.getBuilder().processChangeInList(context.createMovedChange(beforeRevision, afterRevision, copiedStatus, status),
                                                   SvnUtil.getChangelistName(status), SvnVcs.getKey());
          foundRename = true;
        }
      }
    }

    if (!foundRename) {
      // for debug
      LOG.info("Rename not found for " + copiedFile.getFilePath().getPresentableUrl());
      context.processStatus(copiedFile.getFilePath(), copiedStatus);
    }
  }

  private void applyMovedChange(@NotNull SvnChangeProviderContext context,
                                @NotNull FilePath oldPath,
                                @Nullable final VcsDirtyScope dirtyScope,
                                @NotNull Set<SvnChangedFile> deletedToDelete,
                                @NotNull SvnChangedFile deletedFile,
                                @Nullable Status copiedStatus,
                                @Nullable String clName) throws SvnBindException {
    final Change change = context
      .createMovedChange(createBeforeRevision(deletedFile, true), CurrentContentRevision.create(oldPath), copiedStatus,
                         deletedFile.getStatus());
    final boolean isUnder = dirtyScope == null
                            ? true
                            : ReadAction.compute(() -> ChangeListManagerImpl.isUnder(change, dirtyScope));
    if (isUnder) {
      context.getBuilder().removeRegisteredChangeFor(oldPath);
      context.getBuilder().processChangeInList(change, clName, SvnVcs.getKey());
      deletedToDelete.add(deletedFile);
    }
  }

  @NotNull
  private SvnContentRevision createBeforeRevision(@NotNull SvnChangedFile changedFile, boolean forDeleted) {
    Status status = changedFile.getStatus();
    FilePath path = changedFile.getFilePath();

    return SvnContentRevision
      .createBaseRevision(myVcs, forDeleted ? VcsUtil.getFilePath(status.getFile(), path.isDirectory()) : path,
                          status.getRevision());
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  @Override
  public void doCleanup(@NotNull List<VirtualFile> files) {
    new CleanupWorker(myVcs, files).execute();
  }
}
