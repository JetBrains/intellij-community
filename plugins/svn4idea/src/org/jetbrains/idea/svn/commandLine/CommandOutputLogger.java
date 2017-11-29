package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandOutputLogger extends ProcessAdapter {

  private static final Logger LOG = Logger.getInstance(CommandOutputLogger.class);

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    String line =  event.getText();

    if (LOG.isDebugEnabled()) {
      LOG.debug(line);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      System.out.print(line);
    }
  }
}
