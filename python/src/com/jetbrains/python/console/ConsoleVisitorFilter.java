// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.PythonVisitorFilter;
import com.jetbrains.python.validation.PyDocStringHighlightingAnnotator;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 *
 * filter out some python inspections and annotations if we're in console
 */
public final class ConsoleVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(final @NotNull Class visitorClass, final @NotNull PsiFile file) {
    //if we're in console
    if (PydevConsoleRunnerUtil.isInPydevConsole(file)) {
      //inspections
      if (visitorClass == PyUnusedLocalInspection.class || visitorClass == PyUnboundLocalVariableInspection.class ||
          visitorClass == PyStatementEffectInspection.class || visitorClass == PySingleQuotedDocstringInspection.class ||
          visitorClass == PyIncorrectDocstringInspection.class || visitorClass == PyMissingOrEmptyDocstringInspection.class ||
          visitorClass == PyMandatoryEncodingInspection.class || visitorClass == PyPep8Inspection.class ||
          visitorClass == PyCompatibilityInspection.class) {
        return false;
      }

      //annotators
      if (visitorClass == PyDocStringHighlightingAnnotator.class) {
        return false;
      }
    }
    return true;
  }
}
