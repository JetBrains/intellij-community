package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.remote.PyRemoteSdkData;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyRemoteProcessStarter {
  public ProcessHandler startRemoteProcess(@NotNull Sdk sdk,
                                           @NotNull GeneralCommandLine commandLine,
                                           @Nullable Project project,
                                           @Nullable PathMappingSettings mappingSettings)
    throws ExecutionException {
    PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
    if (manager != null) {
      ProcessHandler processHandler;

      while (true) {
        try {
          processHandler = doStartRemoteProcess(sdk, commandLine, manager, project, mappingSettings);
          break;
        }
        catch (ExecutionException e) {
          final Application application = ApplicationManager.getApplication();
          if (application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment())) {
            throw new RuntimeException(e);
          }
          if (Messages.showYesNoDialog(e.getMessage() + "\nTry again?", "Can't Run Remote Interpreter", Messages.getErrorIcon()) ==
              Messages.NO) {
            throw new ExecutionException("Can't run remote python interpreter: " + e.getMessage(), e);
          }
        }
      }
      ProcessTerminatedListener.attach(processHandler);
      return processHandler;
    }
    else {
      throw new PythonRemoteInterpreterManager.PyRemoteInterpreterExecutionException();
    }
  }

  protected ProcessHandler doStartRemoteProcess(@NotNull Sdk sdk,
                                                @NotNull GeneralCommandLine commandLine,
                                                @NotNull PythonRemoteInterpreterManager manager,
                                                @Nullable Project project,
                                                @Nullable PathMappingSettings settings)
    throws ExecutionException {

    return manager.startRemoteProcess(project, (PyRemoteSdkData)sdk.getSdkAdditionalData(), commandLine,
                                      settings);
  }
}
