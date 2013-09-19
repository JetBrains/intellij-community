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
public class SvnSvnkitUpdateClient implements SvnUpdateClientI {
  private final SVNUpdateClient myClient;
  private ISVNEventHandler myDispatcher;

  public SvnSvnkitUpdateClient(SVNUpdateClient client) {
    myClient = client;
  }

  @Override
  public long[] doUpdate(File[] paths,
                         SVNRevision revision,
                         SVNDepth depth,
                         boolean allowUnversionedObstructions,
                         boolean depthIsSticky,
                         boolean makeParents) throws SVNException {
    return myClient.doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky, makeParents);
  }

  @Override
  public long doUpdate(File path, SVNRevision revision, SVNDepth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SVNException {
    return myClient.doUpdate(path, revision, depth, allowUnversionedObstructions, depthIsSticky);
  }

  @Override
  public void setUpdateLocksOnDemand(boolean locksOnDemand) {
    myClient.setUpdateLocksOnDemand(locksOnDemand);
  }

  @Override
  public long doSwitch(File path,
                       SVNURL url,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean allowUnversionedObstructions, boolean depthIsSticky) throws SVNException {
    return myClient.doSwitch(path, url, pegRevision, revision, depth, allowUnversionedObstructions, depthIsSticky);
  }

  @Override
  public void setEventHandler(ISVNEventHandler dispatcher) {
    myDispatcher = dispatcher;
    myClient.setEventHandler(dispatcher);
  }

  @Override
  public void setIgnoreExternals(boolean ignoreExternals) {
    myClient.setIgnoreExternals(ignoreExternals);
  }

  public ISVNEventHandler getEventHandler() {
    return myDispatcher;
  }
}
