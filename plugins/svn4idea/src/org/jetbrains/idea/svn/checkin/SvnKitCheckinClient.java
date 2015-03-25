/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCheckinClient extends BaseSvnClient implements CheckinClient {

  private static final Logger LOG = Logger.getInstance(SvnKitCheckinClient.class);

  @NotNull
  @Override
  public CommitInfo[] commit(@NotNull List<File> paths, @NotNull String comment) throws VcsException {
    File[] pathsToCommit = ArrayUtil.toObjectArray(paths, File.class);
    boolean keepLocks = myVcs.getSvnConfiguration().isKeepLocks();
    SVNCommitPacket[] commitPackets = null;
    SVNCommitInfo[] results;
    SVNCommitClient committer = myVcs.getSvnKitManager().createCommitClient();
    IdeaCommitHandler handler = new IdeaCommitHandler(ProgressManager.getInstance().getProgressIndicator(), true, true);

    committer.setEventHandler(toEventHandler(handler));
    try {
      commitPackets = committer.doCollectCommitItems(pathsToCommit, keepLocks, true, SVNDepth.EMPTY, true, null);
      results = committer.doCommit(commitPackets, keepLocks, comment);
      commitPackets = null;
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
    finally {
      if (commitPackets != null) {
        for (SVNCommitPacket commitPacket : commitPackets) {
          try {
            commitPacket.dispose();
          }
          catch (SVNException e) {
            LOG.info(e);
          }
        }
      }
    }

    // This seems to be necessary only for SVNKit as changes after command line operations should be detected during VFS refresh.
    for (VirtualFile f : handler.getDeletedFiles()) {
      f.putUserData(VirtualFile.REQUESTOR_MARKER, this);
    }

    return convert(results);
  }

  @NotNull
  private static CommitInfo[] convert(@NotNull SVNCommitInfo[] infos) {
    return ContainerUtil.map(infos, new Function<SVNCommitInfo, CommitInfo>() {
      @Override
      public CommitInfo fun(SVNCommitInfo info) {
        return new CommitInfo.Builder(info.getNewRevision(), info.getDate(), info.getAuthor())
          .setError(info.getErrorMessage()).build();
      }
    }, new CommitInfo[0]);
  }
}
