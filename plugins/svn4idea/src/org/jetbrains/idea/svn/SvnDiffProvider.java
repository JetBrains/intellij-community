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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.LatestExistentSearcher;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.info.InfoConsumer;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.List;
import java.util.Map;

public class SvnDiffProvider extends DiffProviderEx implements DiffProvider, DiffMixin {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnDiffProvider");

  public static final String COMMIT_MESSAGE = "svn:log";
  private static final int BATCH_INFO_SIZE = 20;

  @NotNull private final SvnVcs myVcs;

  public SvnDiffProvider(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision(@NotNull VirtualFile file) {
    final Info svnInfo = myVcs.getInfo(VfsUtilCore.virtualToIoFile(file));

    return getRevision(svnInfo);
  }

  @Nullable
  private static VcsRevisionNumber getRevision(@Nullable Info info) {
    VcsRevisionNumber result = null;

    if (info != null) {
      SVNRevision revision = SVNRevision.UNDEFINED.equals(info.getCommittedRevision()) && info.getCopyFromRevision() != null
                             ? info.getCopyFromRevision()
                             : info.getRevision();

      result = new SvnRevisionNumber(revision);
    }

    return result;
  }

  @NotNull
  @Override
  public Map<VirtualFile, VcsRevisionNumber> getCurrentRevisions(@NotNull Iterable<VirtualFile> files) {
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

  private void collectRevisionsInBatch(@NotNull Map<VirtualFile, VcsRevisionNumber> revisionMap,
                                       @NotNull Map<String, VirtualFile> fileMap,
                                       @NotNull List<File> ioFiles) {
    myVcs.collectInfo(ioFiles, createInfoHandler(revisionMap, fileMap));
  }

  @NotNull
  private static InfoConsumer createInfoHandler(@NotNull final Map<VirtualFile, VcsRevisionNumber> revisionMap,
                                                @NotNull final Map<String, VirtualFile> fileMap) {
    return new InfoConsumer() {
      @Override
      public void consume(Info info) throws SVNException {
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

  @Nullable
  @Override
  public VcsRevisionDescription getCurrentRevisionDescription(@NotNull VirtualFile file) {
    return getCurrentRevisionDescription(VfsUtilCore.virtualToIoFile(file));
  }

  @Nullable
  private VcsRevisionDescription getCurrentRevisionDescription(@NotNull File path) {
    final Info svnInfo = myVcs.getInfo(path);
    if (svnInfo == null) {
      return null;
    }

    if (svnInfo.getCommittedRevision().equals(SVNRevision.UNDEFINED) &&
        !svnInfo.getCopyFromRevision().equals(SVNRevision.UNDEFINED) &&
        svnInfo.getCopyFromURL() != null) {
      String localPath = myVcs.getSvnFileUrlMapping().getLocalPath(svnInfo.getCopyFromURL().toString());

      if (localPath != null) {
        return getCurrentRevisionDescription(new File(localPath));
      }
    }

    return new VcsRevisionDescriptionImpl(new SvnRevisionNumber(svnInfo.getCommittedRevision()), svnInfo.getCommittedDate(),
                                          svnInfo.getAuthor(), getCommitMessage(path, svnInfo));
  }

  @Nullable
  private String getCommitMessage(@NotNull File path, @NotNull Info info) {
    String result;

    try {
      PropertyValue property =
        myVcs.getFactory(path).createPropertyClient()
          .getProperty(SvnTarget.fromFile(path), COMMIT_MESSAGE, true, info.getCommittedRevision());

      result = PropertyValue.toString(property);
    }
    catch (VcsException e) {
      LOG.info("Failed to get commit message for file " + path + ", " + info.getCommittedRevision() + ", " + info.getRevision(), e);
      result = "";
    }

    return result;
  }

  @NotNull
  private static ItemLatestState defaultResult() {
    return createResult(SVNRevision.HEAD, true, true);
  }

  @NotNull
  private static ItemLatestState createResult(@NotNull SVNRevision revision, boolean exists, boolean defaultHead) {
    return new ItemLatestState(new SvnRevisionNumber(revision), exists, defaultHead);
  }

  @NotNull
  @Override
  public ItemLatestState getLastRevision(@NotNull VirtualFile file) {
    return getLastRevision(VfsUtilCore.virtualToIoFile(file));
  }

  @NotNull
  @Override
  public ContentRevision createFileContent(@NotNull VcsRevisionNumber revisionNumber, @NotNull VirtualFile selectedFile) {
    FilePath filePath = VcsUtil.getFilePath(selectedFile);
    SVNRevision svnRevision = ((SvnRevisionNumber)revisionNumber).getRevision();

    if (!SVNRevision.HEAD.equals(svnRevision) && revisionNumber.equals(getCurrentRevision(selectedFile))) {
      return SvnContentRevision.createBaseRevision(myVcs, filePath, svnRevision);
    }

    // not clear why we need it, with remote check..
    Status svnStatus = getFileStatus(VfsUtilCore.virtualToIoFile(selectedFile), false);

    return svnStatus != null && svnRevision.equals(svnStatus.getRevision())
           ? SvnContentRevision.createBaseRevision(myVcs, filePath, svnRevision)
           : SvnContentRevision.createRemote(myVcs, filePath, svnRevision);
  }

  @Nullable
  private Status getFileStatus(@NotNull File file, boolean remote) {
    Status result = null;

    try {
      result = myVcs.getFactory(file).createStatusClient().doStatus(file, remote);
    }
    catch (SvnBindException e) {
      LOG.debug(e);
    }

    return result;
  }

  @NotNull
  @Override
  public ItemLatestState getLastRevision(@NotNull FilePath filePath) {
    return getLastRevision(filePath.getIOFile());
  }

  @Nullable
  @Override
  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }

  @NotNull
  private ItemLatestState getLastRevision(@NotNull File file) {
    Status svnStatus = getFileStatus(file, true);

    if (svnStatus == null || itemExists(svnStatus) && SVNRevision.UNDEFINED.equals(svnStatus.getRemoteRevision())) {
      // IDEADEV-21785 (no idea why this can happen)
      final Info info = myVcs.getInfo(file, SVNRevision.HEAD);
      if (info == null || info.getURL() == null) {
        LOG.info("No SVN status returned for " + file.getPath());
        return defaultResult();
      }
      return createResult(info.getCommittedRevision(), true, false);
    }

    if (!itemExists(svnStatus)) {
      return createResult(getLastExistingRevision(file, svnStatus), false, false);
    }
    return createResult(ObjectUtils.notNull(svnStatus.getRemoteRevision(), svnStatus.getRevision()), true, false);
  }

  @NotNull
  private SVNRevision getLastExistingRevision(@NotNull File file, @NotNull Status svnStatus) {
    WorkingCopyFormat format = myVcs.getWorkingCopyFormat(file);
    long revision = -1;

    // skipped for >= 1.8
    if (format.less(WorkingCopyFormat.ONE_DOT_EIGHT)) {
      // get really latest revision
      // TODO: Algorithm seems not to be correct in all cases - for instance, when some subtree was deleted and replaced by other
      // TODO: with same names. pegRevision should be used somehow but this complicates the algorithm
      if (svnStatus.getRepositoryRootURL() != null) {
        revision = new LatestExistentSearcher(myVcs, svnStatus.getURL(), svnStatus.getRepositoryRootURL()).getDeletionRevision();
      }
      else {
        LOG.info("Could not find repository url for file " + file);
      }
    }

    return SVNRevision.create(revision);
  }

  private static boolean itemExists(@NotNull Status svnStatus) {
    return !StatusType.STATUS_DELETED.equals(svnStatus.getRemoteContentsStatus()) &&
           !StatusType.STATUS_DELETED.equals(svnStatus.getRemoteNodeStatus());
  }
}
