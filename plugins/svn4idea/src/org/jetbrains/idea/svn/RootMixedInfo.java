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
import org.tmatesoft.svn.core.SVNURL;

public class RootMixedInfo {
  /**
   * working copy root path
   */
  private final String myFilePath;
  private final VirtualFile myFile;
  /**
   * url corresponding to working copy root
   */
  private final SVNURL myUrl;
  private final VirtualFile myParentVcsRoot;

  public RootMixedInfo(final String filePath, final VirtualFile file, final SVNURL url, final VirtualFile parentVcsRoot) {
    myFilePath = filePath;
    myFile = file;
    myUrl = url;
    myParentVcsRoot = parentVcsRoot;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public SVNURL getUrl() {
    return myUrl;
  }

  public VirtualFile getParentVcsRoot() {
    return myParentVcsRoot;
  }
}
