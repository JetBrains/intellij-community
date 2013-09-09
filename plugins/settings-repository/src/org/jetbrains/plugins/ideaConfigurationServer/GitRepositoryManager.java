package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableRunnable;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  @Override
  public String getRemoteRepositoryUrl() {
    return StringUtil.nullize(git.getRepository().getConfig().getString("remote", "origin", "url"));
  }

  @Override
  public void setRemoteRepositoryUrl(@Nullable String url) {
    StoredConfig config = git.getRepository().getConfig();
    if (StringUtil.isEmptyOrSpaces(url)) {
      config.unset("remote", "origin", "url");
    }
    else {
      config.setString("remote", "origin", "url", url);
    }
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

  @NotNull
  @Override
  public ActionCallback commit(@NotNull final ProgressIndicator indicator) {
    // todo
    indicator.setIndeterminate(true);

    final ActionCallback callback = new ActionCallback();
    taskProcessor.add(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        try {
          doRun();
          callback.setDone();
        }
        catch (Throwable e) {
          callback.reject(e.getMessage());
          LOG.error(e);
        }
      }

      private void doRun() throws IOException, GitAPIException {
        addFilesToGit();
        //indicator.setFraction(5);
        PersonIdent ident = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), "dev@null.org");
        git.commit().setAuthor(ident).setCommitter(ident).setMessage("").call();
        //indicator.setFraction(15);
        try {
          git.push().setProgressMonitor(new JGitProgressMonitor(indicator)).call();
        }
        catch (InvalidRemoteException e) {
          // remote repo is not configured
          LOG.debug(e.getMessage());
        }
      }
    });
    return callback;
  }

  private static class JGitProgressMonitor implements ProgressMonitor {
    private final ProgressIndicator indicator;

    public JGitProgressMonitor(ProgressIndicator indicator) {
      this.indicator = indicator;
    }

    @Override
    public void start(int totalTasks) {
    }

    @Override
    public void beginTask(String title, int totalWork) {
      indicator.setText2(title);
    }

    @Override
    public void update(int completed) {
      // todo
    }

    @Override
    public void endTask() {
      indicator.setText2("");
    }

    @Override
    public boolean isCancelled() {
      return indicator.isCanceled();
    }
  }
}