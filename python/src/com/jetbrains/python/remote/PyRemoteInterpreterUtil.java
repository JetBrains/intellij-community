package com.jetbrains.python.remote;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.remote.RemoteSdkException;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyExecutionException;
import com.jetbrains.python.run.PyRemoteProcessStarterManagerUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

public class PyRemoteInterpreterUtil {
  /**
   * @param nullForUnparsableVersion if version returns by python can't be parsed -- return null instead of exception
   * @return version or null if sdk does not have flavor / version can't be parsed etc
   */
  @Nullable
  public static String getInterpreterVersion(@Nullable final Project project,
                                             @NotNull final PyRemoteSdkAdditionalDataBase data,
                                             final boolean nullForUnparsableVersion)
    throws RemoteSdkException {
    final Ref<String> result = Ref.create(null);
    final Ref<RemoteSdkException> exception = Ref.create(null);

    final Task.Modal task = new Task.Modal(project, PyBundle.message("python.sdk.getting.remote.interpreter.version"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final PythonSdkFlavor flavor = data.getFlavor();
        if (flavor != null) {
          ProcessOutput processOutput;
          try {
            try {
              String[] command = {data.getInterpreterPath(), flavor.getVersionOption()};
              processOutput = PyRemoteProcessStarterManagerUtil.getManager(data).executeRemoteProcess(myProject, command, null,
                                                                                                      data, new PyRemotePathMapper());
              if (processOutput.getExitCode() == 0) {
                final String version = flavor.getVersionStringFromOutput(processOutput);
                if (version != null || nullForUnparsableVersion) {
                  result.set(version);
                  return;
                }
              }
              exception.set(createException(processOutput, command));
            }
            catch (Exception e) {
              throw RemoteSdkException.cantObtainRemoteCredentials(e);
            }
          }
          catch (RemoteSdkException e) {
            exception.set(e);
          }
        }
      }
    };

    if (!ProgressManager.getInstance().hasProgressIndicator()) {

      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> ProgressManager.getInstance().run(task));
    }
    else {
      task.run(ProgressManager.getInstance().getProgressIndicator());
    }

    if (!exception.isNull()) {
      throw exception.get();
    }

    return result.get();
  }

  @NotNull
  private static RemoteSdkException createException(@NotNull final ProcessOutput processOutput, String @NotNull [] command) {
    return RemoteSdkException.cantObtainRemoteCredentials(
      new PyExecutionException(PyBundle.message("python.sdk.can.t.obtain.python.version"),
                               command[0],
                               Lists.newArrayList(command),
                               processOutput));
  }

  public static void closeOnProcessTermination(@NotNull ProcessHandler processHandler, @NotNull Closeable closeable) {
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        // Nothing.
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        try {
          closeable.close();
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        // Nothing.
      }
    });
  }
}
