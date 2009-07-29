package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Nov 14, 2008
 */
public class PythonInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] {
      PyArgumentListInspection.class,
      PyRedeclarationInspection.class,
      PyUnresolvedReferencesInspection.class,
      PyMethodParametersInspection.class,
      PyUnreachableCodeInspection.class,
      PyMethodFirstArgAssignmentInspection.class,
      PyStringFormatInspection.class,
      PyMethodOverridingInspection.class
    };
  }
}
