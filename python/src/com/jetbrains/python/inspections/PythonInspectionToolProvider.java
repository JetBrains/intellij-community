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
      PyMethodOverridingInspection.class,
      PyTrailingSemicolonInspection.class,
      PyReturnFromInitInspection.class,
      PyUnusedLocalVariableInspection.class,
      PyUnsupportedFeaturesInspection.class,
      PyDeprecatedModulesInspection.class,
      PyDictCreationInspection.class,
      PyExceptClausesOrderInspection.class,
      PyTupleAssignmentBalanceInspection.class,
      PyClassicStyleClassInspection.class
    };
  }
}
