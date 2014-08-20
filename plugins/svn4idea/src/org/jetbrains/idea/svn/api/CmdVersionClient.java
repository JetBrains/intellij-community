package org.jetbrains.idea.svn.api;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.commandLine.Command;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
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
  public Version getVersion() throws SvnBindException {
    return parseVersion(runCommand());
  }

  private static ProcessOutput runCommand() throws SvnBindException {
    // TODO: Seems CommandRuntime should be used here when its api is more robust (to specify timeout or so).
    Command command = new Command(SvnCommandName.version);
    command.put("--quiet");

    CommandExecutor executor = new CommandExecutor(SvnApplicationSettings.getInstance().getCommandLinePath(), command);
    executor.run(30 * 1000);

    return executor.getProcessOutput();
  }

  @NotNull
  private static Version parseVersion(@NotNull ProcessOutput output) throws SvnBindException {
    if (output.isTimeout() || (output.getExitCode() != 0) || !output.getStderr().isEmpty()) {
      throw new SvnBindException(
        String.format("Exit code: %d, Error: %s, Timeout: %b", output.getExitCode(), output.getStderr(), output.isTimeout()));
    }

    return parseVersion(output.getStdout());
  }

  @NotNull
  public static Version parseVersion(@NotNull String versionText) throws SvnBindException {
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
      throw new SvnBindException(String.format("Could not parse svn version: %s", versionText), cause);
    }

    return result;
  }

  private static int getInt(@NotNull String value) {
    return Integer.parseInt(value);
  }
}
