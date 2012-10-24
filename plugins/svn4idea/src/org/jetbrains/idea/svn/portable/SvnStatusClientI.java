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
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;

import java.io.File;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/24/12
 * Time: 9:46 AM
 */
public interface SvnStatusClientI extends SvnMarkerInterface {
  long doStatus(File path, boolean recursive, boolean remote, boolean reportAll,
          boolean includeIgnored, ISVNStatusHandler handler) throws SVNException;
  long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, ISVNStatusHandler handler) throws SVNException;
  long doStatus(File path, SVNRevision revision, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, ISVNStatusHandler handler) throws SVNException;
  long doStatus(File path, SVNRevision revision, SVNDepth depth, boolean remote, boolean reportAll,
          boolean includeIgnored, boolean collectParentExternals, ISVNStatusHandler handler,
          Collection changeLists) throws SVNException;
  SVNStatus doStatus( File path, boolean remote) throws SVNException;
  SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException;
}
