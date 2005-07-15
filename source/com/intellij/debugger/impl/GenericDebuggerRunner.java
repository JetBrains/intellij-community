package com.intellij.debugger.impl;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;

import javax.swing.*;

public class GenericDebuggerRunner implements JavaProgramRunner<GenericDebuggerRunnerSettings> {
  private static final Icon ICON = IconLoader.getIcon("/actions/startDebugger.png");
  private static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/general/toolWindowDebugger.png");

  public static final Icon RERUN_ICON = ICON;
  private static final RunnerInfo DEBUGGER_INFO = new RunnerInfo(ToolWindowId.DEBUG,
                                                                 "Debug selected configuration",
                                                                 ICON,
                                                                 TOOLWINDOW_ICON,
                                                                 ToolWindowId.DEBUG,
                                                                 "debugging") {
    public String getRunContextActionId() {
      return "DebugClass";
    }

    public String getStartActionText() {
      return "&Debug";
    }
  };

  public GenericDebuggerRunner() {
  }

  public static RunnerInfo getRunnerInfo() {
    return DEBUGGER_INFO;
  }

  public RunContentDescriptor doExecute(final RunProfileState state,
                                        final RunProfile runProfile,
                                        RunContentDescriptor reuseContent,
                                        final Project project) throws ExecutionException {
    final boolean addLvcsLabel = LvcsConfiguration.getInstance().ADD_LABEL_ON_RUNNING;
    final LocalVcs localVcs = LocalVcs.getInstance(project);
    RunContentDescriptor contentDescriptor = null;

    final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(project);
    if (state instanceof JavaCommandLine) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final JavaCommandLine javaCommandLine = (JavaCommandLine)state;
      if (addLvcsLabel) {
        localVcs.addLabel("Debugging " + runProfile.getName(), "");
      }
      RemoteConnection connection = DebuggerManagerImpl.createDebugParameters(javaCommandLine.getJavaParameters(), true, DebuggerSettings.getInstance().DEBUGGER_TRANSPORT, "", false);
      contentDescriptor = manager.attachVirtualMachine(runProfile, this, javaCommandLine, reuseContent, connection, true);
    }
    else if (state instanceof PatchedRunnableState) {
      FileDocumentManager.getInstance().saveAllDocuments();
      if (addLvcsLabel) {
        localVcs.addLabel("Debugging " + runProfile.getName(), "");
      }
      final RemoteConnection connection = doPatch(new JavaParameters(), state.getRunnerSettings());
      contentDescriptor = manager.attachVirtualMachine(runProfile, this, state, reuseContent, connection, true);
    }
    else if (state instanceof RemoteState) {
      FileDocumentManager.getInstance().saveAllDocuments();
      if (addLvcsLabel) {
        localVcs.addLabel("Starting remote debugging " + runProfile.getName(), "");
      }
      RemoteState remoteState = (RemoteState)state;
      final RemoteConnection connection = createRemoteDebugConnection(remoteState, state.getRunnerSettings());
      contentDescriptor = manager.attachVirtualMachine(runProfile, this, remoteState, reuseContent, connection, false);
    }

    return contentDescriptor != null ? contentDescriptor : null;
  }

  private RemoteConnection createRemoteDebugConnection(RemoteState connection, final RunnerSettings settings) {
    final RemoteConnection remoteConnection = connection.getRemoteConnection();

    GenericDebuggerRunnerSettings debuggerRunnerSettings = ((GenericDebuggerRunnerSettings)settings.getData());

    if (debuggerRunnerSettings != null) {
      remoteConnection.setUseSockets(debuggerRunnerSettings.getTransport() == DebuggerSettings.SOCKET_TRANSPORT);
      remoteConnection.setAddress(debuggerRunnerSettings.DEBUG_PORT);
    }

    return remoteConnection;
  }

  public RunnerInfo getInfo() {
    return DEBUGGER_INFO;
  }

  public GenericDebuggerRunnerSettings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return new GenericDebuggerRunnerSettings();
  }

  public void patch(JavaParameters javaParameters, RunnerSettings settings) throws ExecutionException {
    doPatch(javaParameters, settings);
  }

  private RemoteConnection doPatch(final JavaParameters javaParameters, final RunnerSettings settings) throws ExecutionException {
    final GenericDebuggerRunnerSettings debuggerSettings = ((GenericDebuggerRunnerSettings)settings.getData());
    return DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerSettings, false);
  }

  public AnAction[] createActions(ExecutionResult executionResult) {
    return new AnAction[0];
  }

  public SettingsEditor<GenericDebuggerRunnerSettings> getSettingsEditor(RunConfiguration configuration) {
    if (configuration instanceof RunConfigurationWithRunnerSettings) {
      if (((RunConfigurationWithRunnerSettings)configuration).isSettingsNeeded()) {
        return new GenericDebuggerParametersRunnerConfigurable(configuration.getProject());
      }
    }
    return null;
  }
}
