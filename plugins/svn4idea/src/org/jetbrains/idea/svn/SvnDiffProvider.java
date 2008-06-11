/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.history.LatestExistentSearcher;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;

public class SvnDiffProvider implements DiffProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnDiffProvider");
  private final SvnVcs myVcs;

  public SvnDiffProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    final SVNStatusClient client = myVcs.createStatusClient();
    try {
      final SVNStatus svnStatus = client.doStatus(new File(file.getPresentableUrl()), false, false);
      if (svnStatus.getCommittedRevision().equals(SVNRevision.UNDEFINED) && svnStatus.isCopied()) {
        return new SvnRevisionNumber(svnStatus.getCopyFromRevision());
      }
      return new SvnRevisionNumber(svnStatus.getRevision());
    }
    catch (SVNException e) {
      LOG.debug(e);    // most likely the file is unversioned
      return null;
    }
  }

  private static Pair<Boolean, VcsRevisionNumber> defaultResult() {
    return createResult(Boolean.TRUE, SVNRevision.HEAD);
  }

  private static Pair<Boolean, VcsRevisionNumber> createResult(final Boolean exists, final SVNRevision revision) {
    return new Pair<Boolean, VcsRevisionNumber>(exists, new SvnRevisionNumber(revision));
  }

  public Pair<Boolean, VcsRevisionNumber> getLastRevision(VirtualFile file) {
    final SVNStatusClient client = myVcs.createStatusClient();
    try {
      final SVNStatus svnStatus = client.doStatus(new File(file.getPresentableUrl()), true, false);
      if (svnStatus == null) {
        // IDEADEV-21785 (no idea why this can happen)
        LOG.info("No SVN status returned for " + file.getPath());
        return defaultResult();
      }
      final boolean exists = ! SVNStatusType.STATUS_DELETED.equals(svnStatus.getRemoteContentsStatus());
      if (! exists) {
        // get really latest revision
        final LatestExistentSearcher searcher = new LatestExistentSearcher(myVcs, svnStatus.getURL());
        final long revision = searcher.getDeletionRevision();

        return createResult(exists, SVNRevision.create(revision));
      }
      final SVNRevision remoteRevision = svnStatus.getRemoteRevision();
      if (remoteRevision != null) {
        return createResult(exists, remoteRevision);
      }
      return createResult(exists, svnStatus.getRevision());
    }
    catch (SVNException e) {
      LOG.debug(e);    // most likely the file is unversioned
      return defaultResult();
    }
  }

  public ContentRevision createFileContent(final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile) {
    final SVNRevision svnRevision = ((SvnRevisionNumber)revisionNumber).getRevision();
    FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(selectedFile);
    final SVNStatusClient client = myVcs.createStatusClient();
    try {
      final SVNStatus svnStatus = client.doStatus(new File(selectedFile.getPresentableUrl()), false, false);
      if (svnRevision.equals(svnStatus.getRevision())) {
        return SvnContentRevision.create(myVcs, filePath, svnRevision);
      }
    }
    catch (SVNException e) {
      LOG.debug(e);    // most likely the file is unversioned
    }
    return SvnContentRevision.createRemote(myVcs, filePath, svnRevision);
  }
}
