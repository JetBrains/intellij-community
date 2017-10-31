// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.svn.api.Url;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
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
  public void consume(LogEntry logEntry) {
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
    if (Url.isAncestor(thisEntryPath, myPath)) {
      final String relativePath = Url.getRelative(thisEntryPath, myPath);
      myPath = Url.append(copyPath, relativePath);
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
