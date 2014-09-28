/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseTerminalModule extends LineCommandAdapter implements CommandRuntimeModule, InteractiveCommandListener {

  private static final Logger LOG = Logger.getInstance(BaseTerminalModule.class);

  @NotNull protected final CommandRuntime myRuntime;
  @NotNull protected final CommandExecutor myExecutor;

  // TODO: Do not accept executor here and make it as command runtime module
  protected BaseTerminalModule(@NotNull CommandRuntime runtime, @NotNull CommandExecutor executor) {
    myRuntime = runtime;
    myExecutor = executor;
  }

  @Override
  public void onStart(@NotNull Command command) throws SvnBindException {
  }

  protected boolean sendData(@NotNull String data) {
    try {
      myExecutor.write(data + LineSeparator.CRLF.getSeparatorString());
      return true;
    }
    catch (SvnBindException e) {
      // TODO: handle this more carefully
      LOG.info(e);
    }
    return false;
  }

  protected void cancelAuthentication() {
    myExecutor.destroyProcess("Authentication canceled for repository: " + myExecutor.getCommand().getRepositoryUrl());
  }
}
