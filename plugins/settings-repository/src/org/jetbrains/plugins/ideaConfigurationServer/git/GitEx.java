package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class GitEx extends Git {
  private static final Logger LOG = Logger.getInstance(GitEx.class);

  private TreeWalk treeWalk;

  public GitEx(Repository repo) {
    super(repo);
  }

  public static void createBareRepository(@NotNull File dir) throws IOException {
    new FileRepositoryBuilder().setBare().setGitDir(dir).build().create(true);
  }

  public void disableAutoCrLf() throws IOException {
    StoredConfig config = getRepository().getConfig();
    config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE);
    config.save();
  }

  public void setUpstream(@Nullable String url, @Nullable String branchName) throws IOException {
    // our local branch named 'master' in any case
    String localBranchName = Constants.MASTER;

    StoredConfig config = getRepository().getConfig();
    String remoteName = Constants.DEFAULT_REMOTE_NAME;
    if (StringUtil.isEmptyOrSpaces(url)) {
      LOG.debug("Unset remote");
      config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName);
      config.unsetSection(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName);
    }
    else {
      if (branchName == null) {
        branchName = Constants.MASTER;
      }

      LOG.debug("Set remote " + url);
      config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_KEY_URL, url);
      // http://git-scm.com/book/en/Git-Internals-The-Refspec
      config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, ConfigConstants.CONFIG_FETCH_SECTION, '+' + Constants.R_HEADS + branchName + ':' + Constants.R_REMOTES + remoteName + '/' + branchName);
      // todo should we set it if fetch specified (kirill.likhodedov suggestion)
      //config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, "push", Constants.R_HEADS + localBranchName + ':' + Constants.R_HEADS + branchName);

      config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName, ConfigConstants.CONFIG_KEY_REMOTE, remoteName);
      config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName);
    }
    config.save();
  }

  public boolean add(@NotNull String path) throws IOException {
    Repository repository = getRepository();
    if (treeWalk == null) {
      treeWalk = new TreeWalk(repository);
      treeWalk.setRecursive(false);
    }

    try {
      return doAdd(treeWalk, path, repository, new FileTreeIterator(getRepository()));
    }
    finally {
      treeWalk.release();
      treeWalk.reset();
    }
  }

  public void remove(@NotNull String path, boolean isFile) throws IOException {
    Repository repository = getRepository();
    DirCache dirCache = repository.lockDirCache();
    try {
      DirCacheEditor editor = dirCache.editor();
      editor.add(isFile ? new DirCacheEditor.DeletePath(path) : new DirCacheEditor.DeleteTree(path));
      editor.commit();
    }
    finally {
      dirCache.unlock();
    }
  }

  @NotNull
  public IndexDiff computeIndexDiff() throws IOException {
    WorkingTreeIterator workingTreeIterator = new FileTreeIterator(getRepository());
    try {
      return new IndexDiff(getRepository(), Constants.HEAD, workingTreeIterator);
    }
    finally {
      workingTreeIterator.reset();
    }
  }

  private static boolean doAdd(@NotNull TreeWalk treeWalk, @NotNull String path, @NotNull Repository repository, @NotNull WorkingTreeIterator workingTreeIterator)
    throws IOException {
    PathFilter pathFilter = PathFilter.create(path);
    treeWalk.setFilter(pathFilter);

    DirCache dirCache = repository.lockDirCache();
    try {
      DirCacheBuilder builder = dirCache.builder();
      treeWalk.addTree(new DirCacheBuildIterator(builder));
      treeWalk.addTree(workingTreeIterator);

      while (treeWalk.next()) {
        if (pathFilter.isDone(treeWalk)) {
          break;
        }
        else if (treeWalk.isSubtree()) {
          treeWalk.enterSubtree();
        }
      }

      return doAdd(treeWalk, path, repository, builder);
    }
    finally {
      dirCache.unlock();
      workingTreeIterator.reset();
    }
  }

  private static boolean doAdd(@NotNull TreeWalk treeWalk, @NotNull String path, @NotNull Repository repository, @NotNull DirCacheBuilder builder) throws IOException {
    WorkingTreeIterator workingTree = treeWalk.getTree(1, WorkingTreeIterator.class);
    DirCacheIterator dirCacheTree = treeWalk.getTree(0, DirCacheIterator.class);
    if (dirCacheTree == null && workingTree != null && workingTree.isEntryIgnored()) {
      // file is not in index but is ignored, do nothing
      return true;
    }

    if (workingTree != null) {
      // the file exists
      if (dirCacheTree == null || dirCacheTree.getDirCacheEntry() == null || !dirCacheTree.getDirCacheEntry().isAssumeValid()) {
        FileMode mode = workingTree.getIndexFileMode(dirCacheTree);
        DirCacheEntry entry = new DirCacheEntry(path);
        entry.setFileMode(mode);
        if (mode == FileMode.GITLINK) {
          entry.setObjectId(workingTree.getEntryObjectId());
        }
        else {
          entry.setLength(workingTree.getEntryLength());
          entry.setLastModified(workingTree.getEntryLastModified());
          ObjectInserter inserter = null;
          InputStream in = workingTree.openEntryStream();
          try {
            inserter = repository.newObjectInserter();
            entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, workingTree.getEntryContentLength(), in));
            inserter.flush();
          }
          finally {
            in.close();
            if (inserter != null) {
              inserter.release();
            }
          }
        }
        builder.add(entry);
      }
      else {
        builder.add(dirCacheTree.getDirCacheEntry());
      }
    }
    else if (dirCacheTree != null) {
      builder.add(dirCacheTree.getDirCacheEntry());
    }

    builder.commit();
    return false;
  }
}
