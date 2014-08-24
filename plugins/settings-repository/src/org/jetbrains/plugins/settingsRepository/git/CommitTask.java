package org.jetbrains.plugins.settingsRepository.git;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.settingsRepository.BaseRepositoryManager;
import org.jetbrains.plugins.settingsRepository.IcsUrlBuilder;

import java.util.Collection;

class CommitTask {
  public static void execute(@NotNull GitRepositoryManager manager, @NotNull ProgressIndicator indicator) throws Exception {
    IndexDiff index = manager.git.computeIndexDiff();
    boolean changed = index.diff(new JGitProgressMonitor(indicator), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Commit");

    if (BaseRepositoryManager.LOG.isDebugEnabled()) {
      BaseRepositoryManager.LOG.debug(indexDiffToString(index));
    }

    // don't worry about untracked/modified only in the FS files
    if (!changed || (index.getAdded().isEmpty() && index.getChanged().isEmpty() && index.getRemoved().isEmpty())) {
      if (index.getModified().isEmpty()) {
        BaseRepositoryManager.LOG.debug("Skip scheduled commit, nothing to commit");
        return;
      }

      AddCommand addCommand = null;
      for (String path : index.getModified()) {
        if (!path.startsWith(IcsUrlBuilder.PROJECTS_DIR_NAME)) {
          if (addCommand == null) {
            addCommand = manager.git.add();
          }
          addCommand.addFilepattern(path);
        }
      }
      if (addCommand != null) {
        addCommand.call();
      }
    }

    BaseRepositoryManager.LOG.debug("Commit");
    manager.createCommitCommand().setMessage("").call();
  }

  @NotNull
  private static String indexDiffToString(@NotNull IndexDiff diff) {
    StringBuilder builder = new StringBuilder();
    builder.append("To commit:");
    addList("Added", diff.getAdded(), builder);
    addList("Changed", diff.getChanged(), builder);
    addList("Removed", diff.getRemoved(), builder);
    addList("Modified on disk relative to the index", diff.getModified(), builder);
    addList("Untracked files", diff.getUntracked(), builder);
    addList("Untracked folders", diff.getUntrackedFolders(), builder);
    return builder.toString();
  }

  private static void addList(@NotNull String name, @NotNull Collection<String> list, @NotNull StringBuilder builder) {
    if (list.isEmpty()) {
      return;
    }

    builder.append('\t').append(name).append(": ");
    boolean isNotFirst = false;
    for (String path : list) {
      if (isNotFirst) {
        builder.append(',').append(' ');
      }
      else {
        isNotFirst = true;
      }
      builder.append(path);
    }
  }
}
