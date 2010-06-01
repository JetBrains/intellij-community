/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PairProcessor;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfo;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.Map;

public class OneRecursiveShotMergeInfoWorker implements MergeInfoWorker {
  private final Project myProject;
  private final WCInfo myWCInfo;
  // subpath [file] (local) to (subpathURL - merged FROM - to ranges list)
  private final AreaMap<String, Map<String, SVNMergeRangeList>> myDataMap;
  private String myFromUrlRelative;

  public OneRecursiveShotMergeInfoWorker(final Project project, final WCInfo WCInfo, final String fromUrl) {
    myProject = project;
    myWCInfo = WCInfo;
    
    myDataMap = new AreaMap<String, Map<String, SVNMergeRangeList>>(new PairProcessor<String, String>() {
      public boolean process(String parentUrl, String childUrl) {
        if (".".equals(parentUrl)) return true;
        return SVNPathUtil.isAncestor(ensureUrlFromSlash(parentUrl), ensureUrlFromSlash(childUrl));
      }
    });
    final String url = SVNPathUtil.getRelativePath(myWCInfo.getRepositoryRoot(), fromUrl);
    myFromUrlRelative = ensureUrlFromSlash(url);
  }

  private String ensureUrlFromSlash(final String url) {
    return url.startsWith("/") ? url : "/" + url;
  }
  
  public void prepare() throws SVNException {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final SVNWCClient client = vcs.createWCClient();

    client.doGetProperty(new File(myWCInfo.getPath()), SVNProperty.MERGE_INFO, SVNRevision.UNDEFINED, SVNRevision.WORKING,
                         SVNDepth.INFINITY, new ISVNPropertyHandler() {
        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
          final String key = keyFromFile(path);
          myDataMap.put(key, SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(replaceSeparators(property.getValue().getString())), null));
        }

        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        }
        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        }
      }, null);
  }

  public SvnMergeInfoCache.MergeCheckResult isMerged(final String relativeToRepoURLPath, final long revisionNumber) {
    // should make relative to wc root
    final String relativeToWc = SVNPathUtil.getRelativePath(myFromUrlRelative, ensureUrlFromSlash(relativeToRepoURLPath));
    if (relativeToWc == null) return SvnMergeInfoCache.MergeCheckResult.NOT_EXISTS;

    final InfoProcessor processor = new InfoProcessor(relativeToWc, myFromUrlRelative, revisionNumber);
    myDataMap.getSimiliar(relativeToWc.toUpperCase(), processor);
    return SvnMergeInfoCache.MergeCheckResult.getInstance(processor.isMerged());
  }

  private static class InfoProcessor implements PairProcessor<String, Map<String, SVNMergeRangeList>> {
    private final String myWcLevelRelativeSourceUrl;
    private boolean myMerged;
    private final String myFilePathAsked;
    private final long myRevisionAsked;

    public InfoProcessor(final String filePathAsked, final String wcLevelRelativeSourceUrl, final long revisionAsked) {
      myFilePathAsked = filePathAsked;
      myRevisionAsked = revisionAsked;
      myWcLevelRelativeSourceUrl = wcLevelRelativeSourceUrl.startsWith("/") ? wcLevelRelativeSourceUrl : "/" + wcLevelRelativeSourceUrl;
    }

    public boolean isMerged() {
      return myMerged;
    }

    public boolean process(final String relativeFileSubpath, Map<String, SVNMergeRangeList> map) {
      boolean processed = false;
      final boolean self = relativeFileSubpath.equals(myFilePathAsked);

      for (Map.Entry<String, SVNMergeRangeList> entry : map.entrySet()) {
        String relativeUrl = entry.getKey();

        boolean urlMatches = false;
        if (".".equals(relativeUrl) || "".equals(relativeUrl)) {
          urlMatches = true;
        } else {
          relativeUrl = (relativeUrl.startsWith("/")) ? relativeUrl : "/" + relativeUrl;
          urlMatches = SVNPathUtil.isAncestor(myWcLevelRelativeSourceUrl, relativeUrl);
        }

        if (! urlMatches) continue;
        processed = true;

        final SVNMergeRangeList rangesList = entry.getValue();

        for (SVNMergeRange range : rangesList.getRanges()) {
          // SVN does not include start revision in range
          final long startRevision = range.getStartRevision() + 1;
          final long endRevision = range.getEndRevision();
          final boolean isInheritable = range.isInheritable();
          final boolean inInterval = (myRevisionAsked >= startRevision) && (myRevisionAsked <= endRevision);

          if ((isInheritable || self) && inInterval) {
            myMerged = true;
            break;
          }
        }
        break;
      }

      return processed;
    }
  }

  private String keyFromFile(final File file) {
    final String path =
      FileUtil.getRelativePath(myWCInfo.getPath(), file.getAbsolutePath(), File.separatorChar).replace(File.separatorChar, '/');
    return SystemInfo.isFileSystemCaseSensitive ? path : path.toUpperCase();
  }

  private static String replaceSeparators(final String s) {
    return s.replace('\r', '\n').replace("\n\n", "\n");
  }
}
