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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LocationDetector {
  private final Map<String, File> myMap;

  public LocationDetector(final SvnVcs vcs) {
    myMap = new HashMap<String, File>();

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(vcs.getProject()).getRootsUnderVcs(vcs);

    for (VirtualFile root : roots) {
      myMap.putAll(SvnUtil.getLocationInfoForModule(vcs, new File(root.getPath()), progress));
    }
  }

  @Nullable
  public FilePath crawlForPath(final String fullPath, final NotNullFunction<File, Boolean> detector) {
    for (Map.Entry<String, File> entry : myMap.entrySet()) {
      final String url = entry.getKey();
      if (SVNPathUtil.isAncestor(url, fullPath)) {
        return filePathByUrlAndPath(fullPath, url, entry.getValue().getAbsolutePath(), detector);
      }
    }
    return null;
  }

  static FilePath filePathByUrlAndPath(final String longPath, final String parentUrl, final String parentPath, final NotNullFunction<File, Boolean> detector) {
    final String relPath = longPath.substring(parentUrl.length());
    final File localFile = new File(parentPath, relPath);
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(localFile, detector);
  }
}
