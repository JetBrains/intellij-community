package com.jetbrains.python.console;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.validation.DocStringAnnotator;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 *
 * filter out some python inspections and annotations if we're in console
 */
public class ConsoleVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull Class visitorClass, @NotNull PsiElement element, PsiFile containingFile) {
    //if we're in console
    if (element instanceof PydevConsoleElement || containingFile.getCopyableUserData(PydevConsoleRunner.CONSOLE_KEY) != null) {
      //inspections
      if (visitorClass == PyUnusedLocalInspectionVisitor.class || visitorClass == PyUnboundLocalVariableInspection.Visitor.class ||
          visitorClass == PyStatementEffectInspection.Visitor.class || visitorClass == PySingleQuotedDocstringInspection.Visitor.class ||
          visitorClass == PyDocstringInspection.Visitor.class) {
        return false;
      }

      //annotators
      if (visitorClass == DocStringAnnotator.class)
        return false;
    }
    return true;
  }
}
