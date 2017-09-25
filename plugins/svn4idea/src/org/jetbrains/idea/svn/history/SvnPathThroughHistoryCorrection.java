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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.svn.SvnUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/30/12
 * Time: 5:18 PM
 *
 * We consider here, that history is traversed "from now to past"
 */
public class SvnPathThroughHistoryCorrection implements LogEntryConsumer {
  private String myBefore;
  private String myPath;
  private LogEntryPath myDirectlyMentioned;
  private boolean myRoot;

  public SvnPathThroughHistoryCorrection(String path) {
    myPath = path;
    myBefore = path;
    myRoot = StringUtil.isEmpty(path);
  }

  @Override
  public void consume(LogEntry logEntry) throws SVNException {
    if (myRoot) {
      return;
    }
    myBefore = myPath;
    myDirectlyMentioned = null;
    final Map<String,LogEntryPath> paths = logEntry.getChangedPaths();
    final LogEntryPath entryPath = paths.get(myPath);
    if (entryPath != null) {
      myDirectlyMentioned = entryPath;
      // exact match
      if (entryPath.getCopyPath() != null) {
        myPath = entryPath.getCopyPath();
        return;
      }
    }
    for (LogEntryPath path : paths.values()) {
      // "the origin path *from where* the item, ..."
      // TODO: this could incorrectly handle case when parent folder was replaced - see IDEA-103042
      // TODO: or several parent folder renames occur IDEA-96825
      final String copyPath = path.getCopyPath();
      if (copyPath != null) {
        final String thisEntryPath = path.getPath();
        if (parentPathChanged(copyPath, thisEntryPath)) {
          return;
        }
      }
    }
  }

  private boolean parentPathChanged(String copyPath, String thisEntryPath) {
    if (SVNPathUtil.isAncestor(thisEntryPath, myPath)) {
      final String relativePath = SVNPathUtil.getRelativePath(thisEntryPath, myPath);
      myPath = SvnUtil.appendMultiParts(copyPath, relativePath);
      return true;
    }
    return false;
  }

  public String getBefore() {
    return myBefore;
  }

  public LogEntryPath getDirectlyMentioned() {
    return myDirectlyMentioned;
  }

  public String getCurrentPath() {
    return myPath;
  }

  public boolean isRoot() {
    return myRoot;
  }
}
