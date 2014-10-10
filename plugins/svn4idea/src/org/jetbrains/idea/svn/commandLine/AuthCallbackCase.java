/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class AuthCallbackCase {
  protected final SVNURL myUrl;
  @NotNull protected final AuthenticationService myAuthenticationService;

  AuthCallbackCase(@NotNull AuthenticationService authenticationService, SVNURL url) {
    myAuthenticationService = authenticationService;
    myUrl = url;
  }

  public abstract boolean canHandle(final String error);

  abstract boolean getCredentials(final String errText) throws SvnBindException;

  public void updateParameters(@NotNull Command command) {
  }

  protected SVNURL parseUrl(String urlValue) {
    try {
      return SVNURL.parseURIEncoded(urlValue);
    }
    catch (SVNException e) {
      return null;
    }
  }
}
