package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDebuggerEditorsProvider extends XDebuggerEditorsProvider {

  @NotNull
  @Override
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @NotNull
  @Override
  public Document createDocument(@NotNull Project project, @NotNull String text, @Nullable XSourcePosition sourcePosition) {
    final PyExpressionCodeFragmentImpl fragment = new PyExpressionCodeFragmentImpl(project, "fragment.py", text, true);

    /*
    final PsiElement element = getContextElement(project, sourcePosition);
    System.out.println("element:" + element);
    fragment.setContext(element);
    */

    // todo: bind to context
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  /*
  @Nullable
  private static PsiElement getContextElement(Project project, XSourcePosition sourcePosition) {
    if (sourcePosition != null) {
      final Document document = FileDocumentManager.getInstance().getDocument(sourcePosition.getFile());
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        int offset = sourcePosition.getOffset();
        final int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
        do {
          PsiElement element = psiFile.findElementAt(offset);
          if (element != null && !(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
            PsiElement e = PsiTreeUtil.getParentOfType(element, PyElement.class);
            while (e != null) {
              if (e instanceof PyPrintStatement) {
                return e;
              }
              e = e.getParent();
            }
            //return RControlFlowBuilder.getControlFlowNodeElement(element);
          }
          offset = element.getTextRange().getEndOffset() + 1;
        }
        while (offset < lineEndOffset);
      }
    }

    return null;
  }
  */

}
