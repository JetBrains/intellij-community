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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Map;

public class LatestExistentSearcher {
  private long myStartNumber;
  private boolean myStartExistsKnown;
  private final SVNURL myUrl;
  private final SvnVcs myVcs;
  private long myEndNumber;

  public LatestExistentSearcher(final SvnVcs vcs, final SVNURL url) {
    this(0, -1, false, vcs, url);
  }

  public LatestExistentSearcher(final long startNumber, final long endNumber, final boolean startExistsKnown, final SvnVcs vcs, final SVNURL url) {
    myStartNumber = startNumber;
    myEndNumber = endNumber;
    myStartExistsKnown = startExistsKnown;
    myVcs = vcs;
    myUrl = url;
  }

  public long getDeletionRevision() {
    if (! detectStartRevision()) return -1;

    final Ref<Long> latest = new Ref<Long>(myStartNumber);
    SVNRepository repository = null;
    try {
      repository = myVcs.createRepository(myUrl.toString());
      final SVNURL repRoot = repository.getRepositoryRoot(true);
      if (repRoot != null) {
        if (myEndNumber == -1) {
          myEndNumber = repository.getLatestRevision();
        }

        final SVNURL existingParent = getExistingParent(myUrl, repository, repRoot.toString().length());
        if (existingParent == null) {
          return myStartNumber;
        }

        final String urlRelativeString = myUrl.toString().substring(repRoot.toString().length());
        final SVNRevision startRevision = SVNRevision.create(myStartNumber);
        myVcs.createLogClient().doLog(existingParent, new String[]{""}, startRevision, startRevision, SVNRevision.HEAD, false, true, 0,
                       new ISVNLogEntryHandler() {
                         public void handleLogEntry(final SVNLogEntry logEntry) throws SVNException {
                           final Map changedPaths = logEntry.getChangedPaths();
                           for (Object o : changedPaths.values()) {
                             final SVNLogEntryPath path = (SVNLogEntryPath) o;
                             if ((path.getType() == 'D') && (urlRelativeString.equals(path.getPath()))) {
                               latest.set(logEntry.getRevision());
                               throw new SVNException(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE);
                             }
                           }
                         }
                       });
      }
    }
    catch (SVNException e) {
      //
    } finally {
      if (repository != null) {
        repository.closeSession();
      }
    }

    return latest.get().longValue();
  }

  public long getLatestExistent() {
    if (! detectStartRevision()) return myStartNumber;

    SVNRepository repository = null;
    long latestOk = myStartNumber;
    try {
      repository = myVcs.createRepository(myUrl.toString());
      final SVNURL repRoot = repository.getRepositoryRoot(true);
      if (repRoot != null) {
        if (myEndNumber == -1) {
          myEndNumber = repository.getLatestRevision();
        }
        final String urlString = myUrl.toString().substring(repRoot.toString().length());
        for (long i = myStartNumber + 1; i < myEndNumber; i++) {
          final SVNNodeKind kind = repository.checkPath(urlString, i);
          if (SVNNodeKind.DIR.equals(kind) || SVNNodeKind.FILE.equals(kind)) {
            latestOk = i;
          }
        }
      }
    }
    catch (SVNException e) {
      //
    } finally {
      if (repository != null) {
        repository.closeSession();
      }
    }

    return latestOk;
  }

  private boolean detectStartRevision() {
    if (! myStartExistsKnown) {
      final SvnFileUrlMapping mapping = myVcs.getSvnFileUrlMapping();
      final RootUrlInfo rootUrlInfo = mapping.getWcRootForUrl(myUrl.toString());
      if (rootUrlInfo == null) return true;
      final VirtualFile vf = rootUrlInfo.getVirtualFile();
      if (vf == null) {
        return true;
      }
      final SVNInfo info = myVcs.getInfo(vf);
      if ((info == null) || (info.getRevision() == null)) {
        return false;
      }
      myStartNumber = info.getRevision().getNumber();
      myStartExistsKnown = true;
    }
    return true;
  }

  @Nullable
  private SVNURL getExistingParent(final SVNURL url, final SVNRepository repository, final int repoRootLen) throws SVNException {
    final String urlString = url.toString().substring(repoRootLen);
    if (urlString.length() == 0) {
      // === repository url
      return url;
    }
    final SVNNodeKind kind = repository.checkPath(urlString, myEndNumber);
    if (SVNNodeKind.DIR.equals(kind) || SVNNodeKind.FILE.equals(kind)) {
      return url;
    }
    final SVNURL parentUrl = url.removePathTail();
    if (parentUrl == null) {
      return null;
    }
    return getExistingParent(parentUrl, repository, repoRootLen);
  }
}
