package com.jetbrains.python.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;

/**
 * @author yole
 */
public class PyDebugRunner extends GenericProgramRunner {
  @NotNull
  public String getRunnerId() {
    return "PyDebugRunner";
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof AbstractPythonRunConfiguration;
  }

  protected RunContentDescriptor doExecute(Project project, Executor executor, RunProfileState state,
                                           RunContentDescriptor contentToReuse,
                                           ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }

    final PythonCommandLineState pyState = (PythonCommandLineState) state;
    final CommandLinePatcher debug_server_patcher = new CommandLinePatcher() {
      public void patchCommandLine(GeneralCommandLine commandLine) {
        final String[] args = new String[]{
          PythonHelpersLocator.getHelperPath("pydev/pydevd.py"),
          "--client", "127.0.0.1",
          "--port", String.valueOf(serverSocket.getLocalPort()),
          "--file"
        };
        // script name is the last parameter; all other params are for python interpreter; insert just before name
        final ParametersList parameters_list = commandLine.getParametersList();
        int parameter_offset = pyState.getInterpreterOptionsCount();
        final PythonSdkFlavor flavor = pyState.getConfig().getSdkFlavor();
        if (flavor != null) {
          final Collection<String> options = flavor.getExtraDebugOptions();
          for (String option : options) {
            parameters_list.addAt(parameter_offset++, option);
          }
        }
        for (int i = 0; i < args.length; i++) {
          parameters_list.addAt(i + parameter_offset, args[i]);
        }
      }
    };
    RunProfile profile = env.getRunProfile();
    CommandLinePatcher run_config_patcher = null;
    if (state instanceof PythonCommandLineState && profile instanceof PythonRunConfiguration) {
      run_config_patcher = (PythonRunConfiguration)profile;
    }
    final ExecutionResult result = pyState.execute(debug_server_patcher, run_config_patcher);

    final XDebugSession session = XDebuggerManager.getInstance(project).
        startSession(this, env, contentToReuse, new XDebugProcessStarter() {
          @NotNull
          public XDebugProcess start(@NotNull final XDebugSession session) {
            return new PyDebugProcess(session, serverSocket, result.getExecutionConsole(), result.getProcessHandler());
          }
        });
    return session.getRunContentDescriptor();
  }
}
