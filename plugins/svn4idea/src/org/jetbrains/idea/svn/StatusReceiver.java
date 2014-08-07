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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.status.Status;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.util.EventListener;

public interface StatusReceiver extends EventListener {
  void process(final FilePath path, final Status status) throws SVNException;
  void processIgnored(final VirtualFile vFile);
  void processUnversioned(final VirtualFile vFile);
  void processCopyRoot(VirtualFile file, SVNURL url, WorkingCopyFormat format, SVNURL rootURL);
  void bewareRoot(VirtualFile vf, SVNURL url);
  void finish();
}
