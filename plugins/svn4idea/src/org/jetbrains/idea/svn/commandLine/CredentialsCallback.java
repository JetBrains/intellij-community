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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class CredentialsCallback extends AuthCallbackCase {

  private static final String AUTHENTICATION_REALM = "Authentication realm:";

  CredentialsCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
    super(callback, url);
  }

  @Override
  public boolean canHandle(String error) {
    return error.startsWith(AUTHENTICATION_REALM);
  }

  @Override
  boolean getCredentials(String errText) throws SvnBindException {
    final String realm =
      errText.startsWith(AUTHENTICATION_REALM)
      ? cutFirstLine(errText).substring(AUTHENTICATION_REALM.length()).trim()
      : null;
    final boolean isPassword = StringUtil.containsIgnoreCase(errText, "password");
    if (myTried) {
      myAuthenticationCallback.clearPassiveCredentials(realm, myUrl, isPassword);
    }
    myTried = true;
    if (myAuthenticationCallback.authenticateFor(realm, myUrl, myAuthenticationCallback.getSpecialConfigDir() != null, isPassword)) {
      return true;
    }
    throw new SvnBindException("Authentication canceled for realm: " + realm);
  }

  private static String cutFirstLine(final String text) {
    final int idx = text.indexOf('\n');
    if (idx == -1) return text;
    return text.substring(0, idx);
  }
}
