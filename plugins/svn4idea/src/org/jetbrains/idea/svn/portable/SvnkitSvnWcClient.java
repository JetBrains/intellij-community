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
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/20/12
 * Time: 6:55 PM
 */
public class SvnkitSvnWcClient implements SvnWcClientI {
  @NotNull
  private final SvnVcs myVcs;

  public SvnkitSvnWcClient(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  public SVNWCClient getClient() {
    return myVcs.createWCClient();
  }

  @Override
  public void setAddParameters(ISVNAddParameters addParameters) {
    getClient().setAddParameters(addParameters);
  }

  @Override
  public ISVNCommitHandler getCommitHandler() {
    return getClient().getCommitHandler();
  }

  @Override
  public void setCommitHandler(ISVNCommitHandler handler) {
    getClient().setCommitHandler(handler);
  }

  @Override
  public void doGetFileContents(File path, SVNRevision pegRevision, SVNRevision revision, boolean expandKeywords, OutputStream dst)
    throws SVNException {
    getClient().doGetFileContents(path, pegRevision, revision, expandKeywords, dst);
  }

  @Override
  public void doGetFileContents(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean expandKeywords, OutputStream dst)
    throws SVNException {
    getClient().doGetFileContents(url, pegRevision, revision, expandKeywords, dst);
  }

  @Override
  public void doCleanup(File path) throws SVNException {
    getClient().doCleanup(path);
  }

  @Override
  public void doCleanup(File path, boolean deleteWCProperties) throws SVNException {
    getClient().doCleanup(path, deleteWCProperties);
  }

  @Override
  public void doSetProperty(File path,
                            String propName,
                            SVNPropertyValue propValue,
                            boolean skipChecks,
                            SVNDepth depth,
                            ISVNPropertyHandler handler,
                            Collection changeLists) throws SVNException {
    getClient().doSetProperty(path, propName, propValue, skipChecks, depth, handler, changeLists);
  }

  @Override
  public void doSetProperty(File path,
                            ISVNPropertyValueProvider propertyValueProvider,
                            boolean skipChecks,
                            SVNDepth depth,
                            ISVNPropertyHandler handler,
                            Collection changeLists) throws SVNException {
    getClient().doSetProperty(path, propertyValueProvider, skipChecks, depth, handler, changeLists);
  }

  @Override
  public SVNCommitInfo doSetProperty(SVNURL url,
                                     String propName,
                                     SVNPropertyValue propValue,
                                     SVNRevision baseRevision,
                                     String commitMessage,
                                     SVNProperties revisionProperties,
                                     boolean skipChecks,
                                     ISVNPropertyHandler handler) throws SVNException {
    return getClient().doSetProperty(url, propName, propValue, baseRevision, commitMessage, revisionProperties, skipChecks, handler);
  }

  @Override
  public void doSetRevisionProperty(File path,
                                    SVNRevision revision,
                                    String propName,
                                    SVNPropertyValue propValue,
                                    boolean force,
                                    ISVNPropertyHandler handler) throws SVNException {
    getClient().doSetRevisionProperty(path, revision, propName, propValue, force, handler);
  }

  @Override
  public void doSetRevisionProperty(SVNURL url,
                                    SVNRevision revision,
                                    String propName,
                                    SVNPropertyValue propValue,
                                    boolean force,
                                    ISVNPropertyHandler handler) throws SVNException {
    getClient().doSetRevisionProperty(url, revision, propName, propValue, force, handler);
  }

  @Override
  public SVNPropertyData doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
    return getClient().doGetProperty(path, propName, pegRevision, revision);
  }

  @Override
  public SVNPropertyData doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
    return getClient().doGetProperty(url, propName, pegRevision, revision);
  }

  @Override
  public void doGetProperty(File path,
                            String propName,
                            SVNRevision pegRevision,
                            SVNRevision revision,
                            boolean recursive,
                            ISVNPropertyHandler handler) throws SVNException {
    getClient().doGetProperty(path, propName, pegRevision, revision, recursive, handler);
  }

  @Override
  public void doGetProperty(File path,
                            String propName,
                            SVNRevision pegRevision,
                            SVNRevision revision,
                            SVNDepth depth,
                            ISVNPropertyHandler handler,
                            Collection changeLists) throws SVNException {
    getClient().doGetProperty(path, propName, pegRevision, revision, depth, handler, changeLists);
  }

  @Override
  public void doGetProperty(SVNURL url,
                            String propName,
                            SVNRevision pegRevision,
                            SVNRevision revision,
                            boolean recursive,
                            ISVNPropertyHandler handler) throws SVNException {
    getClient().doGetProperty(url, propName, pegRevision, revision, recursive, handler);
  }

  @Override
  public void doGetProperty(SVNURL url,
                            String propName,
                            SVNRevision pegRevision,
                            SVNRevision revision,
                            SVNDepth depth,
                            ISVNPropertyHandler handler) throws SVNException {
    getClient().doGetProperty(url, propName, pegRevision, revision, depth, handler);
  }

  @Override
  public void doGetRevisionProperty(File path, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
    getClient().doGetRevisionProperty(path, propName, revision, handler);
  }

  @Override
  public long doGetRevisionProperty(SVNURL url, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
    return getClient().doGetRevisionProperty(url, propName, revision, handler);
  }

  @Override
  public void doDelete(File path, boolean force, boolean dryRun) throws SVNException {
    getClient().doDelete(path, force, dryRun);
  }

  @Override
  public void doDelete(File path, boolean force, boolean deleteFiles, boolean dryRun) throws SVNException {
    getClient().doDelete(path, force, deleteFiles, dryRun);
  }

  @Override
  public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, boolean recursive) throws SVNException {
    getClient().doAdd(path, force, mkdir, climbUnversionedParents, recursive);
  }

  @Override
  public void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents, boolean recursive, boolean includeIgnored)
    throws SVNException {
    getClient().doAdd(path, force, mkdir, climbUnversionedParents, recursive, includeIgnored);
  }

  @Override
  public void doAdd(File path,
                    boolean force,
                    boolean mkdir,
                    boolean climbUnversionedParents,
                    SVNDepth depth,
                    boolean includeIgnored,
                    boolean makeParents) throws SVNException {
    getClient().doAdd(path, force, mkdir, climbUnversionedParents, depth, includeIgnored, makeParents);
  }

  @Override
  public void doAdd(File[] paths,
                    boolean force,
                    boolean mkdir,
                    boolean climbUnversionedParents,
                    SVNDepth depth,
                    boolean depthIsSticky,
                    boolean includeIgnored,
                    boolean makeParents) throws SVNException {
    getClient().doAdd(paths, force, mkdir, climbUnversionedParents, depth, depthIsSticky, includeIgnored, makeParents);
  }

  @Override
  public void doAdd(File path,
                    boolean force,
                    boolean mkdir,
                    boolean climbUnversionedParents,
                    SVNDepth depth,
                    boolean depthIsSticky,
                    boolean includeIgnored,
                    boolean makeParents) throws SVNException {
    getClient().doAdd(path, force, mkdir, climbUnversionedParents, depth, depthIsSticky, includeIgnored, makeParents);
  }

  @Override
  public void doMarkReplaced(File path) throws SVNException {
    getClient().doMarkReplaced(path);
  }

  @Override
  public void doRevert(File path, boolean recursive) throws SVNException {
    getClient().doRevert(path, recursive);
  }

  @Override
  public void doRevert(File[] paths, boolean recursive) throws SVNException {
    getClient().doRevert(paths, recursive);
  }

  @Override
  public void doRevert(File[] paths, SVNDepth depth, Collection changeLists) throws SVNException {
    getClient().doRevert(paths, depth, changeLists);
  }

  @Override
  public void doResolve(File path, boolean recursive) throws SVNException {
    getClient().doResolve(path, recursive);
  }

  @Override
  public void doResolve(File path, SVNDepth depth, SVNConflictChoice conflictChoice) throws SVNException {
    getClient().doResolve(path, depth, conflictChoice);
  }

  @Override
  public void doResolve(File path, SVNDepth depth, boolean resolveContents, boolean resolveProperties, SVNConflictChoice conflictChoice)
    throws SVNException {
    getClient().doResolve(path, depth, resolveContents, resolveProperties, conflictChoice);
  }

  @Override
  public void doResolve(File path,
                        SVNDepth depth,
                        boolean resolveContents,
                        boolean resolveProperties,
                        boolean resolveTree,
                        SVNConflictChoice conflictChoice) throws SVNException {
    getClient().doResolve(path, depth, resolveContents, resolveProperties, resolveTree, conflictChoice);
  }

  @Override
  public void doLock(File[] paths, boolean stealLock, String lockMessage) throws SVNException {
    getClient().doLock(paths, stealLock, lockMessage);
  }

  @Override
  public void doLock(SVNURL[] urls, boolean stealLock, String lockMessage) throws SVNException {
    getClient().doLock(urls, stealLock, lockMessage);
  }

  @Override
  public void doUnlock(File[] paths, boolean breakLock) throws SVNException {
    getClient().doUnlock(paths, breakLock);
  }

  @Override
  public void doUnlock(SVNURL[] urls, boolean breakLock) throws SVNException {
    getClient().doUnlock(urls, breakLock);
  }

  @Override
  public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
    getClient().doInfo(path, revision, recursive, handler);
  }

  @Override
  public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    getClient().doInfo(path, pegRevision, revision, recursive, handler);
  }

  @Override
  public void doInfo(File path,
                     SVNRevision pegRevision,
                     SVNRevision revision,
                     SVNDepth depth,
                     Collection changeLists,
                     ISVNInfoHandler handler) throws SVNException {
    getClient().doInfo(path, pegRevision, revision, depth, changeLists, handler);
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    getClient().doInfo(url, pegRevision, revision, recursive, handler);
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, ISVNInfoHandler handler)
    throws SVNException {
    getClient().doInfo(url, pegRevision, revision, depth, handler);
  }

  @Override
  public String doGetWorkingCopyID(File path, String trailURL) throws SVNException {
    return getClient().doGetWorkingCopyID(path, trailURL);
  }

  @Override
  public String doGetWorkingCopyID(File path, String trailURL, boolean committed) throws SVNException {
    return getClient().doGetWorkingCopyID(path, trailURL, committed);
  }

  @Override
  public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
    return getClient().doInfo(path, revision);
  }

  @Override
  public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
    return getClient().doInfo(url, pegRevision, revision);
  }

  @Override
  public void doCleanupWCProperties(File directory) throws SVNException {
    getClient().doCleanupWCProperties(directory);
  }

  @Override
  public void doSetWCFormat(File directory, int format) throws SVNException {
    getClient().doSetWCFormat(directory, format);
  }

  @Override
  public void doSetProperty(File path,
                            String propName,
                            SVNPropertyValue propValue,
                            boolean force,
                            boolean recursive,
                            ISVNPropertyHandler handler) throws SVNException {
    getClient().doSetProperty(path, propName, propValue, force, recursive, handler);
  }
}
