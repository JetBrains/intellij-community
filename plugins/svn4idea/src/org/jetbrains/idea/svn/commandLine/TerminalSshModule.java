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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalSshModule extends LineCommandAdapter implements CommandRuntimeModule, InteractiveCommandListener {

  private static final Logger LOG = Logger.getInstance(TerminalSshModule.class);

  @NotNull private final CommandExecutor myExecutor;
  @NotNull private final AuthenticationCallback myAuthCallback;

  // TODO: Do not accept executor here and make it as command runtime module
  public TerminalSshModule(@NotNull CommandExecutor executor, @NotNull AuthenticationCallback authCallback) {
    myExecutor = executor;
    myAuthCallback = authCallback;
  }

  @Override
  public void onStart(@NotNull Command command) throws SvnBindException {
  }

  @Override
  public boolean handlePrompt(String line, Key outputType) {
    boolean result = false;

    if (line.toLowerCase().contains("enter passphrase for key")) {
      result = handlePassphrase();
    }

    return result;
  }

  private boolean handlePassphrase() {
    SVNAuthentication authentication =
      myAuthCallback.requestCredentials(myExecutor.getCommand().getRepositoryUrl(), ISVNAuthenticationManager.SSH);

    if (authentication != null && authentication instanceof SVNSSHAuthentication) {
      try {
        myExecutor.write(String.format("%s\n", ((SVNSSHAuthentication)authentication).getPassphrase()));
        return true;
      }
      catch (SvnBindException e) {
        // TODO: handle this more carefully
        LOG.info(e);
      }
    }
    return false;
  }
}
