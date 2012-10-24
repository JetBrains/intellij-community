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
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;

import java.io.File;
import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/19/12
 * Time: 3:50 PM
 */
public interface SVNChangelistClientI extends SvnMarkerInterface {
  void getChangeLists(File path, Collection<String> changeLists, SVNDepth depth, ISVNChangelistHandler handler) throws SVNException;

  void getChangeListPaths(Collection<String> changeLists, Collection<File> targets, SVNDepth depth, ISVNChangelistHandler handler) throws SVNException;

  void addToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException;

  void removeFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException;

  void doAddToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException;

  void doRemoveFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException;

  void doGetChangeListPaths(Collection<String> changeLists, Collection<File> targets, SVNDepth depth, ISVNChangelistHandler handler) throws SVNException;

  void doGetChangeLists(File path, Collection<String> changeLists, SVNDepth depth, ISVNChangelistHandler handler) throws SVNException;
}
