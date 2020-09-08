// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.svn.SvnBundle.message;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseTerminalModule extends LineCommandAdapter implements CommandRuntimeModule, InteractiveCommandListener {

  private static final Logger LOG = Logger.getInstance(BaseTerminalModule.class);

  @NotNull protected final CommandRuntime myRuntime;
  @NotNull protected final CommandExecutor myExecutor;

  protected boolean mySkipOneLine;

  // TODO: Do not accept executor here and make it as command runtime module
  protected BaseTerminalModule(@NotNull CommandRuntime runtime, @NotNull CommandExecutor executor) {
    myRuntime = runtime;
    myExecutor = executor;
  }

  @Override
  public void onStart(@NotNull Command command) {
  }

  /**
   * When we send data to svn - we then get it back from corresponding stream. We could receive, for example:
   * - just sent data - for http, https in Unix
   * - prompt line + sent data (at the end) - for http, https in Windows
   * - just new line - for ssh client (as ssh does not output sent data to console)
   * <p/>
   * That is why if the next line (after answer is sent) is not explicitly handled, we skip it anyway.
   */
  @Override
  public boolean handlePrompt(String line, Key outputType) {
    boolean result = doHandlePrompt(line, outputType);

    if (!result && mySkipOneLine) {
      LOG.debug("Skipped " + outputType + " line: " + line);

      mySkipOneLine = false;
      result = true;
    }

    return result;
  }

  protected abstract boolean doHandlePrompt(String line, Key outputType);

  protected boolean sendData(@NotNull String data) {
    try {
      mySkipOneLine = true;
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
    myExecutor.destroyProcess(
      message("error.authentication.canceled.for.repository", myExecutor.getCommand().requireRepositoryUrl().toDecodedString()));
  }
}
