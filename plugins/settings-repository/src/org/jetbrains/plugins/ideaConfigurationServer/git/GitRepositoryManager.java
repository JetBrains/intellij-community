package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager;
import org.jetbrains.plugins.ideaConfigurationServer.IcsBundle;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class GitRepositoryManager extends BaseRepositoryManager {
  final Git git;

  private CredentialsProvider credentialsProvider;

  public GitRepositoryManager() throws IOException {
    Repository repository = new FileRepositoryBuilder().setGitDir(new File(dir, Constants.DOT_GIT)).build();
    if (!dir.exists()) {
      repository.create(false);
      disableAutoCrLf(repository);
    }
    git = Git.wrap(repository);
  }

  private static void disableAutoCrLf(Repository repository) throws IOException {
    StoredConfig config = repository.getConfig();
    config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, ConfigConstants.CONFIG_KEY_FALSE);
    config.save();
  }

  @Override
  public void initRepository(@NotNull File dir) throws IOException {
    new FileRepositoryBuilder().setBare().setGitDir(dir).build().create(true);
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
  public void setRemoteRepositoryUrl(@Nullable String url) throws Exception {
    StoredConfig config = git.getRepository().getConfig();
    if (StringUtil.isEmptyOrSpaces(url)) {
      config.unset(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
      LOG.debug("Unset remote");
      config.save();
    }
    else {
      config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL, url);
      LOG.debug("Set remote " + url);
      // save before next commands
      config.save();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          // http://stackoverflow.com/questions/11077401/why-doesnt-git-branch-show-anything-in-my-new-bitbucket-clone
          // if you run git branch in the current directory then it will return no branches as the repository is empty and the master branch will be created with the first commit.
          // https://plus.google.com/106049295903830073464/posts/e8KfR9sYLqh
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          try {
            new CommitTask(GitRepositoryManager.this, indicator).run();
            if (git.getRepository().getRef(Constants.HEAD) == null) {
              // http://stackoverflow.com/questions/24197299/how-to-create-a-null-branch-in-jgit
              createCommitCommand().setMessage("Initial commit (jgit workaround)").call();
            }

            git.branchCreate()
              .setForce(true)
              .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
              .setName(Constants.MASTER)
              .call();
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }, IcsBundle.message("task.set.upstream.title"), true, null);

      //config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");

      //config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_REMOTE, Constants.DEFAULT_REMOTE_NAME);
      //config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE, "refs/heads/master");
    }
  }

  @NotNull
  CommitCommand createCommitCommand() {
    PersonIdent author = new PersonIdent(git.getRepository());
    PersonIdent committer = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), author.getEmailAddress());
    return git.commit().setAuthor(author).setCommitter(committer);
  }

  @Override
  protected void doUpdateRepository() throws Exception {
    git.fetch().setRemoveDeletedRefs(true).setCredentialsProvider(getCredentialsProvider()).call();
  }

  @Override
  protected boolean hasRemoteRepository() {
    return !StringUtil.isEmptyOrSpaces(git.getRepository().getConfig().getString("remote", "origin", "url"));
  }

  @Override
  protected void doAdd(@NotNull String path) throws Exception {
    git.add().addFilepattern(path).call();
  }

  @Override
  protected void doDelete(@NotNull String path) throws GitAPIException {
    git.rm().addFilepattern(path).call();
  }

  @NotNull
  @Override
  public ActionCallback commit(@NotNull ProgressIndicator indicator) {
    return execute(new CommitTask(this, indicator));
  }

  @Override
  public void commit(@NotNull List<String> paths) {
  }

  @Override
  @NotNull
  public ActionCallback push(@NotNull ProgressIndicator indicator) {
    return execute(new Task(indicator) {
      @Override
      protected void execute() throws Exception {
        git.push().setProgressMonitor(new JGitProgressMonitor(indicator)).setCredentialsProvider(getCredentialsProvider()).call();
      }
    });
  }

  @Override
  @NotNull
  public ActionCallback pull(@NotNull ProgressIndicator indicator) {
    return execute(new PullTask(this, indicator));
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