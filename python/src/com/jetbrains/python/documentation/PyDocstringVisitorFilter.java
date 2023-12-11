// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.PyNonAsciiCharInspection;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PythonVisitorFilter;
import com.jetbrains.python.validation.DocStringAnnotator;
import com.jetbrains.python.validation.HighlightingAnnotator;
import com.jetbrains.python.validation.ParameterListAnnotator;
import com.jetbrains.python.validation.ReturnAnnotator;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 *
 * filter out some python inspections and annotations if we're in docstring substitution
 */
public final class PyDocstringVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull final Class visitorClass, @NotNull final PsiFile file) {
    //inspections
    if (visitorClass == PyArgumentListInspection.class) {
      return false;
    }
    if (visitorClass == PyIncorrectDocstringInspection.class || visitorClass == PyMissingOrEmptyDocstringInspection.class ||
        visitorClass == PyUnboundLocalVariableInspection.class || visitorClass == PyUnnecessaryBackslashInspection.class ||
        visitorClass == PyByteLiteralInspection.class || visitorClass == PyNonAsciiCharInspection.class ||
        visitorClass == PyPackageRequirementsInspection.class || visitorClass == PyMandatoryEncodingInspection.class ||
        visitorClass == PyInterpreterInspection.class || visitorClass == PyDocstringTypesInspection.class ||
        visitorClass == PySingleQuotedDocstringInspection.class || visitorClass == PyClassHasNoInitInspection.class || 
        visitorClass == PyStatementEffectInspection.class || visitorClass == PyPep8Inspection.class) {
      return false;
    }
    //annotators
    if (visitorClass == DocStringAnnotator.class || visitorClass == ParameterListAnnotator.class || visitorClass == ReturnAnnotator.class || visitorClass == HighlightingAnnotator.class)
      return false;
    // doctest in separate file
    final PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    if (visitorClass == PyUnresolvedReferencesInspection.class && !(topLevelFile instanceof PyFile)) {
      return false;
    }
    return true;
  }
}
