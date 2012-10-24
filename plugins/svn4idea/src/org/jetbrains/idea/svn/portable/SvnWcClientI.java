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

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/20/12
 * Time: 6:54 PM
 */
public interface SvnWcClientI extends SvnMarkerInterface {
  void setAddParameters(ISVNAddParameters addParameters);
  ISVNCommitHandler getCommitHandler();
  void setCommitHandler(ISVNCommitHandler handler);
  void doGetFileContents(File path, SVNRevision pegRevision, SVNRevision revision,
          boolean expandKeywords, OutputStream dst) throws SVNException;
  void doGetFileContents(SVNURL url, SVNRevision pegRevision, SVNRevision revision,
          boolean expandKeywords, OutputStream dst) throws SVNException;
  void doCleanup(File path) throws SVNException;
  void doCleanup(File path, boolean deleteWCProperties) throws SVNException;
  void doSetProperty(File path, String propName, SVNPropertyValue propValue, boolean skipChecks,
          SVNDepth depth, ISVNPropertyHandler handler, Collection changeLists) throws SVNException;
  void doSetProperty(File path, ISVNPropertyValueProvider propertyValueProvider, boolean skipChecks,
          SVNDepth depth, ISVNPropertyHandler handler, Collection changeLists) throws SVNException;
  SVNCommitInfo doSetProperty(SVNURL url, String propName, SVNPropertyValue propValue,
          SVNRevision baseRevision, String commitMessage, SVNProperties revisionProperties,
          boolean skipChecks, ISVNPropertyHandler handler) throws SVNException;
  void doSetRevisionProperty(File path, SVNRevision revision, String propName,
          SVNPropertyValue propValue, boolean force, ISVNPropertyHandler handler) throws SVNException;
  void doSetRevisionProperty(SVNURL url, SVNRevision revision, String propName,
          SVNPropertyValue propValue, boolean force, ISVNPropertyHandler handler) throws SVNException;
  SVNPropertyData doGetProperty( File path, String propName, SVNRevision pegRevision,
          SVNRevision revision) throws SVNException;
  SVNPropertyData doGetProperty( SVNURL url, String propName, SVNRevision pegRevision,
          SVNRevision revision) throws SVNException;
  void doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision,
          boolean recursive, ISVNPropertyHandler handler) throws SVNException;
  void doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision,
          SVNDepth depth, ISVNPropertyHandler handler, Collection changeLists) throws SVNException;
  void doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision,
          boolean recursive, ISVNPropertyHandler handler) throws SVNException;
  void doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision,
          SVNDepth depth, ISVNPropertyHandler handler) throws SVNException;
  void doGetRevisionProperty(File path, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException;
  long doGetRevisionProperty(SVNURL url, String propName, SVNRevision revision,
          ISVNPropertyHandler handler) throws SVNException;
  void doDelete(File path, boolean force, boolean dryRun) throws SVNException;
  void doDelete(File path, boolean force, boolean deleteFiles, boolean dryRun) throws SVNException;
  void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents,
          boolean recursive) throws SVNException;
  void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents,
          boolean recursive, boolean includeIgnored) throws SVNException;
  void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents,
          SVNDepth depth, boolean includeIgnored, boolean makeParents) throws SVNException;
  void doAdd(File[] paths, boolean force, boolean mkdir, boolean climbUnversionedParents,
          SVNDepth depth, boolean depthIsSticky, boolean includeIgnored, boolean makeParents) throws SVNException;
  void doAdd(File path, boolean force, boolean mkdir, boolean climbUnversionedParents,
          SVNDepth depth, boolean depthIsSticky, boolean includeIgnored, boolean makeParents) throws SVNException;
  void doMarkReplaced(File path) throws SVNException;
  void doRevert(File path, boolean recursive) throws SVNException;
  void doRevert(File[] paths, boolean recursive) throws SVNException;
  void doRevert(File[] paths, SVNDepth depth, Collection changeLists) throws SVNException;
  void doResolve(File path, boolean recursive) throws SVNException;
  void doResolve(File path, SVNDepth depth, SVNConflictChoice conflictChoice) throws SVNException;
  void doResolve(File path, SVNDepth depth, boolean resolveContents, boolean resolveProperties,
          SVNConflictChoice conflictChoice) throws SVNException;
  void doResolve(File path, SVNDepth depth, boolean resolveContents, boolean resolveProperties,
          boolean resolveTree, SVNConflictChoice conflictChoice) throws SVNException;
  void doLock(File[] paths, boolean stealLock, String lockMessage) throws SVNException;
  void doLock(SVNURL[] urls, boolean stealLock, String lockMessage) throws SVNException;
  void doUnlock(File[] paths, boolean breakLock) throws SVNException;
  void doUnlock(SVNURL[] urls, boolean breakLock) throws SVNException;
  void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException;
  void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException;
  void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth,
          Collection changeLists, ISVNInfoHandler handler) throws SVNException;
  void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException;
  void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth,
          ISVNInfoHandler handler) throws SVNException;
  String doGetWorkingCopyID( File path, String trailURL) throws SVNException;
  String doGetWorkingCopyID( File path, String trailURL, boolean committed) throws SVNException;
  SVNInfo doInfo(File path, SVNRevision revision) throws SVNException;
  SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException;
  void doCleanupWCProperties(File directory) throws SVNException;
  void doSetWCFormat(File directory, int format) throws SVNException;
  void doSetProperty(File path, String propName, SVNPropertyValue propValue, boolean force,
          boolean recursive, ISVNPropertyHandler handler) throws SVNException;
}
