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
package org.jetbrains.idea.svn17.portable;

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
  public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {
    return myClient.doUpdate(file, revision, recursive);
  }

  @Override
  public long doUpdate(File file, SVNRevision revision, boolean recursive, boolean force) throws SVNException {
    return myClient.doUpdate(file, revision, recursive, force);
  }

  @Override
  public long[] doUpdate(File[] paths,
                         SVNRevision revision,
                         SVNDepth depth,
                         boolean allowUnversionedObstructions,
                         boolean depthIsSticky) throws SVNException {
    return myClient.doUpdate(paths, revision, depth, allowUnversionedObstructions, depthIsSticky);
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
  public long doSwitch(File file, SVNURL url, SVNRevision revision, boolean recursive) throws SVNException {
    return myClient.doSwitch(file, url, revision, recursive);
  }

  @Override
  public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
    return myClient.doSwitch(file, url, pegRevision, revision, recursive);
  }

  @Override
  public long doSwitch(File file, SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force)
    throws SVNException {
    return myClient.doSwitch(file, url, pegRevision, revision, recursive, force);
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
  public long doSwitch(File path,
                       SVNURL url,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       SVNDepth depth,
                       boolean allowUnversionedObstructions, boolean depthIsSticky, boolean ignoreAncestry) throws SVNException {
    return myClient.doSwitch(path, url, pegRevision, revision, depth, allowUnversionedObstructions, depthIsSticky, ignoreAncestry);
  }

  @Override
  public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
    return myClient.doCheckout(url, dstPath, pegRevision, revision, recursive);
  }

  @Override
  public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive, boolean force)
    throws SVNException {
    return myClient.doCheckout(url, dstPath, pegRevision, revision, recursive, force);
  }

  @Override
  public long doCheckout(SVNURL url,
                         File dstPath,
                         SVNRevision pegRevision,
                         SVNRevision revision,
                         SVNDepth depth,
                         boolean allowUnversionedObstructions) throws SVNException {
    return myClient.doCheckout(url, dstPath, pegRevision, revision, depth, allowUnversionedObstructions);
  }

  @Override
  public long doExport(SVNURL url,
                       File dstPath,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       String eolStyle,
                       boolean force,
                       boolean recursive) throws SVNException {
    return myClient.doExport(url, dstPath, pegRevision, revision, eolStyle, force, recursive);
  }

  @Override
  public long doExport(SVNURL url,
                       File dstPath,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       String eolStyle,
                       boolean overwrite,
                       SVNDepth depth) throws SVNException {
    return myClient.doExport(url, dstPath, pegRevision, revision, eolStyle, overwrite, depth);
  }

  @Override
  public long doExport(File srcPath,
                       File dstPath,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       String eolStyle,
                       boolean force,
                       boolean recursive) throws SVNException {
    return myClient.doExport(srcPath, dstPath, pegRevision, revision, eolStyle, force, recursive);
  }

  @Override
  public long doExport(File srcPath,
                       File dstPath,
                       SVNRevision pegRevision,
                       SVNRevision revision,
                       String eolStyle,
                       boolean overwrite,
                       SVNDepth depth) throws SVNException {
    return myClient.doExport(srcPath, dstPath, pegRevision, revision, eolStyle, overwrite, depth);
  }

  @Override
  public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL, boolean recursive) throws SVNException {
    myClient.doRelocate(dst, oldURL, newURL, recursive);
  }

  @Override
  public void doCanonicalizeURLs(File dst, boolean omitDefaultPort, boolean recursive) throws SVNException {
    myClient.doCanonicalizeURLs(dst, omitDefaultPort, recursive);
  }

  @Override
  public void setExportExpandsKeywords(boolean expand) {
    myClient.setExportExpandsKeywords(expand);
  }

  @Override
  public void setEventHandler(ISVNEventHandler dispatcher) {
    myDispatcher = dispatcher;
    myClient.setEventHandler(dispatcher);
  }

  public ISVNEventHandler getEventHandler() {
    return myDispatcher;
  }
}
