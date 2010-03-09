package com.jetbrains.python.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.PairProcessor;
import com.jetbrains.django.run.ExecutionHelper;
import com.jetbrains.django.run.Runner;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author oleg
 */
public class PyConsoleRunner {
  private final Project myProject;
  private final String myConsoleTitle;

  private OSProcessHandler myProcessHandler;
  private final CommandLineArgumentsProvider myProvider;
  private final String myWorkingDir;

  private LanguageConsoleViewImpl myConsoleView;
  private final ConsoleHistoryModel myHistory = new ConsoleHistoryModel();

  private PyConsoleRunner(@NotNull final Project project,
                          @NotNull final String consoleTitle,
                          @NotNull final CommandLineArgumentsProvider provider,
                          @Nullable final String workingDir) throws ExecutionException {
    myProject = project;
    myConsoleTitle = consoleTitle;
    myProvider = provider;
    myWorkingDir = workingDir;
  }

  public static void run(@NotNull final Project project,
                         @NotNull final String consoleTitle,
                         @NotNull final CommandLineArgumentsProvider provider,
                         @Nullable final String workingDir) {
    try {
      final PyConsoleRunner consoleRunner = createRunner(project, consoleTitle, provider, workingDir);
      initAndRun(consoleRunner);
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  @NotNull
  public static PyConsoleRunner createRunner(@NotNull final Project project,
                                             @NotNull final String consoleTitle,
                                             @NotNull final CommandLineArgumentsProvider provider,
                                             @Nullable final String workingDir) throws ExecutionException {
    return new PyConsoleRunner(project, consoleTitle, provider, workingDir);
  }

  public static void initAndRun(@NotNull final PyConsoleRunner runner) throws ExecutionException {
    runner.init();
    runner.doRun();
  }

  private void init() throws ExecutionException {
// add holder created
    final Process process = Runner.createProcess(myWorkingDir, true, myProvider.getAdditionalEnvs(), myProvider.getArguments());

    final Charset outputEncoding = EncodingManager.getInstance().getDefaultCharset();
    myProcessHandler = new PyConsoleProcessHandler(this, process, getProviderCommandLine(), outputEncoding);

    ProcessTerminatedListener.attach(myProcessHandler);

    // Init console view
    myConsoleView = new LanguageConsoleViewImpl(myProject, "title", PythonLanguage.getInstance());
    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myConsoleView.setEditorEnabled(false);
          }
        });
      }
    });

// Setup default prompt
    myConsoleView.getConsole().setPrompt(">>>");

// Attach to process
    myConsoleView.attachToProcess(myProcessHandler);

// Add filter
    myConsoleView.addMessageFilter(new OutputConsoleFilter());

// Runner creating
    final Executor defaultExecutor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

// Runner creating
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    panel.add(myConsoleView.getComponent(), BorderLayout.CENTER);

    final RunContentDescriptor myDescriptor =
      new RunContentDescriptor(myConsoleView, myProcessHandler, panel, myConsoleTitle);

// tool bar actions
    final AnAction[] actions = fillToolBarActions(toolbarActions, defaultExecutor, myDescriptor);
    registerActionShortcuts(actions, getLanguageConsole().getConsoleEditor().getComponent());
    registerActionShortcuts(actions, panel);
    panel.updateUI();

// Show in run toolwindow
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);

// Request focus
    IdeFocusManager.getInstance(myProject).requestFocus(myConsoleView.getPreferredFocusableComponent(), true);
  }

  private void registerActionShortcuts(final AnAction[] actions, final JComponent component) {
    for (AnAction action : actions) {
      if (action.getShortcutSet() != null) {
        action.registerCustomShortcutSet(action.getShortcutSet(), component);
      }
    }
  }

  private AnAction[] fillToolBarActions(final DefaultActionGroup toolbarActions,
                                        final Executor defaultExecutor,
                                        final RunContentDescriptor myDescriptor) {
//stop
    final AnAction stopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
    toolbarActions.add(stopAction);

//close
    final CloseAction closeAction = new CloseAction(defaultExecutor, myDescriptor, myProject);
    toolbarActions.add(closeAction);

// run action
    final AnAction runAction = new DumbAwareAction(null, null, IconLoader.getIcon("/actions/execute.png")) {
      public void actionPerformed(final AnActionEvent e) {
        runExecuteActionInner(true);
      }

      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(getLanguageConsole().getEditorDocument().getTextLength() > 0);
      }
    };
    toolbarActions.add(runAction);

// history actions
    final PairProcessor<AnActionEvent, String> historyProcessor = new PairProcessor<AnActionEvent, String>() {
      public boolean process(final AnActionEvent e, final String s) {
        new WriteCommandAction(myProject, getLanguageConsole().getFile()) {
          protected void run(final Result result) throws Throwable {
            getLanguageConsole().getEditorDocument().setText(s == null? "" : s);
          }
        }.execute();
        return true;
      }
    };
    final AnAction historyNextAction = ConsoleHistoryModel.createHistoryAction(myHistory, true, historyProcessor);
    final AnAction historyPrevAction = ConsoleHistoryModel.createHistoryAction(myHistory, false, historyProcessor);
    historyNextAction.getTemplatePresentation().setVisible(false);
    historyPrevAction.getTemplatePresentation().setVisible(false);
    toolbarActions.add(historyNextAction);
    toolbarActions.add(historyPrevAction);

    return new AnAction[]{stopAction, closeAction, runAction, historyNextAction, historyPrevAction};
  }

  private void doRun() {
    myProcessHandler.startNotify();
  }

  private static void sendInput(final String input, final Charset charset, final OutputStream outputStream) {
    try {
      byte[] bytes = input.getBytes(charset.name());
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException e) {
      // ignore
    }
  }

  public LanguageConsoleImpl getLanguageConsole() {
    return myConsoleView.getConsole();
  }

  private void runExecuteActionInner(final boolean erase) {
    final Document document = getLanguageConsole().getCurrentEditor().getDocument();
    final String documentText = document.getText();
    final TextRange range = new TextRange(0, document.getTextLength());
    getLanguageConsole().getCurrentEditor().getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    getLanguageConsole().addCurrentToHistory(range, false);
    if (erase) {
      getLanguageConsole().setInputText("");
    }
    final String line = documentText.trim();
    myHistory.addToHistory(line);
    sendInput(line +"\n", myProcessHandler.getCharset(), myProcessHandler.getProcessInput());
  }

  private class OutputConsoleFilter implements Filter {
    public Result applyFilter(String line, int entireLength) {
      return new Result(entireLength - line.length(), entireLength, null, ColoredProcessHandler.getByKey(ConsoleHighlighter.OUT));
    }
  }

  protected String getProviderCommandLine() {
    final StringBuilder builder = new StringBuilder();
    for (String s : myProvider.getArguments()) {
      if (builder.length() > 0){
        builder.append(' ');
      }
      builder.append(s);
    }
    return builder.toString();
  }
}