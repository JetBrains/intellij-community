package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.jediterm.emulator.TtyConnector;
import com.jediterm.pty.PtyProcess;
import com.jediterm.pty.PtyProcessTtyConnector;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {

  private final Charset myDefaultCharset;
  private final String myCommand;
  private final String[] myArguments;

  public LocalTerminalDirectRunner(Project project, Charset charset, String command, String[] arguments) {
    super(project);
    myDefaultCharset = charset;
    myCommand = command;
    myArguments = arguments;
  }

  @Override
  protected PtyProcess createProcess() throws ExecutionException {
    return new PtyProcess(myCommand, myArguments);
  }

  @Override
  protected ProcessHandler createProcessHandler(final PtyProcess process) {
    return new ProcessHandler() {
      @Override
      protected void destroyProcessImpl() {
        process.destroy();
      }

      @Override
      protected void detachProcessImpl() {
        destroyProcessImpl();
      }

      @Override
      public boolean detachIsDefault() {
        return false;
      }

      @Nullable
      @Override
      public OutputStream getProcessInput() {
        return process.getOutputStream();
      }
    };
  }

  @Override
  protected TtyConnector createTtyConnector(PtyProcess process) {
    return new PtyProcessTtyConnector(process, myDefaultCharset);
  }

  @Override
  protected String getTerminalConnectionName(PtyProcess process) {
    return process.getCommandLineString();
  }
}
