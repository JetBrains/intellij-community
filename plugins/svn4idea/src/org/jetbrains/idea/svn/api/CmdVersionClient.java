package org.jetbrains.idea.svn.api;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdVersionClient extends BaseSvnClient implements VersionClient {

  private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");

  @NotNull
  @Override
  public Version getVersion() throws VcsException {
    // TODO: Do not use common command running mechanism for now - to preserve timeout behavior.
    ProcessOutput output;

    try {
      output = runCommand();
    }
    catch (ExecutionException e) {
      throw new VcsException(e);
    }

    return parseVersion(output);
  }

  private static ProcessOutput runCommand() throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(SvnApplicationSettings.getInstance().getCommandLinePath());
    commandLine.addParameter(SvnCommandName.version.getName());
    commandLine.addParameter("--quiet");

    CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
    return handler.runProcess(30 * 1000);
  }

  @NotNull
  private static Version parseVersion(@NotNull ProcessOutput output) throws VcsException {
    if (output.isTimeout() || (output.getExitCode() != 0) || !output.getStderr().isEmpty()) {
      throw new VcsException(
        String.format("Exit code: %d, Error: %s, Timeout: %b", output.getExitCode(), output.getStderr(), output.isTimeout()));
    }

    return parseVersion(output.getStdout());
  }

  @NotNull
  public static Version parseVersion(@NotNull String versionText) throws VcsException {
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
      throw new VcsException(String.format("Could not parse svn version: %s", versionText), cause);
    }

    return result;
  }

  private static int getInt(@NotNull String value) {
    return Integer.parseInt(value);
  }
}
