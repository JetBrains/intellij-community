package com.jetbrains.python.edu;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.impl.breakpoints.LineBreakpointState;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyLineBreakpointType;
import com.jetbrains.python.debugger.PySourcePosition;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class PyEduDebugRunner extends PyDebugRunner {

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(PyEduDebugExecutor.ID);
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
      if (PyEduUtils.isFirstCodeLine(element)) {
        int offset = element.getTextRange().getStartOffset();
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assert document != null;
        int line = document.getLineNumber(offset) + 1;
        PySourcePosition sourcePosition = pyDebugProcess.getPositionConverter().create(file.getPath(), line);
        XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        PyLineBreakpointType type = new PyLineBreakpointType();
        XBreakpointProperties properties = type.createBreakpointProperties(file, line);
        LineBreakpointState<XBreakpointProperties>
          breakpointState = new LineBreakpointState<XBreakpointProperties>(true, type.getId(), file.getUrl(), line, false, file.getTimeStamp());
        pyDebugProcess.addBreakpoint(sourcePosition, new XLineBreakpointImpl<XBreakpointProperties>(type,
                                                                             ((XBreakpointManagerImpl)breakpointManager),
                                                                             properties, breakpointState));
      }
    }
  }
}