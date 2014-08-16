package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager;
import org.jetbrains.plugins.ideaConfigurationServer.IcsUrlBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public final class GitRepositoryManager extends BaseRepositoryManager {
  private final Git git;

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

  private CredentialsProvider getCredentialsProvider() {
    if (credentialsProvider == null) {
      credentialsProvider = new JGitCredentialsProvider(this);
    }
    return credentialsProvider;
  }

  @Nullable
  @Override
  public String getRemoteRepositoryUrl() {
    return StringUtil.nullize(git.getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL));
  }

  @Override
  public void setRemoteRepositoryUrl(@Nullable String url) {
    StoredConfig config = git.getRepository().getConfig();
    if (StringUtil.isEmptyOrSpaces(url)) {
      config.unset(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
    }
    else {
      config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL, url);
      config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");

      config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_REMOTE, Constants.DEFAULT_REMOTE_NAME);
      config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE, "refs/heads/master");
    }

    try {
      config.save();
    }
    catch (IOException e) {
      LOG.error(e);
    }
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
    return execute(new Task(indicator) {
      @Override
      protected void execute() throws Exception {
        IndexDiff index = new IndexDiff(git.getRepository(), Constants.HEAD, new FileTreeIterator(git.getRepository()));
        boolean changed = index.diff(new JGitProgressMonitor(indicator), ProgressMonitor.UNKNOWN, ProgressMonitor.UNKNOWN, "Commit");

        if (LOG.isDebugEnabled()) {
          LOG.debug(indexDiffToString(index));
        }

        // don't worry about untracked/modified only in the FS files
        if (!changed || (index.getAdded().isEmpty() && index.getChanged().isEmpty() && index.getRemoved().isEmpty())) {
          if (index.getModified().isEmpty()) {
            LOG.debug("Skip scheduled commit, nothing to commit");
            return;
          }

          AddCommand addCommand = null;
          for (String path : index.getModified()) {
            // todo is path absolute or relative?
            if (!path.startsWith(IcsUrlBuilder.PROJECTS_DIR_NAME)) {
              if (addCommand == null) {
                addCommand = git.add();
              }
              addCommand.addFilepattern(path);
            }
          }
          if (addCommand != null) {
            addCommand.call();
          }
        }

        PersonIdent author = new PersonIdent(git.getRepository());
        PersonIdent committer = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), author.getEmailAddress());
        LOG.debug("Commit");
        git.commit().setAuthor(author).setCommitter(committer).setMessage("").call();
      }
    });
  }

  @NotNull
  private static String indexDiffToString(@NotNull IndexDiff diff) {
    StringBuilder builder = new StringBuilder();
    builder.append("To commit:");
    addList("Added", diff.getAdded(), builder);
    addList("Changed", diff.getChanged(), builder);
    addList("Removed", diff.getChanged(), builder);
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
    for (String path : list) {
      builder.append(", ").append(path);
    }
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
    return execute(new Task(indicator) {
      @Override
      protected void execute() throws Exception {
        JGitProgressMonitor progressMonitor = new JGitProgressMonitor(indicator);
        FetchResult fetchResult = git.fetch().setRemoveDeletedRefs(true).setProgressMonitor(progressMonitor).setCredentialsProvider(getCredentialsProvider()).call();
        if (LOG.isDebugEnabled()) {
          String messages = fetchResult.getMessages();
          if (!StringUtil.isEmptyOrSpaces(messages)) {
            LOG.debug(messages);
          }
        }

        Iterator<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates().iterator();
        TrackingRefUpdate refUpdate = refUpdates.hasNext() ? refUpdates.next() : null;
        if (refUpdate == null || refUpdate.getResult() == RefUpdate.Result.NO_CHANGE || refUpdate.getResult() == RefUpdate.Result.FORCED) {
          LOG.debug("Nothing to merge");
          return;
        }

        int attemptCount = 0;
        do {
          MergeCommand mergeCommand = git.merge();
          org.eclipse.jgit.lib.Ref ref = getUpstreamBranchRef();
          if (ref == null) {
            throw new AssertionError();
          }
          else {
            mergeCommand.include(ref);
          }

         MergeResult mergeResult = mergeCommand.setFastForward(MergeCommand.FastForwardMode.FF_ONLY).call();
          if (LOG.isDebugEnabled()) {
            LOG.debug(mergeResult.toString());
          }

          MergeResult.MergeStatus status = mergeResult.getMergeStatus();
          if (status.isSuccessful()) {
            rebase(progressMonitor);
            return;
          }
          else if (status != MergeResult.MergeStatus.ABORTED) {
            break;
          }
        }
        while (++attemptCount < 3);
      }

      private org.eclipse.jgit.lib.Ref getUpstreamBranchRef() throws IOException {
        return git.getRepository().getRef(Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
      }

      private void rebase(@NotNull JGitProgressMonitor progressMonitor) throws GitAPIException {
        RebaseResult result = null;
        do {
          if (result == null) {
            result = git.rebase().setUpstream(Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER).setProgressMonitor(progressMonitor).call();
          }
          else if (result.getStatus() == RebaseResult.Status.CONFLICTS) {
            throw new UnsupportedOperationException();
          }
          else if (result.getStatus() == RebaseResult.Status.NOTHING_TO_COMMIT) {
            result = git.rebase().setOperation(RebaseCommand.Operation.SKIP).call();
          }
          else {
            throw new UnsupportedOperationException();
          }
        }
        while (!result.getStatus().isSuccessful());
      }
    });
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