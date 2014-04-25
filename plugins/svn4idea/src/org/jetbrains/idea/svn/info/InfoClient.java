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
package org.jetbrains.idea.svn.info;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/20/12
 * Time: 6:54 PM
 */
public interface InfoClient extends SvnClient {

  void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException;
  void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException;
  void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth,
          Collection changeLists, ISVNInfoHandler handler) throws SVNException;
  void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException;
  void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth,
          ISVNInfoHandler handler) throws SVNException;
  SVNInfo doInfo(File path, SVNRevision revision) throws SVNException;
  SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException;

  void doInfo(@NotNull Collection<File> paths, @Nullable ISVNInfoHandler handler) throws SVNException;
}
