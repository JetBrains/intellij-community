package com.jetbrains.python.console;

import com.intellij.psi.PsiElement;
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
  public boolean isSupported(@NotNull Class visitorClass, @NotNull PsiElement element) {
    //if we're in console
    if (PydevConsoleRunner.isInPydevConsole(element)) {
      //inspections
      if (visitorClass == PyUnusedLocalInspectionVisitor.class || visitorClass == PyUnboundLocalVariableInspection.Visitor.class ||
          visitorClass == PyStatementEffectInspection.class || visitorClass == PySingleQuotedDocstringInspection.class ||
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
