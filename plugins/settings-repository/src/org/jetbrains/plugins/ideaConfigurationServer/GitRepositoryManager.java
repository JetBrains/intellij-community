package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.UIUtil;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

final class GitRepositoryManager extends BaseRepositoryManager {
  private final Git git;

  private CredentialsProvider credentialsProvider;

  public GitRepositoryManager() throws IOException {
    FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
    repositoryBuilder.setGitDir(new File(dir, Constants.DOT_GIT));
    Repository repository = repositoryBuilder.build();
    if (!dir.exists()) {
      repository.create();
    }

    git = new Git(repository);
  }

  private CredentialsProvider getCredentialsProvider() {
    if (credentialsProvider == null) {
      credentialsProvider = new MyCredentialsProvider();
    }
    return credentialsProvider;
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
      config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
      config.setString("branch", "master", "remote", "origin");
      config.setString("branch", "master", "merge", "refs/heads/master");
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
  public ActionCallback commit() {
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
        PersonIdent ident = new PersonIdent(ApplicationInfoEx.getInstanceEx().getFullApplicationName(), "dev@null.org");
        git.commit().setAuthor(ident).setCommitter(ident).setMessage("").call();
      }
    });
    return callback;
  }

  @Override
  @NotNull
  public ActionCallback push(@NotNull final ProgressIndicator indicator) {
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
        git.push().setProgressMonitor(new JGitProgressMonitor(indicator)).setCredentialsProvider(getCredentialsProvider()).call();
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

  private class MyCredentialsProvider extends CredentialsProvider {
    private String username;
    private String password;

    private MyCredentialsProvider() {
      File loginDataFile = getPasswordStorageFile();
      if (loginDataFile.exists()) {
        try {
          boolean hasErrors = true;
          DataInputStream in = new DataInputStream(new FileInputStream(loginDataFile));
          try {
            username = PasswordUtil.decodePassword(IOUtil.readString(in));
            password = PasswordUtil.decodePassword(IOUtil.readString(in));
            hasErrors = false;
          }
          finally {
            if (hasErrors) {
              //noinspection ResultOfMethodCallIgnored
              loginDataFile.delete();
            }
            in.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }

    private File getPasswordStorageFile() {
      return new File(IcsManager.getPluginSystemDir(), ".git_auth");
    }

    @Override
    public boolean isInteractive() {
      return true;
    }

    @Override
    public boolean supports(CredentialItem... items) {
      for (CredentialItem item : items) {
        if (item instanceof CredentialItem.Password) {
          continue;
        }
        if (item instanceof CredentialItem.Username) {
          continue;
        }
        return false;
      }
      return true;
    }

    @Override
    public boolean get(final URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
      CredentialItem.Username userNameItem = null;
      CredentialItem.Password passwordItem = null;
      for (CredentialItem item : items) {
        if (item instanceof CredentialItem.Username) {
          userNameItem = (CredentialItem.Username)item;
        }
        else if (item instanceof CredentialItem.Password) {
          passwordItem = (CredentialItem.Password)item;
        }
      }

      if (userNameItem != null || passwordItem != null) {
        String u = uri.getUser();
        String p;
        if (u == null) {
          // username is not in the url - reading pre-filled value from the password storage
          u = username;
          p = password;
        }
        else {
          p = StringUtil.nullize(uri.getPass(), true);
          // username is in url - read password only if it is for the same user
          if (u.equals(username) && p == null) {
            p = password;
          }
        }

        boolean ok;
        if (u != null && p != null) {
          ok = true;
        }
        else {
          final Ref<AuthDialog> dialogRef = Ref.create();
          final String finalU = u;
          final String finalP = p;
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              AuthDialog dialog = new AuthDialog("Login required", "Login to " + uri, finalU, finalP);
              dialogRef.set(dialog);
              dialog.show();
            }
          });
          ok = dialogRef.get().isOK();
          if (ok) {
            u = dialogRef.get().getUsername();
            p = dialogRef.get().getPassword();
            if (StringUtil.isEmptyOrSpaces(p)) {
              p = "x-oauth-basic";
            }
          }
        }

        if (ok) {
          if (userNameItem != null) {
            userNameItem.setValue(u);
          }
          if (passwordItem != null) {
            passwordItem.setValue(p.toCharArray());
          }
          password = p;
          username = u;

          taskProcessor.add(new ThrowableRunnable<Exception>() {
            @Override
            public void run() throws Exception {
              File loginDataFile = getPasswordStorageFile();
              FileUtil.createParentDirs(loginDataFile);
              DataOutputStream out = new DataOutputStream(new FileOutputStream(loginDataFile));
              try {
                IOUtil.writeString(PasswordUtil.encodePassword(username), out);
                IOUtil.writeString(PasswordUtil.encodePassword(password), out);
              }
              finally {
                out.close();
              }
            }
          });
        }
        return ok;
      }
      return true;
    }
  }
}