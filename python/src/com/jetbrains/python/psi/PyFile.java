/*
 * Copyright (c) 2005, Your Corporation. All Rights Reserved.
 */
package com.jetbrains.python.psi;

import com.intellij.psi.PsiFile;

import java.util.List;

public interface PyFile extends PyElement, PsiFile {
  List<PyStatement> getStatements();

  List<PyClass> getTopLevelClasses();

  List<PyFunction> getTopLevelFunctions();

  List<PyTargetExpression> getTopLevelAttributes();
}
