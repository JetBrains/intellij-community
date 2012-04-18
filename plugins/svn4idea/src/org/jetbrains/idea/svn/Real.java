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

import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.wc.SVNInfo;

class Real extends Node implements RootUrlPair {
  private final SVNInfo myInfo;
  private final VirtualFile myVcsRoot;

  Real(final VirtualFile file, final SVNInfo info, VirtualFile vcsRoot) {
    super(file, info.getURL().toString());
    myInfo = info;
    myVcsRoot = vcsRoot;
  }

  public SVNInfo getInfo() {
    return myInfo;
  }

  public VirtualFile getVcsRoot() {
    return myVcsRoot;
  }

  public VirtualFile getVirtualFile() {
    return getFile();
  }
}
