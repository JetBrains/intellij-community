// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PythonDebugLanguageConsoleView extends DuplexConsoleView<ConsoleView, PythonConsoleView> implements PyCodeExecutor {

  public static final String DEBUG_CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))";
  private boolean myDebugConsoleInitialized = false;

  /**
   * @param testMode this console will be used to display test output and should support TC messages
   */
  public PythonDebugLanguageConsoleView(final Project project, Sdk sdk, ConsoleView consoleView, final boolean testMode) {
    super(consoleView, new PythonConsoleView(project, PyBundle.message("python.console"), sdk, testMode));

    enableConsole(!PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault());

    getSwitchConsoleActionPresentation().setIcon(PythonIcons.Python.PythonConsole);
    getSwitchConsoleActionPresentation().setText(PyBundle.messagePointer("run.configuration.show.command.line.action.name"));

    List<AnAction> actions = ContainerUtil.newArrayList(PyConsoleUtil.createTabCompletionAction(getPydevConsoleView()));
    actions.add(PyConsoleUtil.createInterruptAction(getPydevConsoleView()));
    AbstractConsoleRunnerWithHistory.registerActionShortcuts(actions, getPydevConsoleView().getEditor().getComponent());
    boolean isUseSoftWraps = EditorSettingsExternalizable.getInstance().isUseSoftWraps(SoftWrapAppliancePlaces.CONSOLE);
    getPydevConsoleView().getEditor().getSettings().setUseSoftWraps(isUseSoftWraps);
  }

  public PythonDebugLanguageConsoleView(final Project project, Sdk sdk) {
    this(project, sdk, TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole(), false);
  }

  @Override
  public void executeCode(@Nullable String code, @Nullable Editor e) {
    enableConsole(false);
    if (code != null) {
      getPydevConsoleView().executeInConsole(code);
    } else {
      IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> getPydevConsoleView().requestFocus());
    }
  }

  @NotNull
  public PythonConsoleView getPydevConsoleView() {
    return getSecondaryConsoleView();
  }

  @Nullable
  public ConsoleViewImpl getTextConsole() {
    ConsoleView consoleView = getPrimaryConsoleView();
    if (consoleView instanceof ConsoleViewImpl) {
      return (ConsoleViewImpl)consoleView;
    }
    return null;
  }

  public void showStartMessageForFirstExecution(String startCommand, PythonConsoleView console) {
    console.setPrompt("");
    console.executeStatement(startCommand + "\n", ProcessOutputTypes.SYSTEM);
  }

  @Override
  public void enableConsole(boolean primary) {
    super.enableConsole(primary);

    if (!primary && !isPrimaryConsoleEnabled()) {
      PythonConsoleView console = getPydevConsoleView();
      if (!myDebugConsoleInitialized && console.getExecuteActionHandler() != null) {
        if (!console.getExecuteActionHandler().getConsoleCommunication().isWaitingForInput()) {
          showStartMessageForFirstExecution(DEBUG_CONSOLE_START_COMMAND, console);
        }
        myDebugConsoleInitialized = true;
        console.initialized();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> console.requestFocus());
      }
    }
  }

  public void initialized() {
    myDebugConsoleInitialized = true;
  }
}
