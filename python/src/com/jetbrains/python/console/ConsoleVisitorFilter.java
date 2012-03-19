package com.jetbrains.python.console;

import com.intellij.psi.PsiFile;
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
  public boolean isSupported(@NotNull final Class visitorClass, @NotNull final PsiFile file) {
    //if we're in console
    if (PydevConsoleRunner.isInPydevConsole(file)) {
      //inspections
      if (visitorClass == PyUnusedLocalInspection.class || visitorClass == PyUnboundLocalVariableInspection.class ||
          visitorClass == PyStatementEffectInspection.class || visitorClass == PySingleQuotedDocstringInspection.class ||
          visitorClass == PyDocstringInspection.class) {
        return false;
      }

      //annotators
      if (visitorClass == DocStringAnnotator.class) {
        return false;
      }
    }
    return true;
  }
}
