/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilterFilePathStrings;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.*;

/**
* @author Konstantin Kolosovsky.
*/
public class ChangesChecker {

  @NotNull private final SuperfluousRemover myForAdds;
  @NotNull private final SuperfluousRemover myForDeletes;
  private final List<File> myForEdits;

  private final SvnChangeProvider myChangeProvider;
  @NotNull private final UnversionedAndNotTouchedFilesGroupCollector myCollector;

  private final List<VcsException> myExceptions;

  ChangesChecker(@NotNull SvnVcs vcs, @NotNull UnversionedAndNotTouchedFilesGroupCollector collector) {
    myChangeProvider = (SvnChangeProvider)vcs.getChangeProvider();
    myCollector = collector;
    myForAdds = new SuperfluousRemover(true);
    myForDeletes = new SuperfluousRemover(false);
    myForEdits = new ArrayList<>();
    myExceptions = new ArrayList<>();
  }

  public void gather(final List<Change> changes) {
    final TreeMap<String, File> renames = new TreeMap<>();
    final Set<String> alsoReverted = new HashSet<>();
    final Map<String, FilePath> files = new HashMap<>();
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      final String key = afterRevision == null ? null : FilePathsHelper.convertWithLastSeparator(afterRevision.getFile());
      if (SvnRollbackEnvironment.isMoveRenameReplace(change)) {
        final File beforeFile = beforeRevision.getFile().getIOFile();
        renames.put(key, beforeFile);
        files.put(key, afterRevision.getFile());
        myCollector.markRename(beforeFile, afterRevision.getFile().getIOFile());
      } else if (afterRevision != null) {
        alsoReverted.add(key);
      }
    }
    if (! renames.isEmpty()) {
      final ArrayList<String> paths = new ArrayList<>(renames.keySet());
      if (paths.size() > 1) {
        FilterFilePathStrings.getInstance().doFilter(paths);
      }
      myCollector.setRenamesMap(renames);
      myCollector.setAlsoReverted(alsoReverted);
      for (String path : paths) {
        try {
          myChangeProvider.getChanges(files.get(path), true, myCollector);
        }
        catch (SVNException e) {
          myExceptions.add(new VcsException(e));
        }
        catch (SvnBindException e) {
          myExceptions.add(e);
        }
      }
    }

    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();

      boolean checked = myForAdds.accept(change);
      checked |= myForDeletes.accept(change);

      if (! checked) {
        myForEdits.add(afterRevision.getFile().getIOFile());
      }
    }
  }

  @NotNull
  public Collection<File> getForAdds() {
    return myForAdds.getParentPaths();
  }

  @NotNull
  public Collection<File> getForDeletes() {
    return myForDeletes.getParentPaths();
  }

  public List<VcsException> getExceptions() {
    return myExceptions;
  }

  @NotNull
  public List<File> getForEdits() {
    return myForEdits;
  }
}
