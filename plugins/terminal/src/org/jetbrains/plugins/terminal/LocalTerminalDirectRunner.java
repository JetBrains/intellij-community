package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.jediterm.emulator.TtyConnector;
import com.jediterm.pty.PtyProcess;
import com.jediterm.pty.PtyProcessTtyConnector;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
public class LocalTerminalDirectRunner extends AbstractTerminalRunner<PtyProcess> {

  private final Charset myDefaultCharset;
  private final String myCommand;

  public LocalTerminalDirectRunner(Project project, Charset charset, String command) {
    super(project);
    myDefaultCharset = charset;
    myCommand = command;
  }

  @Override
  protected PtyProcess createProcess() throws ExecutionException {
    Map<String, String> envs = new HashMap<String, String>(System.getenv());
    envs.put("TERM", "vt100");
    return new PtyProcess(myCommand, new String[]{myCommand}, envs);
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
        return true;
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
