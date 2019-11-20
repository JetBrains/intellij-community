package com.jetbrains.python.inspections.unresolvedReference;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PyInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

abstract class PyUnresolvedReferencesInspectionBase extends PyInspection {
  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyPsiBundle.message("INSP.NAME.unresolved.refs");
  }
}
