package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


final class IcsGitConnector {
  private final Git git;

  // avoid FS recursive scan
  private final Set<String> filesToAdd = new THashSet<String>();

  public IcsGitConnector() throws IOException {
    File gitDir = new File(PathManager.getSystemPath(), "ideaConfigurationServer/data");
    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setGitDir(new File(gitDir, Constants.DOT_GIT));
    Repository repository = repositoryBuilder.build();
    git = new Git(repository);
    if (!gitDir.exists()) {
      repository.create();
    }
  }

  public void updateRepo() {
    // todo sync with remote
  }

  @SuppressWarnings("UnusedDeclaration")
  public void push() throws IOException {
    addFilesToGit();
    try {
      git.push().call();
    }
    catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  private void addFilesToGit() throws IOException {
    AddCommand addCommand;
    synchronized (filesToAdd) {
      if (filesToAdd.isEmpty()) {
        return;
      }

      addCommand = git.add();
      for (String pathname : filesToAdd) {
        addCommand.addFilepattern(pathname);
      }
      filesToAdd.clear();
    }

    try {
      addCommand.call();
    }
    catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  @Nullable
  public InputStream loadUserPreferences(@NotNull String path) throws IOException {
    Repository repository = git.getRepository();
    TreeWalk treeWalk = TreeWalk.forPath(repository, path, new RevWalk(repository).parseCommit(repository.resolve(Constants.HEAD)).getTree());
    if (treeWalk == null) {
      return null;
    }
    return repository.open(treeWalk.getObjectId(0)).openStream();
  }

  public void save(@NotNull InputStream content, @NotNull String path) throws IOException {
    File file = new File(git.getRepository().getDirectory().getParent(), path);
    FileOutputStream out = new FileOutputStream(file);
    try {
      FileUtilRt.copy(content, out);
      synchronized (filesToAdd) {
        filesToAdd.add(path);
      }
    }
    finally {
      out.close();
    }
  }

  public void delete(@NotNull String path) throws IOException {
    synchronized (filesToAdd) {
      filesToAdd.remove(path);
    }

    try {
      git.rm().addFilepattern(path).call();
    }
    catch (GitAPIException e) {
      throw new IOException(e);
    }
  }

  public String[] listSubFileNames(@NotNull String path) {
    File[] files = new File(git.getRepository().getDirectory().getParent(), path).listFiles();
    if (files == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    
    List<String> result = new ArrayList<String>(files.length);
    for (File file : files) {
      result.add(file.getName());
    }
    return ArrayUtil.toStringArray(result);
  }
}