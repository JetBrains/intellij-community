// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.PyArgumentListInspection;
import com.jetbrains.python.inspections.PyByteLiteralInspection;
import com.jetbrains.python.inspections.PyClassHasNoInitInspection;
import com.jetbrains.python.inspections.PyDocstringTypesInspection;
import com.jetbrains.python.inspections.PyIncorrectDocstringInspection;
import com.jetbrains.python.inspections.PyMandatoryEncodingInspection;
import com.jetbrains.python.inspections.PyMissingOrEmptyDocstringInspection;
import com.jetbrains.python.inspections.PyNonAsciiCharInspection;
import com.jetbrains.python.inspections.PyPackageRequirementsInspection;
import com.jetbrains.python.inspections.PyPep8Inspection;
import com.jetbrains.python.inspections.PySingleQuotedDocstringInspection;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.inspections.PyUnboundLocalVariableInspection;
import com.jetbrains.python.inspections.PyUnnecessaryBackslashInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PythonVisitorFilter;
import com.jetbrains.python.validation.PyDocStringHighlightingAnnotator;
import com.jetbrains.python.validation.PyFunctionHighlightingAnnotator;
import com.jetbrains.python.validation.PyParameterListAnnotatorVisitor;
import com.jetbrains.python.validation.PyReturnYieldAnnotatorVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 * <p>
 * filter out some python inspections and annotations if we're in docstring substitution
 */
public final class PyDocstringVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(final @NotNull Class visitorClass, final @NotNull PsiFile file) {
    //inspections
    if (visitorClass == PyArgumentListInspection.class) {
      return false;
    }
    if (visitorClass == PyIncorrectDocstringInspection.class || visitorClass == PyMissingOrEmptyDocstringInspection.class ||
        visitorClass == PyUnboundLocalVariableInspection.class || visitorClass == PyUnnecessaryBackslashInspection.class ||
        visitorClass == PyByteLiteralInspection.class || visitorClass == PyNonAsciiCharInspection.class ||
        visitorClass == PyPackageRequirementsInspection.class || visitorClass == PyMandatoryEncodingInspection.class ||
        visitorClass == PyDocstringTypesInspection.class ||
        visitorClass == PySingleQuotedDocstringInspection.class || visitorClass == PyClassHasNoInitInspection.class ||
        visitorClass == PyStatementEffectInspection.class || visitorClass == PyPep8Inspection.class) {
      return false;
    }
    //annotators
    if (visitorClass == PyDocStringHighlightingAnnotator.class ||
        visitorClass == PyParameterListAnnotatorVisitor.class ||
        visitorClass == PyReturnYieldAnnotatorVisitor.class ||
        visitorClass == PyFunctionHighlightingAnnotator.class) {
      return false;
    }
    // doctest in separate file
    final PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    if (visitorClass == PyUnresolvedReferencesInspection.class && !(topLevelFile instanceof PyFile)) {
      return false;
    }
    return true;
  }
}
