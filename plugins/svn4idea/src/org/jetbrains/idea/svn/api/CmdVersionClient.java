// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.api;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.commandLine.Command;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class CmdVersionClient extends BaseSvnClient implements VersionClient {

  private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
  private static final int COMMAND_TIMEOUT = 30 * 1000;

  @Override
  public @NotNull Version getVersion() throws SvnBindException {
    return parseVersion(runCommand(true));
  }

  public @NotNull ProcessOutput runCommand(boolean quiet) throws SvnBindException {
    Command command = new Command(SvnCommandName.version);
    if (quiet) {
    command.put("--quiet");
    }

    return newRuntime(myVcs).runLocal(command, COMMAND_TIMEOUT).getProcessOutput();
  }

  private static @NotNull Version parseVersion(@NotNull ProcessOutput output) throws SvnBindException {
    // TODO: This or similar check should likely go to CommandRuntime - to be applied for all commands
    if (output.isTimeout()) {
      throw new SvnBindException(message("error.could.not.get.svn.version", output.getExitCode(), output.getStderr()));
    }

    return parseVersion(output.getStdout());
  }

  public static @NotNull Version parseVersion(@NlsSafe @NotNull String versionText) throws SvnBindException {
    Version result = null;
    Exception cause = null;

    Matcher matcher = VERSION.matcher(versionText);
    boolean found = matcher.find();

    if (found) {
      try {
        result = new Version(getInt(matcher.group(1)), getInt(matcher.group(2)), getInt(matcher.group(3)));
      }
      catch (NumberFormatException e) {
        cause = e;
      }
    }

    if (!found || cause != null) {
      throw new SvnBindException(message("error.could.not.parse.svn.version", versionText), cause);
    }

    return result;
  }

  private static int getInt(@NotNull String value) {
    return Integer.parseInt(value);
  }
}
