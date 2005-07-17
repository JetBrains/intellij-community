package org.jetbrains.idea.svn.dialogs;

import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.*;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.security.cert.X509Certificate;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 17:00:17
 * To change this template use File | Settings | File Templates.
 */
public class SvnAuthenticationProvider implements ISVNAuthenticationProvider {
  private Project myProject;

  public SvnAuthenticationProvider(Project project) {
    myProject = project;
  }

  public SVNAuthentication requestClientAuthentication(String kind,
                                                       String url,
                                                       final String realm,
                                                       String errorMessage,
                                                       final SVNAuthentication previousAuth,
                                                       final boolean authMayBeStored) {
    final SVNAuthentication[] result = new SVNAuthentication[1];
    Runnable command = null;
    final String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : System
      .getProperty("user.name");
    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
      command = new Runnable() {
        public void run() {
          SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myProject);
          dialog.setup(realm, userName, authMayBeStored);
          if (previousAuth == null) {
            dialog.setTitle("Authentication Required");
          }
          else {
            dialog.setTitle("Authentication Required (Authentication Failed)");
          }
          dialog.show();
          if (dialog.isOK()) {
            result[0] = new SVNPasswordAuthentication(dialog.getUserName(), dialog.getPassword(), dialog.isSaveAllowed());
          }
        }
      };
    }
    else if (ISVNAuthenticationManager.SSH.equals(kind)) {
      command = new Runnable() {
        public void run() {
          SSHCredentialsDialog dialog = new SSHCredentialsDialog(myProject);
          dialog.setup(realm, userName, authMayBeStored);
          if (previousAuth == null) {
            dialog.setTitle("Authentication Required");
          }
          else {
            dialog.setTitle("Authentication Required (Authentication Failed)");
          }
          dialog.show();
          if (dialog.isOK()) {
            if (dialog.getKeyFile() != null && dialog.getKeyFile().trim().length() > 0) {
              String passphrase = dialog.getPassphrase();
              if (passphrase != null && passphrase.length() == 0) {
                passphrase = null;
              }
              result[0] = new SVNSSHAuthentication(dialog.getUserName(), new File(dialog.getKeyFile()), passphrase, dialog.isSaveAllowed());
            } else {
              result[0] = new SVNSSHAuthentication(dialog.getUserName(), dialog.getPassword(), dialog.isSaveAllowed());
            }
          }
        }
      };
    }

    if (command != null) {
      if (SwingUtilities.isEventDispatchThread()) {
        command.run();
      }
      else {
        try {
          SwingUtilities.invokeAndWait(command);
        }
        catch (InterruptedException e) {
          //
        }
        catch (InvocationTargetException e) {
          //
        }
      }
    }
    return result[0];
  }

  public int acceptServerAuthentication(String url, String realm, final Object certificate, final boolean resultMayBeStored) {
    if (!(certificate instanceof X509Certificate)) {
      return ACCEPTED;
    }
    final int[] result = new int[1];
    Runnable command = new Runnable() {
      public void run() {
        ServerSSLDialog dialog = new ServerSSLDialog(myProject, (X509Certificate) certificate, resultMayBeStored);
        dialog.show();
        result[0] = dialog.getResult();

      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      command.run();
    }
    else {
      try {
        SwingUtilities.invokeAndWait(command);
      }
      catch (InterruptedException e) {
        //
      }
      catch (InvocationTargetException e) {
        //
      }
    }
    return result[0];
  }
}
