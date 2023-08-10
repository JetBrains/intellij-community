// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filters out Python scripts for which it doesn't make sense to run the standard Python configuration,
 * and which are (possibly) run by other configurations instead.
 */
public interface RunnableScriptFilter {
  ExtensionPointName<RunnableScriptFilter> EP_NAME = ExtensionPointName.create("Pythonid.runnableScriptFilter");

  boolean isRunnableScript(PsiFile script, @NotNull Module module, Location location, @Nullable TypeEvalContext context);

  static boolean isIfNameMain(Location location) {
    PsiElement element = location.getPsiElement();
    while (true) {
      final PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
      if (ifStatement == null) {
        break;
      }
      element = ifStatement;
    }
    if (element instanceof PyIfStatement) {
      PyIfStatement ifStatement = (PyIfStatement)element;
      return PyUtil.isIfNameEqualsMain(ifStatement);
    }
    return false;
  }
}
