// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilterFilePathStrings;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;
import java.util.*;

public class ChangesChecker {

  private final @NotNull SuperfluousRemover myForAdds;
  private final @NotNull SuperfluousRemover myForDeletes;
  private final List<File> myForEdits;

  private final SvnChangeProvider myChangeProvider;
  private final @NotNull UnversionedAndNotTouchedFilesGroupCollector myCollector;

  private final List<SvnBindException> myExceptions;

  ChangesChecker(@NotNull SvnVcs vcs, @NotNull UnversionedAndNotTouchedFilesGroupCollector collector) {
    myChangeProvider = (SvnChangeProvider)vcs.getChangeProvider();
    myCollector = collector;
    myForAdds = new SuperfluousRemover(true);
    myForDeletes = new SuperfluousRemover(false);
    myForEdits = new ArrayList<>();
    myExceptions = new ArrayList<>();
  }

  public void gather(final List<? extends Change> changes) {
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

  public @NotNull Collection<File> getForAdds() {
    return myForAdds.getParentPaths();
  }

  public @NotNull Collection<File> getForDeletes() {
    return myForDeletes.getParentPaths();
  }

  public List<SvnBindException> getExceptions() {
    return myExceptions;
  }

  public @NotNull List<File> getForEdits() {
    return myForEdits;
  }
}
