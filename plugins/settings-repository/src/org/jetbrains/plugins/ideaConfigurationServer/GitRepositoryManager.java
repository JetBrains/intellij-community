package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

final class GitRepositoryManager extends BaseRepositoryManager {
  private static final Logger LOG = Logger.getInstance(GitRepositoryManager.class);

  private final Git git;

  public GitRepositoryManager() throws IOException {
    super(new File(IcsManager.PLUGIN_SYSTEM_DIR, "data"));

    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setGitDir(new File(dir, Constants.DOT_GIT));
    Repository repository = repositoryBuilder.build();
    if (!dir.exists()) {
      repository.create();
    }

    git = new Git(repository);
  }

  @Override
  public void updateRepo() throws IOException {
    try {
      git.fetch().setRemoveDeletedRefs(true).call();
    }
    catch (InvalidRemoteException ignored) {
      // remote repo is not configured
      LOG.debug(ignored.getMessage());
    }
    catch (GitAPIException e) {
      throw new IOException(e);
    }
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

  @Override
  protected void doDelete(@NotNull String path) throws IOException {
    try {
      git.rm().addFilepattern(path).call();
    }
    catch (GitAPIException e) {
      throw new IOException(e);
    }
  }
}