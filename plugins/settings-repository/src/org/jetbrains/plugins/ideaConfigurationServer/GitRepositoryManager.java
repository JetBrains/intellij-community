package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.util.ThrowableRunnable;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

final class GitRepositoryManager extends BaseRepositoryManager {
  private final Git git;

  public GitRepositoryManager() throws IOException {
    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setGitDir(new File(dir, Constants.DOT_GIT));
    Repository repository = repositoryBuilder.build();
    if (!dir.exists()) {
      repository.create();
    }

    git = new Git(repository);
  }

  @Override
  public void updateRepository() throws IOException {
    try {
      git.fetch().setRemoveDeletedRefs(true).call();
    }
    catch (InvalidRemoteException e) {
      // remote repo is not configured
      LOG.debug(e.getMessage());
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
  protected void doDelete(@NotNull String path) throws GitAPIException {
    git.rm().addFilepattern(path).call();
  }

  @Override
  public void commit() {
    taskProcessor.add(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        addFilesToGit();
        PersonIdent ident = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), "dev@null.org");
        git.commit().setAuthor(ident).setCommitter(ident).setMessage("").call();
        try {
          git.push().call();
        }
        catch (InvalidRemoteException e) {
          // remote repo is not configured
          LOG.debug(e.getMessage());
        }
      }
    });
  }
}