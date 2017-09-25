package com.jetbrains.python.edu.debugger;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyLineBreakpointType;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.ServerSocket;

public class PyEduDebugRunner extends PyDebugRunner {
  private static final Logger LOG = Logger.getInstance(PyEduDebugRunner.class);
  public static final int NO_LINE = -1;

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(PyEduDebugExecutor.ID);
  }

  @NotNull
  @Override
  protected PyDebugProcess createDebugProcess(@NotNull XDebugSession session,
                                              ServerSocket serverSocket,
                                              ExecutionResult result,
                                              PythonCommandLineState pyState) {
    ExecutionConsole executionConsole = result.getExecutionConsole();
    ProcessHandler processHandler = result.getProcessHandler();
    boolean isMultiProcess = pyState.isMultiprocessDebug();
    String scriptName = getScriptName(pyState);
    if (scriptName != null) {
      VirtualFile file = VfsUtil.findFileByIoFile(new File(scriptName), true);
      if (file != null) {
        int line = getBreakpointLineNumber(file, session.getProject());
        if (line != NO_LINE) {
          EduUsagesCollector.stepThroughInvoked();
          return new PyEduDebugProcess(session, serverSocket,
                                       executionConsole, processHandler,
                                       isMultiProcess, scriptName, line + 1);
        }
      }
    }
    LOG.info("Failed to create PyEduDebugProcess. PyDebugProcess created instead.");
    return new PyDebugProcess(session, serverSocket, executionConsole,
                              processHandler, isMultiProcess);
  }

  @Nullable
  private static String getScriptName(PythonCommandLineState pyState) {
    ExecutionEnvironment environment = pyState.getEnvironment();
    if (environment == null) {
      return null;
    }
    RunProfile runProfile = environment.getRunProfile();
    if (runProfile instanceof PythonRunConfiguration) {
      String name = FileUtil.toSystemIndependentName(((PythonRunConfiguration)runProfile).getScriptName());
      return SystemInfo.isWindows ? name.toLowerCase() : name;
    }
    return null;
  }

  /**
   * @return the smallest line (from 0 to line number) suitable to set breakpoint on it, NO_LINE if there is no such line in the file
   */
  private static int getBreakpointLineNumber(@NotNull final VirtualFile file, @NotNull final Project project) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      return NO_LINE;
    }
    PyLineBreakpointType lineBreakpointType = new PyLineBreakpointType();
    for (int line = 0; line < document.getLineCount(); line++) {
      if (lineBreakpointType.canPutAt(file, line, project)) {
        return line;
      }
    }
    return NO_LINE;
  }


  @Override
  protected void initSession(XDebugSession session, RunProfileState state, Executor executor) {
    XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
    if (tab != null) {
      RunnerLayoutUi ui = tab.getUi();
      ContentManager contentManager = ui.getContentManager();
      Content content = findContent(contentManager, XDebuggerBundle.message("debugger.session.tab.console.content.name"));
      if (content != null) {
        ExecutionConsole console = session.getDebugProcess().createConsole();
        PythonDebugLanguageConsoleView view = (PythonDebugLanguageConsoleView)console;
        Presentation presentation = view.getSwitchConsoleActionPresentation();
        ToggleAction action = new ToggleAction(presentation.getText(), presentation.getDescription(), presentation.getIcon()) {

          @Override
          public boolean isSelected(AnActionEvent e) {
            return !view.isPrimaryConsoleEnabled();
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            view.enableConsole(!state);
          }
        };
        content.setActions(new DefaultActionGroup(action), ActionPlaces.DEBUGGER_TOOLBAR, view.getPreferredFocusableComponent());
      }
      patchLeftToolbar(session, ui);
    }
  }

  private static void patchLeftToolbar(@NotNull XDebugSession session, @NotNull RunnerLayoutUi ui) {
    DefaultActionGroup newLeftToolbar = new DefaultActionGroup();

    DefaultActionGroup firstGroup = new DefaultActionGroup();
    addActionToGroup(firstGroup, XDebuggerActions.RESUME);
    addActionToGroup(firstGroup, IdeActions.ACTION_STOP_PROGRAM);
    newLeftToolbar.addAll(firstGroup);

    newLeftToolbar.addSeparator();

    Executor executor = PyEduDebugExecutor.getInstance();
    newLeftToolbar.add(new CloseAction(executor, session.getRunContentDescriptor(), session.getProject()));
    //TODO: return proper helpID
    newLeftToolbar.add(new ContextHelpAction(executor.getHelpId()));

    ui.getOptions().setLeftToolbar(newLeftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
  }

  private static void addActionToGroup(DefaultActionGroup group, String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) {
      action.getTemplatePresentation().setEnabled(true);
      group.add(action, Constraints.LAST);
    }
  }

  @Nullable
  private static Content findContent(ContentManager manager, String name) {
    for (Content content : manager.getContents()) {
      if (content.getDisplayName().equals(name)) {
        return content;
      }
    }
    return null;
  }
}