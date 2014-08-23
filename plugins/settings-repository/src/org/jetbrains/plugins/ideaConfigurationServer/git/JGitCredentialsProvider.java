package org.jetbrains.plugins.ideaConfigurationServer.git;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.ui.UIUtil;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.plugins.ideaConfigurationServer.AuthDialog;
import org.jetbrains.plugins.ideaConfigurationServer.BaseRepositoryManager;
import org.jetbrains.plugins.ideaConfigurationServer.IcsManager;

import java.io.*;

class JGitCredentialsProvider extends CredentialsProvider {
  // we store only one pair for any URL, don't want to add complexity, OS keychain should be used
  private String username;
  private String password;

  public JGitCredentialsProvider() {
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
        BaseRepositoryManager.LOG.error(e);
      }
    }
  }

  private static File getPasswordStorageFile() {
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

    if (userNameItem == null && passwordItem == null) {
      return true;
    }

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

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
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
          catch (IOException e) {
            BaseRepositoryManager.LOG.error(e);
          }
        }
      });
    }
    return ok;
  }

  @Override
  public void reset(URIish uri) {
    password = null;
  }
}