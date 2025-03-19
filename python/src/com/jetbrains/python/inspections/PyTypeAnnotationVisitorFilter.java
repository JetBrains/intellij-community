// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PythonVisitorFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public final class PyTypeAnnotationVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull Class visitorClass, @NotNull PsiFile file) {
    return !(visitorClass == PyIncorrectDocstringInspection.class ||
             visitorClass == PyMissingOrEmptyDocstringInspection.class ||
             visitorClass == PySingleQuotedDocstringInspection.class ||
             visitorClass == PyByteLiteralInspection.class ||
             visitorClass == PyMandatoryEncodingInspection.class ||
             visitorClass == PyNonAsciiCharInspection.class ||
             visitorClass == PyInterpreterInspection.class ||
             visitorClass == PyPep8Inspection.class ||
             visitorClass == PyCompatibilityInspection.class ||
             visitorClass == PyPackageRequirementsInspection.class);
  }
}
