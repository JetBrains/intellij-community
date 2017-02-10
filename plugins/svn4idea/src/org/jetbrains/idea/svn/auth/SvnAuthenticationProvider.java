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
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;

import java.util.HashSet;
import java.util.Set;

/**
 * @author alex
 */
public class SvnAuthenticationProvider implements ISVNAuthenticationProvider {

  private final Project myProject;
  private final SvnAuthenticationNotifier myAuthenticationNotifier;
  private final ISVNAuthenticationProvider mySvnInteractiveAuthenticationProvider;
  private final SvnAuthenticationManager myAuthenticationManager;
  private static final Set<Thread> ourForceInteractive = new HashSet<>();

  public SvnAuthenticationProvider(final SvnVcs svnVcs, final ISVNAuthenticationProvider provider,
                                   final SvnAuthenticationManager authenticationManager) {
    myAuthenticationManager = authenticationManager;
    myProject = svnVcs.getProject();
    myAuthenticationNotifier = svnVcs.getAuthNotifier();
    mySvnInteractiveAuthenticationProvider = provider;
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
    if (wcUrl == null || ourForceInteractive.contains(Thread.currentThread())) {
      // outside-project url
      return mySvnInteractiveAuthenticationProvider.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
    } else {
      if (myAuthenticationNotifier.ensureNotify(obj)) {
        return myAuthenticationManager.requestFromCache(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
      }
    }
    return null;
  }
  
  public static void forceInteractive() {
    ourForceInteractive.add(Thread.currentThread());
  }
  
  public static void clearInteractive() {
    ourForceInteractive.remove(Thread.currentThread());
  }

  public int acceptServerAuthentication(SVNURL url, String realm, final Object certificate, final boolean resultMayBeStored) {
    return mySvnInteractiveAuthenticationProvider.acceptServerAuthentication(url, realm, certificate, resultMayBeStored);
  }
}
