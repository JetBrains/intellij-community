package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDebuggerEvaluator extends XDebuggerEvaluator {

  private final PyDebugProcess myDebugProcess;

  public PyDebuggerEvaluator(@NotNull final PyDebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  @Override
  public void evaluate(@NotNull String expression, XEvaluationCallback callback) {
    // todo: parse expression and use either EVAL or EXEC (add parameter to evaluate)
    // todo: think on getting results from EXEC
    try {
      final PyDebugValue value = myDebugProcess.evaluate(expression);
      callback.evaluated(value);
    }
    catch (Exception e) {
      callback.errorOccurred("Unable to evaluate \"" + expression + "\": " + e.getMessage());
    }
  }

  @Override
  public TextRange getExpressionRangeAtOffset(Project project, Document document, int offset) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      PsiElement element = psiFile.findElementAt(offset);
      if (!(element instanceof PyExpression)) {
        element = PsiTreeUtil.getParentOfType(element, PyExpression.class);
      }
      if (element != null && isSimpleEnough(element)) {
        return element.getTextRange();
      }
    }
    return null;
  }

  private static boolean isSimpleEnough(@Nullable PsiElement element) {
    return element instanceof PyLiteralExpression || element instanceof PyQualifiedExpression ||
        element instanceof PyCallExpression || element instanceof PyBinaryExpression ||
        element instanceof PyPrefixExpression || element instanceof PySliceExpression;
  }

}
