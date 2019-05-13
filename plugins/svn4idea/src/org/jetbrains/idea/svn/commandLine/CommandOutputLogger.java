package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.diagnostic.Logger;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandOutputLogger extends com.intellij.execution.process.CommandOutputLogger {
  private static final Logger LOG = Logger.getInstance(CommandOutputLogger.class);
  public CommandOutputLogger() {
    super(LOG);
  }
}
