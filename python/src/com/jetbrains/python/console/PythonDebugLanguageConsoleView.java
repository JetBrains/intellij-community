// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.actions.ShowCommandQueueAction;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class PythonDebugLanguageConsoleView extends DuplexConsoleView<ConsoleView, PythonConsoleView> implements PyCodeExecutor {

  public static final String DEBUG_CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))";
  private boolean myDebugConsoleInitialized = false;
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  /**
   * @param testMode this console will be used to display test output and should support TC messages
   */
  public PythonDebugLanguageConsoleView(final Project project, Sdk sdk, ConsoleView consoleView, final boolean testMode) {
    super(consoleView, new PythonConsoleView(project, PyBundle.message("python.console"), sdk, testMode));

    if (consoleView instanceof ConsoleViewImpl) {
      var console = this.getPydevConsoleView();
      var action = new ShowCommandQueueAction(console);
      ((ConsoleViewImpl)consoleView).addCustomConsoleAction(action);
    }

    enableConsole(!PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault());

    getSwitchConsoleActionPresentation().setIcon(PythonIcons.Python.PythonConsole);
    getSwitchConsoleActionPresentation().setText(PyBundle.messagePointer("run.configuration.show.command.line.action.name"));

    List<AnAction> actions = List.of(PyConsoleUtil.createTabCompletionAction(getPydevConsoleView()),
                                     PyConsoleUtil.createInterruptAction(getPydevConsoleView()));
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
    }
    else {
      IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> getPydevConsoleView().requestFocus());
    }
  }

  public @NotNull PythonConsoleView getPydevConsoleView() {
    return getSecondaryConsoleView();
  }

  public @Nullable ConsoleViewImpl getTextConsole() {
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
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    Key<?> outputType;
    if (contentType.equals(ConsoleViewContentType.ERROR_OUTPUT)) {
      outputType = ProcessOutputTypes.STDERR;
    }
    else {
      outputType = ProcessOutputTypes.STDOUT;
    }

    myAnsiEscapeDecoder.escapeText(text, outputType, (chunk, attributes) -> {
      ConsoleViewContentType type = getPydevConsoleView().outputTypeForAttributes(attributes);
      getPrimaryConsoleView().print(chunk, type);
      getPydevConsoleView().print(chunk, type);
    });
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

  @Override
  public JComponent getPreferredFocusableComponent() {
    var console = getPydevConsoleView();
    if (console.isVisible()) {
      return console.getConsoleEditor().getContentComponent();
    }
    return this;
  }
}
