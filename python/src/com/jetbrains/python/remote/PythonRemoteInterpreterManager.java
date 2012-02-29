package com.jetbrains.python.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.debugger.remote.PyPathMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public abstract class PythonRemoteInterpreterManager {
  public final static ExtensionPointName<PythonRemoteInterpreterManager> EP_NAME =
    ExtensionPointName.create("Pythonid.remoteInterpreterManager");

  public abstract ProcessHandler startRemoteProcess(Project project,
                                                    PythonRemoteSdkAdditionalData data,
                                                    GeneralCommandLine commandLine,
                                                    @Nullable
                                                    PyPathMappingSettings mappingSettings)
    throws PyRemoteInterpreterException;

  @Nullable
  public abstract Sdk addRemoteSdk(Project project);

  public abstract ProcessOutput runRemoteProcess(@Nullable Project project, PythonRemoteSdkAdditionalData data, String[] command)
    throws PyRemoteInterpreterException;

  @NotNull
  public abstract PyRemoteSshProcess createRemoteProcess(Project project,
                                              PythonRemoteSdkAdditionalData data,
                                              GeneralCommandLine commandLine) throws PyRemoteInterpreterException;

  public abstract boolean testConnection(final Project project, final PythonRemoteSdkAdditionalData data,
                                         final String title) throws PyRemoteInterpreterException;

  public abstract boolean editSdk(@NotNull Project project, @NotNull SdkModificator sdkModificator);

  @Nullable
  public static PythonRemoteInterpreterManager getInstance() {
    if (EP_NAME.getExtensions().length > 0) {
      return EP_NAME.getExtensions()[0];
    }
    else {
      return null;
    }
  }

  public static void addUnbuffered(ParamsGroup exeGroup) {
    for (String param : exeGroup.getParametersList().getParameters()) {
      if ("-u".equals(param)) {
        return;
      }
    }
    exeGroup.addParameter("-u");
  }

  public static String toSystemDependent(String path, boolean isWin) {
    char separator = isWin ? '\\' : '/';
    return FileUtil.toSystemIndependentName(path).replace('/', separator);
  }

  public static class PyRemoteInterpreterExecutionException extends ExecutionException {

    public PyRemoteInterpreterExecutionException() {
      super("Can't run remote python interpreter. WebDeployment plugin is disabled.");
    }
  }
}

