/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.portable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 9:47 AM
 */
public class SvnkitSvnStatusClient implements SvnStatusClientI {

  @Nullable private final SVNStatusClient myStatusClient;
  @NotNull private final SvnVcs myVcs;

  public SvnkitSvnStatusClient(@NotNull SvnVcs vcs, @Nullable SVNStatusClient statusClient) {
    myVcs = vcs;
    myStatusClient = statusClient;
  }

  @Override
  public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler)
    throws SVNException {
    return getStatusClient().doStatus(path, recursive, remote, reportAll, includeIgnored, handler);
  }

  @Override
  public long doStatus(File path,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return getStatusClient().doStatus(path, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler);
  }

  @Override
  public long doStatus(File path,
                       SVNRevision revision,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return getStatusClient().doStatus(path, revision, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler);
  }

  @Override
  public long doStatus(File path,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler,
                       Collection changeLists) throws SVNException {
    return getStatusClient()
      .doStatus(path, revision, depth, remote, reportAll, includeIgnored, collectParentExternals, handler, changeLists);
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote) throws SVNException {
    return getStatusClient().doStatus(path, remote);
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException {
    return getStatusClient().doStatus(path, remote, collectParentExternals);
  }

  @NotNull
  private SVNStatusClient getStatusClient() {
    return myStatusClient != null ? myStatusClient : myVcs.createStatusClient();
  }
}
