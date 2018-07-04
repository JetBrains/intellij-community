// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;

import java.util.HashSet;
import java.util.Set;

public class SvnAuthenticationProvider implements AuthenticationProvider {

  private final Project myProject;
  private final SvnAuthenticationNotifier myAuthenticationNotifier;
  private final AuthenticationProvider mySvnInteractiveAuthenticationProvider;
  private final SvnAuthenticationManager myAuthenticationManager;
  private static final Set<Thread> ourForceInteractive = new HashSet<>();

  public SvnAuthenticationProvider(final SvnVcs svnVcs,
                                   final AuthenticationProvider provider,
                                   final SvnAuthenticationManager authenticationManager) {
    myAuthenticationManager = authenticationManager;
    myProject = svnVcs.getProject();
    myAuthenticationNotifier = svnVcs.getAuthNotifier();
    mySvnInteractiveAuthenticationProvider = provider;
  }

  public AuthenticationData requestClientAuthentication(final String kind,
                                                        final Url url,
                                                        final String realm,
                                                        final boolean canCache) {
    final SvnAuthenticationNotifier.AuthenticationRequest obj =
      new SvnAuthenticationNotifier.AuthenticationRequest(myProject, kind, url, realm);
    final Url wcUrl = myAuthenticationNotifier.getWcUrl(obj);
    if (wcUrl == null || ourForceInteractive.contains(Thread.currentThread())) {
      // outside-project url
      return mySvnInteractiveAuthenticationProvider.requestClientAuthentication(kind, url, realm, canCache);
    } else {
      if (myAuthenticationNotifier.ensureNotify(obj)) {
        return myAuthenticationManager.requestFromCache(kind, realm);
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

  public AcceptResult acceptServerAuthentication(Url url, String realm, final Object certificate, final boolean canCache) {
    return mySvnInteractiveAuthenticationProvider.acceptServerAuthentication(url, realm, certificate, canCache);
  }
}
