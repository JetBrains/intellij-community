package com.jetbrains.python.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.net.NetUtils;
import com.jetbrains.django.run.ExecutionHelper;
import com.jetbrains.django.run.Runner;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author oleg
 */
public class PydevConsoleRunner extends PyConsoleRunner {
  private final int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;

  protected PydevConsoleRunner(@NotNull final Project project,
                               @NotNull final String consoleTitle,
                               @NotNull final CommandLineArgumentsProvider provider,
                               @Nullable final String workingDir,
                               int[] ports) {
    super(project, consoleTitle, provider, workingDir);
    myPorts = ports;
  }

  public static void run(@NotNull final Project project,
                         @NotNull final Module module,
                         @NotNull final Sdk sdk) {
    final String consoleTitle = "TRUE console";
    final int[] ports;
    try {
      // File "pydev/pydevconsole.py", line 223, in <module>
      // port, client_port = sys.argv[1:3]
      ports = NetUtils.findAvailableSocketPorts(2);
    }
    catch (IOException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
      return;
    }
    final ArrayList<String> args = new ArrayList<String>(Arrays.asList(sdk.getHomePath(), "-u", PythonHelpersLocator.getHelperPath("pydev/pydevconsole.py")));
    for (int port : ports) {
      args.add(String.valueOf(port));
    }
    final CommandLineArgumentsProvider provider = new CommandLineArgumentsProvider() {
      public String[] getArguments() {
        return args.toArray(new String[args.size()]);
      }

      public boolean passParentEnvs() {
        return false;
      }

      public Map<String, String> getAdditionalEnvs() {
        return Collections.emptyMap();
      }
    };

    final PydevConsoleRunner consoleRunner = new PydevConsoleRunner(project, consoleTitle, provider, DjangoUtil.getProjectRoot(module), ports);
    try {
      consoleRunner.initAndRun();
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  @Override
  protected Process createProcess() throws ExecutionException {
    final Process server = Runner.createProcess(myWorkingDir, true, myProvider.getAdditionalEnvs(), myProvider.getArguments());
    try {
      myPydevConsoleCommunication = new PydevConsoleCommunication(getProject(), myPorts[0], server, myPorts[1]);
    }
    catch (Exception e) {
      throw new ExecutionException(e.getMessage());
    }
    return server;
  }

  @Override
  public void initAndRun() throws ExecutionException {
    super.initAndRun();
    sendInput("import sys; print('Python %s on %s' % (sys.version, sys.platform))\n");
  }

  @Override
  protected void sendInput(final String input) {
    if (myPydevConsoleCommunication != null){
      myPydevConsoleCommunication.execInterpreter(input, new ICallback<Object, InterpreterResponse>() {
        public Object call(final InterpreterResponse interpreterResponse) {
          final LanguageConsoleImpl console = myConsoleView.getConsole();
          // TODO[oleg] More sophisticated callback required!
          // At least this one works!
          if (!StringUtil.isEmpty(interpreterResponse.err)){
            PyConsoleProcessHandler.processOutput(console, interpreterResponse.err, ProcessOutputTypes.STDERR);
          } else {
            PyConsoleProcessHandler.processOutput(console, interpreterResponse.out, ProcessOutputTypes.STDOUT);
          }
          return null;
        }
      });
    }
  }
}