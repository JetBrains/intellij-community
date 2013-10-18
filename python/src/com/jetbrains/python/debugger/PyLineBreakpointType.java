package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyLineBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
  public static final String ID = "python-line";
  private static final String NAME = "Python Line Breakpoint";

  private final PyDebuggerEditorsProvider myEditorsProvider = new PyDebuggerEditorsProvider();

  public PyLineBreakpointType() {
    super(ID, NAME);
  }

  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      if (file.getFileType() == PythonFileType.INSTANCE) {
        XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
          public boolean process(PsiElement psiElement) {
            if (psiElement instanceof PsiWhiteSpace || psiElement instanceof PsiComment) return true;
            if (psiElement.getNode() != null && notStoppableElementType(psiElement.getNode().getElementType())) return true;

            // Python debugger seems to be able to stop on pretty much everything
            stoppable.set(true);
            return false;
          }
        });

        if (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
          stoppable.set(false);
        }
      }
    }

    return stoppable.get();
  }

  private static boolean notStoppableElementType(IElementType elementType) {
    return elementType == PyTokenTypes.TRIPLE_QUOTED_STRING ||
           elementType == PyTokenTypes.SINGLE_QUOTED_STRING ||
           elementType == PyTokenTypes.SINGLE_QUOTED_UNICODE ||
           elementType == PyTokenTypes.DOCSTRING
      ;
  }

  @Nullable
  public XBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
    return null;
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint, @NotNull Project project) {
    return myEditorsProvider;
  }
}
