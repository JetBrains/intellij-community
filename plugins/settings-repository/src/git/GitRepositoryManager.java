package org.jetbrains.plugins.settingsRepository.git;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.OperationResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.settingsRepository.BaseRepositoryManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class GitRepositoryManager extends BaseRepositoryManager {
  final GitEx git;

  private CredentialsProvider credentialsProvider;

  public GitRepositoryManager() throws IOException {
    Repository repository = new FileRepositoryBuilder().setWorkTree(dir).build();
    git = new GitEx(repository);
    if (!dir.exists()) {
      repository.create();
      git.disableAutoCrLf();
    }
  }

  @TestOnly
  @NotNull
  public GitEx getGit() {
    return git;
  }

  @Override
  public void initRepository(@NotNull File dir) throws IOException {
    GitEx.createBareRepository(dir);
  }

  @NotNull
  CredentialsProvider getCredentialsProvider() {
    if (credentialsProvider == null) {
      credentialsProvider = new JGitCredentialsProvider();
    }
    return credentialsProvider;
  }

  @Nullable
  @Override
  public String getRemoteRepositoryUrl() {
    return StringUtil.nullize(git.getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL));
  }

  @Override
  public void setUpstream(@Nullable String url, @Nullable String branch) throws Exception {
    git.setUpstream(url, branch);
  }

  @NotNull
  CommitCommand createCommitCommand() {
    PersonIdent author = new PersonIdent(git.getRepository());
    PersonIdent committer = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), author.getEmailAddress());
    return git.commit().setAuthor(author).setCommitter(committer);
  }

  @Override
  public boolean hasUpstream() {
    return !StringUtil.isEmptyOrSpaces(git.getRepository().getConfig().getString("remote", "origin", "url"));
  }

  @Override
  protected void addToIndex(@NotNull File file, @NotNull String path) throws Exception {
    git.add(path);
  }

  @Override
  protected void deleteFromIndex(@NotNull String path, boolean isFile) throws IOException {
    git.remove(path, isFile);
  }

  @Override
  public void commit(@NotNull ProgressIndicator indicator) throws Exception {
    synchronized (lock) {
      CommitTask.execute(this, indicator);
    }
  }

  @Override
  public void commit(@NotNull List<String> paths) {
  }

  @Override
  public void push(@NotNull ProgressIndicator indicator) throws Exception {
    LOG.debug("Push");
    Iterable<PushResult> pushResults = git.push().setProgressMonitor(new JGitProgressMonitor(indicator)).setCredentialsProvider(getCredentialsProvider()).call();
    for (PushResult pushResult : pushResults) {
      if (LOG.isDebugEnabled()) {
        printMessages(pushResult);

        for (RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
          LOG.debug(refUpdate.toString());
        }
      }
    }
  }

  static void printMessages(@NotNull OperationResult fetchResult) {
    if (LOG.isDebugEnabled()) {
      String messages = fetchResult.getMessages();
      if (!StringUtil.isEmptyOrSpaces(messages)) {
        LOG.debug(messages);
      }
    }
  }

  @Override
  public void pull(@NotNull ProgressIndicator indicator) throws Exception {
    PullTask.execute(this, indicator);
  }

  @Override
  public boolean isValidRepository(@NotNull File file) {
    if (new File(file, Constants.DOT_GIT).exists()) {
      return true;
    }
    // existing bare repository
    try {
      new FileRepositoryBuilder().setGitDir(file).setMustExist(true).build();
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }
}
