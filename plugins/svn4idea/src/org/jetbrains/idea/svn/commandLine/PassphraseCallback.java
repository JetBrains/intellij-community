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
public class PassphraseCallback extends AuthCallbackCase {

  private static final String PASSPHRASE_FOR = "Passphrase for";

  PassphraseCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
    super(callback, url);
  }

  @Override
  public boolean canHandle(String error) {
    return error.startsWith(PASSPHRASE_FOR);
  }

  @Override
  boolean getCredentials(String errText) throws SvnBindException {
    // try to get from file
    /*if (myTried) {
      myAuthenticationCallback.clearPassiveCredentials(null, myBase);
    }*/
    myTried = true;
    if (myAuthenticationCallback.authenticateFor(null, myUrl, myAuthenticationCallback.getSpecialConfigDir() != null, false)) {
      return true;
    }
    throw new SvnBindException("Authentication canceled for : " + errText.substring(PASSPHRASE_FOR.length()));
  }
}
