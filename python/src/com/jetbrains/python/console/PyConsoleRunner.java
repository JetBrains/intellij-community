package com.jetbrains.python.console;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.*;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairProcessor;
import com.jetbrains.django.run.Runner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author oleg
 */
public class PyConsoleRunner extends AbstractConsoleRunnerWithHistory {
  public PyConsoleRunner(@NotNull final Project project,
                         @NotNull final String consoleTitle,
                         @NotNull final CommandLineArgumentsProvider provider,
                         @Nullable final String workingDir) {
    super(project, consoleTitle, provider, workingDir);
  }

  public static void run(@NotNull final Project project,
                         @NotNull final String consoleTitle,
                         @NotNull final CommandLineArgumentsProvider provider,
                         @Nullable final String workingDir) {

    final PyConsoleRunner consoleRunner = new PyConsoleRunner(project, consoleTitle, provider, workingDir);
    try {
      consoleRunner.initAndRun();
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  protected LanguageConsoleViewImpl createConsoleView() {
    return new PyLanguageConsoleView(myProject, myConsoleTitle);
  }


  @Nullable
  protected Process createProcess() throws ExecutionException {
    return Runner.createProcess(myWorkingDir, true, myProvider.getAdditionalEnvs(), myProvider.getArguments());
  }

  protected PyConsoleProcessHandler createProcessHandler(final Process process) {
    final Charset outputEncoding = EncodingManager.getInstance().getDefaultCharset();
    return new PyConsoleProcessHandler(process, myConsoleView.getConsole(), getProviderCommandLine(myProvider), outputEncoding);
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
    final AnAction stopAction = createStopAction();
    toolbarActions.add(stopAction);

//close
    final AnAction closeAction = createCloseAction(defaultExecutor, myDescriptor);
    toolbarActions.add(closeAction);

// run action
    myRunAction = new DumbAwareAction(null, null, IconLoader.getIcon("/actions/execute.png")) {
      public void actionPerformed(final AnActionEvent e) {
        runExecuteActionInner(true);
      }

      public void update(final AnActionEvent e) {
        final EditorEx editor = getLanguageConsole().getConsoleEditor();
        final Lookup lookup = LookupManager.getActiveLookup(editor);
        e.getPresentation().setEnabled(!myProcessHandler.isProcessTerminated() &&
                                       (lookup == null || !lookup.isCompletion()));
      }
    };
    final ActionManager manager = ActionManager.getInstance();

      // TODO[oleg] fix when Maia compatibility doesn't care
    if (manager.getAction("Console.Execute") != null){
      EmptyAction.setupAction(myRunAction, "Console.Execute", null);
      KeymapManager.getInstance().getActiveKeymap().addShortcut("Console.Execute", new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));
    } else {
      EmptyAction.setupAction(myRunAction, "Python.Console.Execute", null);
      KeymapManager.getInstance().getActiveKeymap().addShortcut("Python.Console.Execute", new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));
    }

    toolbarActions.add(myRunAction);

// Help
    toolbarActions.add(CommonActionsManager.getInstance().createHelpAction("interactive_console"));

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
    // API compatibility with Maia
    final AnAction historyAction = manager.getAction("Console.History.Next");
    if (historyAction != null) {
      final AnAction historyNextAction = ConsoleHistoryModel.createHistoryAction(myHistory, true, historyProcessor);
      final AnAction historyPrevAction = ConsoleHistoryModel.createHistoryAction(myHistory, false, historyProcessor);
      historyNextAction.getTemplatePresentation().setVisible(false);
      historyPrevAction.getTemplatePresentation().setVisible(false);
      return new AnAction[]{stopAction, closeAction, myRunAction, historyNextAction, historyPrevAction};
    } else {
      // TODO[oleg]: remove me!!!
      final AnAction historyNextAction = createHistoryAction(myHistory, true, historyProcessor);
      manager.registerAction("Console.History.Next", historyNextAction);
      EmptyAction.setupAction(historyNextAction, "Console.History.Next", null);
      KeymapManager.getInstance().getActiveKeymap().addShortcut("Console.History.Next",
                                                                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.CTRL_MASK), null));

      final AnAction historyPrevAction = createHistoryAction(myHistory, false, historyProcessor);
      manager.registerAction("Console.History.Prev", historyPrevAction);
      EmptyAction.setupAction(historyPrevAction, "Console.History.Prev", null);
      KeymapManager.getInstance().getActiveKeymap().addShortcut("Console.History.Prev",
                                                                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.CTRL_MASK), null));

      historyNextAction.getTemplatePresentation().setVisible(false);
      historyPrevAction.getTemplatePresentation().setVisible(false);
      return new AnAction[]{stopAction, closeAction, myRunAction, historyNextAction, historyPrevAction};
    }
  }

   public static AnAction createHistoryAction(final ConsoleHistoryModel model, final boolean next, final PairProcessor<AnActionEvent,String> processor) {
     final AnAction action = new AnAction(null, null, null) {
       @Override
       public void actionPerformed(final AnActionEvent e) {
         processor.process(e, next ? model.getHistoryNext() : model.getHistoryPrev());
       }

       @Override
       public void update(final AnActionEvent e) {
         e.getPresentation().setEnabled(model.hasHistory(next));
       }
     };
     return action;
   }

  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    return new CloseAction(defaultExecutor, myDescriptor, myProject);
  }

  protected AnAction createStopAction() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
  }

  protected void sendInput(final String input) {
    super.sendInput(input);
    ((PyLanguageConsoleView)myConsoleView).inputSent(input);
  }
}
