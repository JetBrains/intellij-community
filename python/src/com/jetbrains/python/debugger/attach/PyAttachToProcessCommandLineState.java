package com.jetbrains.python.debugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.debugger.PyRemoteDebugProcess;
import com.jetbrains.python.debugger.PyRemoteDebugProcessAware;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import com.jetbrains.python.run.PythonScriptCommandLineState;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;

/**
 * @author traff
 */
public class PyAttachToProcessCommandLineState extends PythonScriptCommandLineState {


  private PyAttachToProcessCommandLineState(PythonRunConfiguration runConfiguration,
                                            ExecutionEnvironment env) {
    super(runConfiguration, env);
  }

  public static PyAttachToProcessCommandLineState create(@NotNull Project project, @NotNull String sdkPath, int port, int pid)
    throws ExecutionException {
    PythonRunConfiguration conf =
      (PythonRunConfiguration)PythonConfigurationType.getInstance().getFactory().createTemplateConfiguration(project);
    conf.setScriptName(PythonHelper.ATTACH_DEBUGGER.asParamString());
    conf.setSdkHome(sdkPath);
    conf.setScriptParameters("--port " + port + " --pid " + pid);

    ExecutionEnvironment env =
      ExecutionEnvironmentBuilder.create(project, DefaultDebugExecutor.getDebugExecutorInstance(), conf).build();


    return new PyAttachToProcessCommandLineState(conf, env);
  }


  @Override
  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    ProcessHandler handler = super.doCreateProcess(commandLine);

    return new PyRemoteDebugProcessHandler(handler);
  }

  public static class PyRemoteDebugProcessHandler extends ProcessHandler implements PyRemoteDebugProcessAware {
    private final ProcessHandler myHandler;
    private PyRemoteDebugProcess myProcess = null;

    public PyRemoteDebugProcessHandler(ProcessHandler handler) {
      myHandler = handler;
      myHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          PyRemoteDebugProcessHandler.this.notifyTextAvailable(event.getText(), outputType);
        }
      });
    }

    @Override
    public void startNotify() {
      super.startNotify();
      myHandler.startNotify();
    }

    @Override
    protected void destroyProcessImpl() {
      if (myProcess != null) {
        myProcess.stop();
      }
      detachProcessImpl();
    }

    @Override
    protected void detachProcessImpl() {
      notifyProcessTerminated(0);
      notifyTextAvailable("Server stopped.\n", ProcessOutputTypes.SYSTEM);
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Override
    public OutputStream getProcessInput() {
      return null;
    }

    public void setRemoteDebugProcess(PyRemoteDebugProcess process) {
      myProcess = process;
    }
  }
}
