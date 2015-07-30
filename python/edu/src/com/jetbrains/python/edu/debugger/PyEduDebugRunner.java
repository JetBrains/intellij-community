package com.jetbrains.python.edu.debugger;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.Predicate;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.edu.PyEduUtils;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.ServerSocket;
import java.util.List;

public class PyEduDebugRunner extends PyDebugRunner {

  public static final Predicate<PsiElement> IS_NOTHING = new Predicate<PsiElement>() {
    @Override
    public boolean apply(@Nullable PsiElement input) {
      return (input instanceof PsiComment) ||
             (input instanceof PyImportStatement) ||
             (input instanceof PsiWhiteSpace) ||
             (isDocString(input));
    }
  };
  public static final String OUTPUT = "Output";

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

    return new PyEduDebugProcess(session, serverSocket, result.getExecutionConsole(), result.getProcessHandler(),
                                 pyState.isMultiprocessDebug());
  }

  @Override
  protected void initDebugProcess(String name, PyDebugProcess pyDebugProcess) {
    VirtualFile file = VfsUtil.findFileByIoFile(new File(name), true);
    assert file != null;

    final Project project = pyDebugProcess.getProject();
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    assert psiFile != null;

    List<PsiElement> psiElements = CollectHighlightsUtil.getElementsInRange(psiFile, 0, psiFile.getTextLength());
    for (PsiElement element : psiElements) {
      if (PyEduUtils.isFirstCodeLine(element, IS_NOTHING)) {
        int offset = element.getTextRange().getStartOffset();
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assert document != null;
        int line = document.getLineNumber(offset) + 1;
        PySourcePosition sourcePosition = pyDebugProcess.getPositionConverter().create(file.getPath(), line);
        XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        PyLineBreakpointType type = new PyLineBreakpointType();
        XBreakpointProperties properties = type.createBreakpointProperties(file, line);
        LineBreakpointState<XBreakpointProperties>
          breakpointState =
          new LineBreakpointState<XBreakpointProperties>(true, type.getId(), file.getUrl(), line, false, file.getTimeStamp());
        pyDebugProcess.addBreakpoint(sourcePosition, new XLineBreakpointImpl<XBreakpointProperties>(type,
                                                                                                    ((XBreakpointManagerImpl)breakpointManager),
                                                                                                    properties, breakpointState));
      }
    }
  }


  @Override
  protected void initSession(XDebugSession session, RunProfileState state, Executor executor) {
    XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
    if (tab != null) {
      RunnerLayoutUi ui = tab.getUi();
      ContentManager contentManager = ui.getContentManager();
      Content content = findContent(contentManager, "Watches");
      if (content != null) {
        contentManager.removeContent(content, true);
      }
      content = findContent(contentManager, "Console");
      if (content != null) {
        contentManager.removeContent(content, true);
      }
      initEduConsole(session, ui);
    }
  }

  private static void initEduConsole(@NotNull final XDebugSession session,
                                     @NotNull final RunnerLayoutUi ui) {
    Project project = session.getProject();
    final Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
    final PythonDebugLanguageConsoleView view = new PythonDebugLanguageConsoleView(project, sdk);
    final ProcessHandler processHandler = session.getDebugProcess().getProcessHandler();

    view.attachToProcess(processHandler);
    view.addMessageFilter(new PythonTracebackFilter(project));
    view.addMessageFilter(new UrlFilter());

    switchToPythonConsole(view);

    Content eduConsole =
      ui.createContent(OUTPUT, view.getComponent() , OUTPUT, AllIcons.Debugger.ToolConsole, view.getPreferredFocusableComponent());
    eduConsole.setCloseable(false);
    ui.addContent(eduConsole, 0, PlaceInGrid.right, false);

    PyDebugProcess process = (PyDebugProcess)session.getDebugProcess();
    PyDebugRunner.initDebugConsoleView(project, process, view, processHandler, session);
  }

  private static void switchToPythonConsole(PythonDebugLanguageConsoleView view) {
    AnAction[] actions = view.createConsoleActions();
    for (AnAction action : actions) {
      Presentation presentation = action.getTemplatePresentation();
      String text = presentation.getText();
      if (PyBundle.message("run.configuration.show.command.line.action.name").equals(text)) {
        AnActionEvent event =
          AnActionEvent.createFromAnAction(action, null,
                                           ActionPlaces.DEBUGGER_TOOLBAR, DataManager.getInstance().getDataContext(view));
        action.actionPerformed(event);
      }
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


  private static boolean isDocString(PsiElement element) {
    if (element instanceof PyExpressionStatement) {
      element = ((PyExpressionStatement)element).getExpression();
    }
    if (element instanceof PyExpression) {
      return DocStringUtil.isDocStringExpression((PyExpression)element);
    }
    return false;
  }

  private static class PyEduDebugProcess extends PyDebugProcess {
    public PyEduDebugProcess(@NotNull XDebugSession session,
                             @NotNull ServerSocket serverSocket,
                             @NotNull ExecutionConsole executionConsole,
                             @Nullable ProcessHandler processHandler, boolean multiProcess) {
      super(session, serverSocket, executionConsole, processHandler, multiProcess);
    }

    @Override
    public PyStackFrame createStackFrame(PyStackFrameInfo frameInfo) {
      return new PyEduStackFrame(getSession().getProject(), this, frameInfo,
                                 getPositionConverter().convertFromPython(frameInfo.getPosition()));
    }
  }
}