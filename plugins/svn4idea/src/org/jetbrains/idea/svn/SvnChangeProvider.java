/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnExceptionWrapper;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;

import java.io.File;
import java.util.*;

/**
 * @author max
 * @author yole
 */
public class SvnChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangeProvider");
  public static final String PROPERTY_LAYER = "Property";

  private static final NotNullFactory<Map<String, File>> NAME_TO_FILE_MAP_FACTORY = new NotNullFactory<Map<String, File>>() {
    @NotNull
    @Override
    public Map<String, File> create() {
      return ContainerUtil.newHashMap();
    }
  };

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

    final Map<String, SvnScopeZipper.MyDirNonRecursive> nonRecursiveMap = zipper.getNonRecursiveDirs();
    final ISVNStatusFileProvider fileProvider = createFileProvider(nonRecursiveMap);

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

      walker.setFileProvider(fileProvider);
      for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
        walker.go(item.getDir(), Depth.IMMEDIATES);
      }

      statusReceiver.getMulticaster().finish();

      processCopiedAndDeleted(context, dirtyScope);
      processUnsaved(dirtyScope, addGate, context);

      final Set<NestedCopyInfo> nestedCopies = nestedCopiesBuilder.getCopies();
      mySvnFileUrlMapping.acceptNestedData(nestedCopies);
      putAdministrative17UnderVfsListener(nestedCopies);
    } catch (SvnExceptionWrapper e) {
      LOG.info(e);
      throw new VcsException(e.getCause());
    } catch (SVNException e) {
      if (e.getCause() != null) {
        throw new VcsException(e.getMessage() + " " + e.getCause().getMessage(), e);
      }
      throw new VcsException(e);
    }
  }

  /**
   * TODO: Currently could not find exact case when "file status is not correctly refreshed after external commit" that is covered by this
   * TODO: code. So for now, checks for formats greater than 1.7 are not added here.
   */
  private static void putAdministrative17UnderVfsListener(Set<NestedCopyInfo> pointInfos) {
    if (! SvnVcs.ourListenToWcDb) return;
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    for (NestedCopyInfo info : pointInfos) {
      if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(info.getFormat()) && ! NestedCopyType.switched.equals(info.getType())) {
        final VirtualFile root = info.getFile();
        lfs.refreshIoFiles(Collections.singletonList(SvnUtil.getWcDb(new File(root.getPath()))), true, false, null);
      }
    }
  }

  private static void processUnsaved(@NotNull VcsDirtyScope dirtyScope,
                                     @NotNull ChangeListManagerGate addGate,
                                     @NotNull SvnChangeProviderContext context)
    throws SVNException {
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

  @NotNull
  private static ISVNStatusFileProvider createFileProvider(@NotNull Map<String, SvnScopeZipper.MyDirNonRecursive> nonRecursiveMap) {
    final Map<String, Map<String, File>> result = ContainerUtil.newHashMap();

    for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
      File file = item.getDir().getIOFile();

      Map<String, File> fileMap = ContainerUtil.getOrCreate(result, file.getAbsolutePath(), NAME_TO_FILE_MAP_FACTORY);
      for (FilePath path : item.getChildrenList()) {
        fileMap.put(path.getName(), path.getIOFile());
      }

      // also add currently processed file to the map of its parent, as there are cases when SVNKit calls ISVNStatusFileProvider with file
      // parent (and not file that was passed to doStatus()), gets null result and does not provide any status
      // see http://issues.tmatesoft.com/issue/SVNKIT-567 for details
      if (file.getParentFile() != null) {
        Map<String, File> parentMap = ContainerUtil.getOrCreate(result, file.getParentFile().getAbsolutePath(), NAME_TO_FILE_MAP_FACTORY);

        parentMap.put(file.getName(), file);
      }
    }

    return new ISVNStatusFileProvider() {
      @Override
      public Map<String, File> getChildrenFiles(File parent) {
        return result.get(parent.getAbsolutePath());
      }
    };
  }

  private void processCopiedAndDeleted(@NotNull SvnChangeProviderContext context, @Nullable VcsDirtyScope dirtyScope) throws SVNException {
    for(SvnChangedFile copiedFile: context.getCopiedFiles()) {
      context.checkCanceled();
      processCopiedFile(copiedFile, context, dirtyScope);
    }
    for(SvnChangedFile deletedFile: context.getDeletedFiles()) {
      context.checkCanceled();
      context.processStatus(deletedFile.getFilePath(), deletedFile.getStatus());
    }
  }

  public void getChanges(@NotNull FilePath path, boolean recursive, @NotNull ChangelistBuilder builder)
    throws SVNException, SvnBindException {
    final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, null);
    SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(myVcs, context, ProgressManager.getInstance().getProgressIndicator());
    walker.go(path, recursive ? Depth.INFINITY : Depth.IMMEDIATES);
    processCopiedAndDeleted(context, null);
  }

  private void processCopiedFile(@NotNull SvnChangedFile copiedFile,
                                 @NotNull SvnChangeProviderContext context,
                                 @Nullable VcsDirtyScope dirtyScope) throws SVNException {
    boolean foundRename = false;
    final Status copiedStatus = copiedFile.getStatus();
    final String copyFromURL = ObjectUtils.assertNotNull(copiedFile.getCopyFromURL());
    final Set<SvnChangedFile> deletedToDelete = new HashSet<>();

    for (SvnChangedFile deletedFile : context.getDeletedFiles()) {
      final Status deletedStatus = deletedFile.getStatus();
      if (deletedStatus.getURL() != null && Comparing.equal(copyFromURL, deletedStatus.getURL().toString())) {
        final String clName = SvnUtil.getChangelistName(copiedFile.getStatus());
        applyMovedChange(context, copiedFile.getFilePath(), dirtyScope, deletedToDelete, deletedFile, copiedStatus, clName);
        for (SvnChangedFile deletedChild : context.getDeletedFiles()) {
          final Status childStatus = deletedChild.getStatus();
          final SVNURL childUrl = childStatus.getURL();
          if (childUrl == null) {
            continue;
          }
          final String childURL = childUrl.toDecodedString();
          if (StringUtil.startsWithConcatenation(childURL, copyFromURL, "/")) {
            String relativePath = childURL.substring(copyFromURL.length());
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
          final FilePath filePath = myFactory.createFilePathOnDeleted(wcPath, false);
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
                                @Nullable String clName) throws SVNException {
    final Change change = context
      .createMovedChange(createBeforeRevision(deletedFile, true), CurrentContentRevision.create(oldPath), copiedStatus,
                         deletedFile.getStatus());
    final boolean isUnder = dirtyScope == null ? true : ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return ChangeListManagerImpl.isUnder(change, dirtyScope);
      }
    });
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

  public void doCleanup(@NotNull List<VirtualFile> files) {
    new CleanupWorker(VfsUtilCore.toVirtualFileArray(files), myVcs.getProject(), "action.Subversion.cleanup.progress.title").execute();
  }
}
