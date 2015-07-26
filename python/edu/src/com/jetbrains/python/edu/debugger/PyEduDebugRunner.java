package com.jetbrains.python.edu.debugger;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.Predicate;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.edu.PyEduUtils;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.run.PythonCommandLineState;
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

  private static boolean isDocString(PsiElement element) {
    if (element instanceof PyExpressionStatement) {
      element = ((PyExpressionStatement)element).getExpression();
    }
    if (element instanceof PyExpression) {
      return DocStringUtil.isDocStringExpression((PyExpression)element);
    }
    return false;
  }

  private class PyEduDebugProcess extends PyDebugProcess {
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