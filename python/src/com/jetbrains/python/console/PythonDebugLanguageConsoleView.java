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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.console.actions.ShowCommandQueueAction;
import com.jetbrains.python.icons.PythonIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;

@ApiStatus.Internal
public class PythonDebugLanguageConsoleView extends DuplexConsoleView<ConsoleView, PythonConsoleView> implements PyCodeExecutor {

  /** The value {@link PyDebuggerOptionsProvider#getDebugConsoleStartScript} starts from. */
  public static final String DEBUG_CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))";

  /**
   * The script the Debug Console runs on its first execution.
   *
   * @return the configured script, or an empty string when the user cleared it
   */
  @ApiStatus.Internal
  public static @NotNull String getStartScript(@NotNull Project project) {
    return PyDebuggerOptionsProvider.getInstance(project).getDebugConsoleStartScript();
  }
  private boolean myDebugConsoleInitialized = false;
  private boolean myStartScriptExecuted = false;
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

    getPydevConsoleView().markAsDebugConsole();

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
    console.executeStatementWithHighlighting(startCommand + "\n");
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
    ApplicationManager.getApplication().invokeLater(() -> {
      super.enableConsole(primary);

      if (!primary && !isPrimaryConsoleEnabled()) {
        PythonConsoleView console = getPydevConsoleView();
        initDebugConsole();
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> console.requestFocus());
      }
    });
  }

  public void initialized() {
    myDebugConsoleInitialized = true;
  }

  /**
   * Marks the Debug Console ready, whether or not it is the visible one.
   * <p>
   * {@link PythonConsoleView#initialized()} resolves a callback that queued work waits on, among it
   * {@code setConsoleEnabled} and {@code executeCode}. Readiness therefore must not depend on
   * "Always show Debug Console": that setting picks the visible console, not a working one. See PY-91913.
   */
  public void initDebugConsole() {
    PythonConsoleView console = getPydevConsoleView();
    if (myDebugConsoleInitialized || console.getExecuteActionHandler() == null) return;
    myDebugConsoleInitialized = true;
    console.initialized();
  }

  /**
   * Runs the Debug Console start script, at most once per session.
   * <p>
   * Call it only while the debugged process is paused. The script goes through the same channel as user input,
   * and that channel needs a stack frame: {@code PyDebugProcess.consoleExec} reads {@code currentFrame()} and
   * fails without one. The script used to be attempted from {@link #enableConsole}, which runs at session start
   * while the process is still running, so it never executed. See PY-91913.
   */
  public void executeStartScriptIfNeeded() {
    // Called from XDebugSessionListener.sessionPaused, which pydevd dispatches on its reader thread. Sending the
    // script reaches PSI through PydevConsoleExecuteActionHandler.checkSingleLine, so it needs the EDT and its
    // write-intent read action, the same context enableConsole() runs in.
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myStartScriptExecuted) return;
      PythonConsoleView console = getPydevConsoleView();
      PythonConsoleExecuteActionHandler handler = console.getExecuteActionHandler();
      if (handler == null || handler.getConsoleCommunication().isWaitingForInput()) return;

      myStartScriptExecuted = true;
      String script = getStartScript(console.getProject());
      if (script.isBlank()) return;
      showStartMessageForFirstExecution(script, console);
    });
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
