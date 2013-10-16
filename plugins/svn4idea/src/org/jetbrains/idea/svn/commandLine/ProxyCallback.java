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
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProxyCallback extends AuthCallbackCase {

  private static final String UNABLE_TO_CONNECT_CODE = "svn: E170001:";
  private static final String CANNOT_AUTHENTICATE_TO_PROXY = "Could not authenticate to proxy server";

  ProxyCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
    super(callback, url);
  }

  @Override
  public boolean canHandle(String error) {
    return error.startsWith(UNABLE_TO_CONNECT_CODE) && error.contains(CANNOT_AUTHENTICATE_TO_PROXY);
  }

  @Override
  boolean getCredentials(String errText) throws SvnBindException {
    return myAuthenticationCallback.askProxyCredentials(myUrl);
  }
}
