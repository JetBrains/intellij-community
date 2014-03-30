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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffMixin;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.DiffProviderEx;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionDescriptionImpl;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.LatestExistentSearcher;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.List;
import java.util.Map;

public class SvnDiffProvider extends DiffProviderEx implements DiffProvider, DiffMixin {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnDiffProvider");
  public static final String COMMIT_MESSAGE = "svn:log";
  private static final int BATCH_INFO_SIZE = 20;

  private final SvnVcs myVcs;

  public SvnDiffProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    final SVNInfo svnInfo = myVcs.getInfo(new File(file.getPresentableUrl()));

    return getRevision(svnInfo);
  }

  @Nullable
  private static VcsRevisionNumber getRevision(@Nullable SVNInfo info) {
    VcsRevisionNumber result = null;

    if (info != null) {
      SVNRevision revision = SVNRevision.UNDEFINED.equals(info.getCommittedRevision()) && info.getCopyFromRevision() != null
                             ? info.getCopyFromRevision()
                             : info.getRevision();

      result = new SvnRevisionNumber(revision);
    }

    return result;
  }

  @Override
  public Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(Iterable<VirtualFile> files) {
    Map<VirtualFile, VcsRevisionNumber> result = ContainerUtil.newHashMap();
    Map<String, VirtualFile> items = ContainerUtil.newHashMap();
    List<File> ioFiles = ContainerUtil.newArrayList();

    for (VirtualFile file : files) {
      File ioFile = VfsUtilCore.virtualToIoFile(file);
      ioFiles.add(ioFile);
      items.put(ioFile.getAbsolutePath(), file);

      // process in blocks of BATCH_INFO_SIZE size
      if (items.size() == BATCH_INFO_SIZE) {
        collectRevisionsInBatch(result, items, ioFiles);
        items.clear();
        ioFiles.clear();
      }
    }
    // process left files
    collectRevisionsInBatch(result, items, ioFiles);

    return result;
  }

  private void collectRevisionsInBatch(@NotNull final Map<VirtualFile, VcsRevisionNumber> revisionMap,
                                       @NotNull final Map<String, VirtualFile> fileMap,
                                       @NotNull List<File> ioFiles) {
    myVcs.collectInfo(ioFiles, createInfoHandler(revisionMap, fileMap));
  }

  @NotNull
  private static ISVNInfoHandler createInfoHandler(@NotNull final Map<VirtualFile, VcsRevisionNumber> revisionMap,
                                                   @NotNull final Map<String, VirtualFile> fileMap) {
    return new ISVNInfoHandler() {
      @Override
      public void handleInfo(SVNInfo info) throws SVNException {
        if (info != null) {
          VirtualFile file = fileMap.get(info.getFile().getAbsolutePath());

          if (file != null) {
            revisionMap.put(file, getRevision(info));
          }
          else {
            LOG.info("Could not find virtual file for path " + info.getFile().getAbsolutePath());
          }
        }
      }
    };
  }

  @Override
  public VcsRevisionDescription getCurrentRevisionDescription(VirtualFile file) {
    File path = new File(file.getPresentableUrl());
    return getCurrentRevisionDescription(path);
  }

  private VcsRevisionDescription getCurrentRevisionDescription(File path) {
    final SVNInfo svnInfo = myVcs.getInfo(path);
    if (svnInfo == null) {
      return null;
    }

    if (svnInfo.getCommittedRevision().equals(SVNRevision.UNDEFINED) && !svnInfo.getCopyFromRevision().equals(SVNRevision.UNDEFINED) &&
        svnInfo.getCopyFromURL() != null) {
      SVNURL copyUrl = svnInfo.getCopyFromURL();
      String localPath = myVcs.getSvnFileUrlMapping().getLocalPath(copyUrl.toString());
      if (localPath != null) {
        return getCurrentRevisionDescription(new File(localPath));
      }
    }

    try {
      final String message = getCommitMessage(path);
      return new VcsRevisionDescriptionImpl(new SvnRevisionNumber(svnInfo.getCommittedRevision()), svnInfo.getCommittedDate(),
                                            svnInfo.getAuthor(), message);
    }
    catch (VcsException e) {
      LOG.debug(e);    // most likely the file is unversioned
      return null;
    }
  }

  private String getCommitMessage(File path) throws VcsException {
    SVNPropertyData property =
      myVcs.getFactory(path).createPropertyClient().getProperty(SvnTarget.fromFile(path), COMMIT_MESSAGE, true, SVNRevision.BASE);

    return property != null ? SVNPropertyValue.getPropertyAsString(property.getValue()) : null;
  }

  private static ItemLatestState defaultResult() {
    return createResult(SVNRevision.HEAD, true, true);
  }

  private static ItemLatestState createResult(final SVNRevision revision, final boolean exists, boolean defaultHead) {
    return new ItemLatestState(new SvnRevisionNumber(revision), exists, defaultHead);
  }

  public ItemLatestState getLastRevision(VirtualFile file) {
    return getLastRevision(new File(file.getPath()));
  }

  public ContentRevision createFileContent(final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile) {
    FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(selectedFile);
    final SVNRevision svnRevision = ((SvnRevisionNumber)revisionNumber).getRevision();

    if (! SVNRevision.HEAD.equals(svnRevision)) {
      if (revisionNumber.equals(getCurrentRevision(selectedFile))) {
        return SvnContentRevision.createBaseRevision(myVcs, filePath, svnRevision);
      }
    }

    // not clear why we need it, with remote check..
    SVNStatus svnStatus = getFileStatus(new File(selectedFile.getPresentableUrl()), false);
    if (svnStatus != null && svnRevision.equals(svnStatus.getRevision())) {
        return SvnContentRevision.createBaseRevision(myVcs, filePath, svnRevision);
    }
    return SvnContentRevision.createRemote(myVcs, filePath, svnRevision);
  }

  private SVNStatus getFileStatus(File file, boolean remote) {
    SVNStatus result = null;

    try {
      result = myVcs.getFactory(file).createStatusClient().doStatus(file, remote, false);
    }
    catch (SVNException e) {
      LOG.debug(e);
    }

    return result;
  }

  public ItemLatestState getLastRevision(FilePath filePath) {
    return getLastRevision(filePath.getIOFile());
  }

  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }

  private ItemLatestState getLastRevision(final File file) {
    final SVNStatus svnStatus = getFileStatus(file, true);
    if (svnStatus == null || itemExists(svnStatus) && SVNRevision.UNDEFINED.equals(svnStatus.getRemoteRevision())) {
      // IDEADEV-21785 (no idea why this can happen)
      final SVNInfo info = myVcs.getInfo(file, SVNRevision.HEAD);
      if (info == null || info.getURL() == null) {
        LOG.info("No SVN status returned for " + file.getPath());
        return defaultResult();
      }
      return createResult(info.getCommittedRevision(), true, false);
    }
    final boolean exists = itemExists(svnStatus);
    if (! exists) {
      WorkingCopyFormat format = myVcs.getWorkingCopyFormat(file);
      long revision = -1;

      // skipped for 1.8
      if (!WorkingCopyFormat.ONE_DOT_EIGHT.equals(format)) {
        // get really latest revision
        // TODO: Algorithm seems not to be correct in all cases - for instance, when some subtree was deleted and replaced by other
        // TODO: with same names. pegRevision should be used somehow but this complicates the algorithm
        final LatestExistentSearcher searcher = new LatestExistentSearcher(myVcs, svnStatus.getURL());
        revision = searcher.getDeletionRevision();
      }

      return createResult(SVNRevision.create(revision), exists, false);
    }
    final SVNRevision remoteRevision = svnStatus.getRemoteRevision();
    if (remoteRevision != null) {
      return createResult(remoteRevision, exists, false);
    }
    return createResult(svnStatus.getRevision(), exists, false);
  }

  private boolean itemExists(SVNStatus svnStatus) {
    return ! SVNStatusType.STATUS_DELETED.equals(svnStatus.getRemoteContentsStatus()) &&
      ! SVNStatusType.STATUS_DELETED.equals(svnStatus.getRemoteNodeStatus());
  }
}
