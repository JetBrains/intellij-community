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
package org.jetbrains.idea.svn.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/1/12
 * Time: 12:11 PM
 */
public class SvnKitUpdateClient extends BaseSvnClient implements UpdateClient {

  @Nullable protected ISVNEventHandler myDispatcher;
  protected boolean myIgnoreExternals;
  protected boolean myLocksOnDemand;

  @Override
  public long[] doUpdate(File[] paths,
                         SVNRevision revision,
                         SVNDepth depth,
                         boolean allowUnversionedObstructions,
                         boolean depthIsSticky,
                         boolean makeParents) throws SVNException {
    return getClient().doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky, makeParents);
  }

  @Override
  public long doUpdate(File path, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SVNException {
    return getClient().doUpdate(path, revision, depth, allowUnversionedObstructions, depthIsSticky);
  }

  @Override
  public long doSwitch(File path,
                       SVNURL url,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean allowUnversionedObstructions, boolean depthIsSticky) throws SVNException {
    return getClient().doSwitch(path, url, pegRevision, revision, depth, allowUnversionedObstructions, depthIsSticky);
  }

  @Override
  public void setUpdateLocksOnDemand(boolean locksOnDemand) {
    myLocksOnDemand = locksOnDemand;
  }

  @Override
  public void setEventHandler(ISVNEventHandler dispatcher) {
    myDispatcher = dispatcher;
  }

  @Override
  public void setIgnoreExternals(boolean ignoreExternals) {
    myIgnoreExternals = ignoreExternals;
  }

  @NotNull
  private SVNUpdateClient getClient() {
    SVNUpdateClient client = myVcs.createUpdateClient();

    client.setEventHandler(myDispatcher);
    client.setIgnoreExternals(myIgnoreExternals);
    client.setUpdateLocksOnDemand(myLocksOnDemand);

    return client;
  }
}
