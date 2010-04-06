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
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
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
  public static final String ourDefaultListName = VcsBundle.message("changes.default.changlist.name");

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
      final NestedCopiesBuilder nestedCopiesBuilder = new NestedCopiesBuilder();

      final EventDispatcher<StatusReceiver> statusReceiver = EventDispatcher.create(StatusReceiver.class);
      statusReceiver.addListener(context);
      statusReceiver.addListener(nestedCopiesBuilder);

      final SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(statusReceiver.getMulticaster(), partner);

      for (FilePath path : zipper.getRecursiveDirs()) {
        walker.go(path, SVNDepth.INFINITY);
      }

      partner.setFileProvider(fileProvider);
      for (SvnScopeZipper.MyDirNonRecursive item : nonRecursiveMap.values()) {
        walker.go(item.getDir(), SVNDepth.FILES);
      }

      // they are taken under non recursive: ENTRIES file is read anyway, so we get to know parent status also for free
      /*for (FilePath path : zipper.getSingleFiles()) {
        FileStatus status = getParentStatus(context, path);
        processFile(path, context, status, false, context.getClient());
      }*/

      processCopiedAndDeleted(context);

      mySvnFileUrlMapping.acceptNestedData(nestedCopiesBuilder.getSet());
    }
    catch (SVNException e) {
      throw new VcsException(e);
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

  private void processCopiedAndDeleted(final SvnChangeProviderContext context) throws SVNException {
    for(SvnChangedFile copiedFile: context.getCopiedFiles()) {
      if (context.isCanceled()) {
        throw new ProcessCanceledException();
      }
      processCopiedFile(copiedFile, context.getBuilder(), context);
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
    final SvnRecursiveStatusWalker walker = new SvnRecursiveStatusWalker(context, partner);
    walker.go(path, recursive ? SVNDepth.INFINITY : SVNDepth.IMMEDIATES);
    processCopiedAndDeleted(context);
  }

  @Nullable
  private String changeListNameFromStatus(final SVNStatus status) {
    if (WorkingCopyFormat.getInstance(status.getWorkingCopyFormat()).supportsChangelists()) {
      if (SVNNodeKind.FILE.equals(status.getKind())) {
        final String clName = status.getChangelistName();
        return (clName == null) ? null : clName;
      }
    }
    // always null for earlier versions
    return null;
  }

  private void processCopiedFile(SvnChangedFile copiedFile, ChangelistBuilder builder, SvnChangeProviderContext context) throws SVNException {
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
        final String clName = changeListNameFromStatus(copiedFile.getStatus());
        builder.processChangeInList(context.createMovedChange(createBeforeRevision(deletedFile, true),
                                 CurrentContentRevision.create(copiedFile.getFilePath()), copiedStatus, deletedStatus), clName, SvnVcs.getKey());
        deletedToDelete.add(deletedFile);
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
          final String childURL = childUrl.toString();
          if (StringUtil.startsWithConcatenationOf(childURL, copyFromURL, "/")) {
            String relativePath = childURL.substring(copyFromURL.length());
            File newPath = new File(copiedFile.getFilePath().getIOFile(), relativePath);
            FilePath newFilePath = myFactory.createFilePathOn(newPath);
            if (!context.isDeleted(newFilePath)) {
              builder.processChangeInList(context.createMovedChange(createBeforeRevision(deletedChild, true),
                                                            CurrentContentRevision.create(newFilePath),
                                                            context.getTreeConflictStatus(newPath), childStatus), clName, SvnVcs.getKey());
              deletedToDelete.add(deletedChild);
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
      if (status != null && status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
        final FilePath filePath = myFactory.createFilePathOnDeleted(wcPath, false);
        final SvnContentRevision beforeRevision = SvnContentRevision.create(myVcs, filePath, status.getCommittedRevision());
        final ContentRevision afterRevision = CurrentContentRevision.create(copiedFile.getFilePath());
        builder.processChangeInList(context.createMovedChange(beforeRevision, afterRevision, copiedStatus, status), changeListNameFromStatus(status),
                                    SvnVcs.getKey());
        foundRename = true;
      }
    }

    if (!foundRename) {
      // for debug
      LOG.info("Rename not found for " + copiedFile.getFilePath().getPresentableUrl());
      context.processStatus(copiedFile.getFilePath(), copiedStatus);
    }
  }

  private SvnContentRevision createBeforeRevision(final SvnChangedFile changedFile, final boolean forDeleted) {
    return SvnContentRevision.create(myVcs,
        forDeleted ? FilePathImpl.createForDeletedFile(changedFile.getStatus().getFile(), changedFile.getFilePath().isDirectory()) :
                   changedFile.getFilePath(), changedFile.getStatus().getCommittedRevision());
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
