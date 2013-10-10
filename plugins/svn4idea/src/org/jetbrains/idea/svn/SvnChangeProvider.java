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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.portable.SvnExceptionWrapper;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.*;

/**
 * @author max
 * @author yole
 */
public class SvnChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangeProvider");
  public static final String ourDefaultListName = VcsBundle.message("changes.default.changelist.name");
  public static final String PROPERTY_LAYER = "Property";

  private final SvnVcs myVcs;
  private final VcsContextFactory myFactory;
  private final SvnFileUrlMappingImpl mySvnFileUrlMapping;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
    myFactory = VcsContextFactory.SERVICE.getInstance();
    mySvnFileUrlMapping = (SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping();
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, ProgressIndicator progress,
                         final ChangeListManagerGate addGate) throws VcsException {
    final SvnScopeZipper zipper = new SvnScopeZipper(dirtyScope);
    zipper.run();

    final Map<String, SvnScopeZipper.MyDirNonRecursive> nonRecursiveMap = zipper.getNonRecursiveDirs();
    final ISVNStatusFileProvider fileProvider = createFileProvider(nonRecursiveMap);

    try {
      final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, progress);

      final StatusWalkerPartnerImpl partner = new StatusWalkerPartnerImpl(myVcs, progress);
      final NestedCopiesBuilder nestedCopiesBuilder = new NestedCopiesBuilder(myVcs, mySvnFileUrlMapping);

      final EventDispatcher<StatusReceiver> statusReceiver = EventDispatcher.create(StatusReceiver.class);
      statusReceiver.addListener(context);
      statusReceiver.addListener(nestedCopiesBuilder);

      final SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(myVcs, statusReceiver.getMulticaster(), partner);

      for (FilePath path : zipper.getRecursiveDirs()) {
        walker.go(path, SVNDepth.INFINITY);
      }

      partner.setFileProvider(fileProvider);
      for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
        walker.go(item.getDir(), SVNDepth.IMMEDIATES);
      }

      // they are taken under non recursive: ENTRIES file is read anyway, so we get to know parent status also for free
      /*for (FilePath path : zipper.getSingleFiles()) {
        FileStatus status = getParentStatus(context, path);
        processFile(path, context, status, false, context.getClient());
      }*/

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

  private void processUnsaved(VcsDirtyScope dirtyScope, ChangeListManagerGate addGate, SvnChangeProviderContext context)
    throws SVNException {
    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    final Document[] unsavedDocuments = fileDocumentManager.getUnsavedDocuments();
    for (Document unsavedDocument : unsavedDocuments) {
      final VirtualFile file = fileDocumentManager.getFile(unsavedDocument);
      if (file != null && dirtyScope.belongsTo(new FilePathImpl(file)) && fileDocumentManager.isFileModified(file)) {
        final FileStatus status = addGate.getStatus(file);
        if (status == null || FileStatus.NOT_CHANGED.equals(status)) {
          context.addModifiedNotSavedChange(file);
        }
      }
    }
  }

  private ISVNStatusFileProvider createFileProvider(Map<String, SvnScopeZipper.MyDirNonRecursive> nonRecursiveMap) {
    // translate into terms of File.getAbsolutePath()
    final Map<String, Map> preparedMap = new HashMap<String, Map>();
    for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
      final Map result = new HashMap();
      for (FilePath path : item.getChildrenList()) {
        result.put(path.getName(), path.getIOFile());
      }
      preparedMap.put(item.getDir().getIOFile().getAbsolutePath(), result);
    }
    return new ISVNStatusFileProvider() {
      public Map getChildrenFiles(File parent) {
        return preparedMap.get(parent.getAbsolutePath());
      }
    };
  }

  private void processCopiedAndDeleted(final SvnChangeProviderContext context, final VcsDirtyScope dirtyScope) throws SVNException {
    for(SvnChangedFile copiedFile: context.getCopiedFiles()) {
      if (context.isCanceled()) {
        throw new ProcessCanceledException();
      }
      processCopiedFile(copiedFile, context.getBuilder(), context, dirtyScope);
    }
    for(SvnChangedFile deletedFile: context.getDeletedFiles()) {
      if (context.isCanceled()) {
        throw new ProcessCanceledException();
      }
      context.processStatus(deletedFile.getFilePath(), deletedFile.getStatus());
    }
  }

  public void getChanges(final FilePath path, final boolean recursive, final ChangelistBuilder builder) throws SVNException {
    final SvnChangeProviderContext context = new SvnChangeProviderContext(myVcs, builder, null);
    final StatusWalkerPartnerImpl partner = new StatusWalkerPartnerImpl(myVcs, ProgressManager.getInstance().getProgressIndicator());
    final SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(myVcs, context, partner);
    walker.go(path, recursive ? SVNDepth.INFINITY : SVNDepth.IMMEDIATES);
    processCopiedAndDeleted(context, null);
  }

  private void processCopiedFile(SvnChangedFile copiedFile,
                                 ChangelistBuilder builder,
                                 SvnChangeProviderContext context, final VcsDirtyScope dirtyScope) throws SVNException {
    boolean foundRename = false;
    final SVNStatus copiedStatus = copiedFile.getStatus();
    final String copyFromURL = copiedFile.getCopyFromURL();
    final FilePath copiedToPath = copiedFile.getFilePath();

    // if copy target is _deleted_, treat like deleted, not moved!
    /*for (Iterator<SvnChangedFile> iterator = context.getDeletedFiles().iterator(); iterator.hasNext();) {
      final SvnChangedFile deletedFile = iterator.next();
      final FilePath deletedPath = deletedFile.getFilePath();

      if (Comparing.equal(deletedPath, copiedToPath)) {
        return;
      }
    }*/

    final Set<SvnChangedFile> deletedToDelete = new HashSet<SvnChangedFile>();

    for (Iterator<SvnChangedFile> iterator = context.getDeletedFiles().iterator(); iterator.hasNext();) {
      SvnChangedFile deletedFile = iterator.next();
      final SVNStatus deletedStatus = deletedFile.getStatus();
      if ((deletedStatus != null) && (deletedStatus.getURL() != null) && Comparing.equal(copyFromURL, deletedStatus.getURL().toString())) {
        final String clName = SvnUtil.getChangelistName(copiedFile.getStatus());
        final Change newChange = context.createMovedChange(createBeforeRevision(deletedFile, true),
                                                        CurrentContentRevision.create(copiedFile.getFilePath()), copiedStatus,
                                                        deletedStatus);
        applyMovedChange(copiedFile.getFilePath(), builder, dirtyScope, deletedToDelete, deletedFile, clName, newChange);
        for(Iterator<SvnChangedFile> iterChild = context.getDeletedFiles().iterator(); iterChild.hasNext();) {
          SvnChangedFile deletedChild = iterChild.next();
          final SVNStatus childStatus = deletedChild.getStatus();
          if (childStatus == null) {
            continue;
          }
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
              final Change movedChange = context.createMovedChange(createBeforeRevision(deletedChild, true),
                                                              CurrentContentRevision.create(newFilePath),
                                                              context.getTreeConflictStatus(newPath), childStatus);
              applyMovedChange(newFilePath, builder, dirtyScope, deletedToDelete, deletedChild, clName, movedChange);
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
      File wcPath = guessWorkingCopyPath(copiedStatus.getFile(), copiedStatus.getURL(), copyFromURL);
      SVNStatus status;
      try {
        status = context.getClient().doStatus(wcPath, false);
      }
      catch(SVNException ex) {
        status = null;
      }
      if (status != null && SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_DELETED)) {
        final FilePath filePath = myFactory.createFilePathOnDeleted(wcPath, false);
        final SvnContentRevision beforeRevision = SvnContentRevision.createBaseRevision(myVcs, filePath, status.getRevision());
        final ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());
        builder.processChangeInList(context.createMovedChange(beforeRevision, afterRevision, copiedStatus, status),
                                    SvnUtil.getChangelistName(status), SvnVcs.getKey());
        foundRename = true;
      }
    }

    if (!foundRename) {
      // for debug
      LOG.info("Rename not found for " + copiedFile.getFilePath().getPresentableUrl());
      context.processStatus(copiedFile.getFilePath(), copiedStatus);
    }
  }

  private void applyMovedChange(final FilePath oldPath,
                                ChangelistBuilder builder,
                                final VcsDirtyScope dirtyScope,
                                Set<SvnChangedFile> deletedToDelete, SvnChangedFile deletedFile, String clName, final Change newChange) {
    final boolean isUnder = dirtyScope == null ? true : ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return ChangeListManagerImpl.isUnder(newChange, dirtyScope);
      }
    });
    if (isUnder) {
      builder.removeRegisteredChangeFor(oldPath);
      builder.processChangeInList(newChange, clName, SvnVcs.getKey());
      deletedToDelete.add(deletedFile);
    }
  }

  private SvnContentRevision createBeforeRevision(final SvnChangedFile changedFile, final boolean forDeleted) {
    return SvnContentRevision.createBaseRevision(myVcs,
                                                 forDeleted ? FilePathImpl.createForDeletedFile(changedFile.getStatus().getFile(),
                                                                                                changedFile.getFilePath().isDirectory()) :
                                                 changedFile.getFilePath(), changedFile.getStatus().getRevision());
  }

  private static File guessWorkingCopyPath(final File file, @NotNull final SVNURL url, final String copyFromURL) throws SVNException {
    String copiedPath = url.getPath();
    String copyFromPath = SVNURL.parseURIEncoded(copyFromURL).getPath();
    String commonPathAncestor = SVNPathUtil.getCommonPathAncestor(copiedPath, copyFromPath);
    int pathSegmentCount = SVNPathUtil.getSegmentsCount(copiedPath);
    int ancestorSegmentCount = SVNPathUtil.getSegmentsCount(commonPathAncestor);
    boolean startsWithSlash = file.getAbsolutePath().startsWith("/");
    List<String> segments = StringUtil.split(file.getPath(), File.separator);
    List<String> copyFromPathSegments = StringUtil.split(copyFromPath, "/");
    List<String> resultSegments = new ArrayList<String>();
    final int keepSegments = segments.size() - pathSegmentCount + ancestorSegmentCount;
    for(int i=0; i< keepSegments; i++) {
      resultSegments.add(segments.get(i));
    }
    for(int i=ancestorSegmentCount; i<copyFromPathSegments.size(); i++) {
      resultSegments.add(copyFromPathSegments.get(i));
    }

    String result = StringUtil.join(resultSegments, "/");
    if (startsWithSlash) {
      result = "/" + result;
    }
    return new File(result);
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public void doCleanup(final List<VirtualFile> files) {
    new CleanupWorker(VfsUtil.toVirtualFileArray(files), myVcs.getProject(), "action.Subversion.cleanup.progress.title").execute();
  }
}
