package com.jetbrains.python.remote;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public abstract class PythonRemoteInterpreterManager {
  public final static ExtensionPointName<PythonRemoteInterpreterManager> EP_NAME =
    ExtensionPointName.create("Pythonid.remoteInterpreterManager");

  public abstract ProcessHandler doCreateProcess(Project project, PythonRemoteSdkAdditionalData data, GeneralCommandLine commandLine)
    throws PyRemoteInterpreterException;

  @Nullable
  public abstract Sdk addRemoteSdk(Project project);

  public abstract ProcessOutput runRemoteProcess(@Nullable Project project, PythonRemoteSdkAdditionalData data, String[] command)
    throws PyRemoteInterpreterException;

  @Nullable
  public static PythonRemoteInterpreterManager getInstance() {
    if (EP_NAME.getExtensions().length > 0) {
      return EP_NAME.getExtensions()[0];
    } else {
      return null;
    }
  }
}

