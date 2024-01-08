// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.commandInterfaceConsole;

import com.intellij.execution.console.LanguageConsoleBuilder;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Consumer;
import com.intellij.commandInterface.commandLine.CommandLineLanguage;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.jetbrains.python.PythonPluginDisposable;
import com.jetbrains.python.psi.PyUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * <h1>Command line console</h1>
 * <p>
 * Console that allows user to type commands and execute them.
 * </p>
 * <h2>2 modes of console</h2>
 * <p>There are 2 types of consoles: First one is based on document: it simply allows user to type something there. It also has prompt.
 * Second one is connected to process, hence it bridges all 3 streams between process and console.
 * This console, how ever, should support both modes and switch between them. So, it supports 2 modes:
 * <dl>
 * <dt>Command-mode</dt>
 * <dd>Console accepts user-input, treats it as commands and allows to execute em with aid of {@link CommandModeConsumer}.
 * In this mode it also has prompt and {@link CommandLineLanguage}.
 * </dd>
 * <dt>Process-mode</dt>
 * <dd>Activated when {@link #attachToProcess(ProcessHandler)} is called.
 * Console hides prompt, disables language and connects itself to process with aid of {@link ProcessModeConsumer}.
 * When process terminates, console switches back to command-mode (restoring prompt, etc)</dd>
 * </dl>
 * </p>
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings("SerializableClassInSecureContext") // Nobody will serialize console
final class CommandConsole extends LanguageConsoleImpl implements Consumer<String>, Condition<LanguageConsoleView>, ConsoleWithProcess {
  /**
   * Width of border to create around console
   */
  static final int BORDER_SIZE_PX = 3;
  /**
   * List of commands, executor, filter and other stuff to be injected into {@link CommandLineFile}) if any.
   * See {@link CommandsInfo} doc
   */
  @Nullable
  private final CommandsInfo myCommandsInfo;
  @NotNull
  private final Module myModule;
  /**
   * {@link CommandModeConsumer} or {@link ProcessModeConsumer} to delegate execution to.
   * It also may be null if exection is not available.
   */
  private @Nullable Consumer<? super String> myCurrentConsumer;
  /**
   * One to sync action access to consumer field because it may be changed by callback when process is terminated
   */
  @NotNull
  private final Object myConsumerSemaphore = new Object();

  /**
   * Listener that will be notified when console state (mode?) changed.
   */
  @NotNull
  private final Collection<Runnable> myStateChangeListeners = new ArrayList<>();
  /**
   * Process handler currently running on console (if any)
   *
   * @see #switchToProcessMode(ProcessHandler)
   */
  @Nullable
  private volatile ProcessHandler myProcessHandler;

  /**
   * @param module       module console runs on
   * @param title        console title
   * @param commandsInfo See {@link CommandsInfo}
   */
  private CommandConsole(@NotNull final Module module,
                         @NotNull final String title,
                         @Nullable final CommandsInfo commandsInfo) {
    super(new Helper(module.getProject(), new LightVirtualFile(title, CommandLineLanguage.INSTANCE, "")) {
      @Override
      public void setupEditor(@NotNull EditorEx editor) {
        super.setupEditor(editor);
        // We do not need spaces here, because it leads to PY-15557
        EditorSettings editorSettings = editor.getSettings();
        editorSettings.setAdditionalLinesCount(0);
        editorSettings.setAdditionalColumnsCount(0);
      }
    });
    myCommandsInfo = commandsInfo;
    myModule = module;
  }

  @Override
  public void requestFocus() {
    super.getPreferredFocusableComponent().requestFocus();
  }

  @Override
  public void setInputText(@NotNull String query) {
    super.setInputText(query);
    getConsoleEditor().getCaretModel().moveToOffset(query.length());
  }

  @Override
  public void print(@NotNull String text, @NotNull final ConsoleViewContentType contentType) {
    if (myCommandsInfo != null) {
      final Function1<String, String> outputFilter = myCommandsInfo.getOutputFilter();
      if (outputFilter != null) {
        // Pass text through filter if is provided
        //noinspection AssignmentToMethodParameter
        text = outputFilter.invoke(text);
      }
    }
    super.print(text, contentType);
  }

  /**
   * @param module       module console runs on
   * @param title        console title
   * @param commandsInfo See {@link CommandsInfo}
   */
  @NotNull
  static CommandConsole createConsole(@NotNull final Module module,
                                      @Nls @NotNull final String title,
                                      @Nullable final CommandsInfo commandsInfo) {
    final CommandConsole console = new CommandConsole(module, title, commandsInfo);
    console.setEditable(true);
    LanguageConsoleBuilder.registerExecuteAction(console, console, title, title, console);

    console.switchToCommandMode();
    console.getComponent(); // For some reason console does not have component until this method is called which leads to some errros.
    console.getConsoleEditor().getSettings().setAdditionalLinesCount(2); // to prevent PY-15583
    Disposer.register(PythonPluginDisposable.getInstance(module.getProject()), console); // To dispose console when project disposes
    console.addMessageFilter(new UrlFilter());
    return console;
  }

  /**
   * Enables/disables left border {@link #BORDER_SIZE_PX} width for certain editors.
   *
   * @param editors editors to enable/disable border
   * @param enable  whether border should be enabled
   */
  private static void configureLeftBorder(final boolean enable, final EditorEx @NotNull ... editors) {
    for (final EditorEx editor : editors) {
      final Color backgroundColor = editor.getBackgroundColor(); // Border have the same color console background has
      final int thickness = enable ? BORDER_SIZE_PX : 0;
      final Border border = BorderFactory.createMatteBorder(0, thickness, 0, 0, backgroundColor);
      editor.getComponent().setBorder(border);
    }
  }

  @Override
  public void attachToProcess(final @NotNull ProcessHandler processHandler) {
    super.attachToProcess(processHandler);
    processHandler.addProcessListener(new MyProcessListener());
  }

  /**
   * Switches console to "command-mode" (see class doc for details)
   */
  private void switchToCommandMode() {
    // "upper" and "bottom" parts of console both need padding in command mode
    myProcessHandler = null;
    setPrompt(getTitle() + " > ");
    ApplicationManager.getApplication().invokeAndWait(() -> {
      notifyStateChangeListeners();
      configureLeftBorder(true, getConsoleEditor(), getHistoryViewer());
      setLanguage(CommandLineLanguage.INSTANCE);
      final CommandLineFile file = PyUtil.as(getFile(), CommandLineFile.class);
      resetConsumer(null);
      if (file == null || myCommandsInfo == null) {
        return;
      }
      file.setCommands(myCommandsInfo.getCommands());
      final CommandConsole console = this;
      resetConsumer(new CommandModeConsumer(myCommandsInfo.getCommands(), myModule, console, myCommandsInfo.getUnknownCommandsExecutor()));
    }, ModalityState.nonModal());
  }

  /**
   * Switches console to "process-mode" (see class doc for details)
   *
   * @param processHandler process to attach to
   */
  private void switchToProcessMode(@NotNull final ProcessHandler processHandler) {
    myProcessHandler = processHandler;
    ApplicationManager.getApplication().invokeAndWait(() -> {
      configureLeftBorder(false,
                          getConsoleEditor()); // "bottom" part of console do not need padding now because it is used for user inputA
      notifyStateChangeListeners();
      resetConsumer(new ProcessModeConsumer(processHandler));
      // In process mode we do not need prompt and highlighting
      setLanguage(PlainTextLanguage.INSTANCE);
      setPrompt("");
    }, ModalityState.nonModal());
  }

  /**
   * Notify listeners that state has been changed
   */
  private void notifyStateChangeListeners() {
    for (final Runnable listener : myStateChangeListeners) {
      listener.run();
    }
  }

  /**
   * @return process handler currently running on console (if any) or null if in {@link #switchToCommandMode() command mode}
   * @see #switchToProcessMode(ProcessHandler)
   */
  @Override
  @Nullable
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  /**
   * Chooses consumer to delegate execute action to.
   *
   * @param newConsumer new consumer to register to delegate execution to
   *                    or null if just reset consumer
   */
  private void resetConsumer(final @Nullable Consumer<? super String> newConsumer) {
    synchronized (myConsumerSemaphore) {
      myCurrentConsumer = newConsumer;
    }
  }

  @Override
  public boolean value(final LanguageConsoleView t) {
    // Is execution available?
    synchronized (myConsumerSemaphore) {
      return myCurrentConsumer != null;
    }
  }

  @Override
  public void consume(final String t) {
    // User requested execution (enter clicked)
    synchronized (myConsumerSemaphore) {
      if (myCurrentConsumer != null) {
        myCurrentConsumer.consume(t);
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return super.getActionUpdateThread();
  }

  /**
   * Adds listener that will be notified when console state (mode?) changed.
   * <strong>Called on EDT</strong>
   *
   * @param listener listener to notify
   */
  void addStateChangeListener(@NotNull final Runnable listener) {
    myStateChangeListeners.add(listener);
  }


  /**
   * Listens for process to switch between modes
   */
  private final class MyProcessListener extends ProcessAdapter {
    @Override
    public void processTerminated(@NotNull final ProcessEvent event) {
      super.processTerminated(event);
      switchToCommandMode();
    }

    @Override
    public void startNotified(@NotNull final ProcessEvent event) {
      super.startNotified(event);
      switchToProcessMode(event.getProcessHandler());
    }
  }

}
