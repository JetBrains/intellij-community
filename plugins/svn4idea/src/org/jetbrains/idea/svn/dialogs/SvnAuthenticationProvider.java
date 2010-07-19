/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.GuiUtils;
import com.intellij.util.SystemProperties;
import org.jetbrains.idea.svn.SvnAuthenticationManager;
import org.jetbrains.idea.svn.SvnAuthenticationNotifier;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;

import java.lang.reflect.InvocationTargetException;
import java.security.cert.X509Certificate;

/**
 * @author alex
 */
public class SvnAuthenticationProvider implements ISVNAuthenticationProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.dialogs.SvnAuthenticationProvider");
  private final Project myProject;
  private final SvnAuthenticationNotifier myAuthenticationNotifier;
  private final SvnInteractiveAuthenticationProvider mySvnInteractiveAuthenticationProvider;

  public SvnAuthenticationProvider(final SvnVcs svnVcs, final SvnAuthenticationManager manager) {
    myProject = svnVcs.getProject();
    myAuthenticationNotifier = svnVcs.getAuthNotifier();
    mySvnInteractiveAuthenticationProvider = new SvnInteractiveAuthenticationProvider(svnVcs, manager);
  }

  private void log(final String s) {
    LOG.debug(s);
  }

  public SVNAuthentication requestClientAuthentication(final String kind,
                                                       final SVNURL url,
                                                       final String realm,
                                                       SVNErrorMessage errorMessage,
                                                       final SVNAuthentication previousAuth,
                                                       final boolean authMayBeStored) {
    if (ApplicationManager.getApplication().isUnitTestMode() && ISVNAuthenticationManager.USERNAME.equals(kind)) {
      final String userName = previousAuth != null && previousAuth.getUserName() != null ? previousAuth.getUserName() : SystemProperties.getUserName();
      return new SVNUserNameAuthentication(userName, false);
    }
    final SvnAuthenticationNotifier.AuthenticationRequest obj =
      new SvnAuthenticationNotifier.AuthenticationRequest(myProject, kind, url, realm);
    final SVNURL wcUrl = myAuthenticationNotifier.getWcUrl(obj);
    if (wcUrl == null) {
      // outside-project url
      return mySvnInteractiveAuthenticationProvider.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
    } else {
      myAuthenticationNotifier.ensureNotify(obj);
    }
    return null;
  }

  public int acceptServerAuthentication(SVNURL url, String realm, final Object certificate, final boolean resultMayBeStored) {
    if (!(certificate instanceof X509Certificate)) {
      return ACCEPTED;
    }
    final int[] result = new int[1];
    Runnable command = new Runnable() {
      public void run() {
        ServerSSLDialog dialog = new ServerSSLDialog(myProject, (X509Certificate)certificate, resultMayBeStored);
        dialog.show();
        result[0] = dialog.getResult();

      }
    };
    try {
      GuiUtils.runOrInvokeAndWait(command);
    }
    catch (InterruptedException e) {
      //
    }
    catch (InvocationTargetException e) {
      //
    }
    return result[0];
  }
}
