// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdVersionClient extends BaseSvnClient implements VersionClient {

  private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
  private static final int COMMAND_TIMEOUT = 30 * 1000;

  @NotNull
  @Override
  public Version getVersion() throws SvnBindException {
    return parseVersion(runCommand(true));
  }

  @NotNull
  public ProcessOutput runCommand(boolean quiet) throws SvnBindException {
    Command command = new Command(SvnCommandName.version);
    if (quiet) {
    command.put("--quiet");
    }

    return newRuntime(myVcs).runLocal(command, COMMAND_TIMEOUT).getProcessOutput();
  }

  @NotNull
  private static Version parseVersion(@NotNull ProcessOutput output) throws SvnBindException {
    // TODO: This or similar check should likely go to CommandRuntime - to be applied for all commands
    if (output.isTimeout()) {
      throw new SvnBindException(message("error.could.not.get.svn.version", output.getExitCode(), output.getStderr()));
    }

    return parseVersion(output.getStdout());
  }

  @NotNull
  public static Version parseVersion(@NlsSafe @NotNull String versionText) throws SvnBindException {
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
