// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.testing.PythonUnitTestDetectorsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public abstract class PyBaseDocstringInspection extends PyInspection {
  @Override
  public abstract @NotNull Visitor buildVisitor(@NotNull ProblemsHolder holder,
                                                boolean isOnTheFly,
                                                @NotNull LocalInspectionToolSession session);

  protected abstract static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public final void visitPyFile(@NotNull PyFile node) {
      checkDocString(node);
    }

    @Override
    public final void visitPyFunction(@NotNull PyFunction node) {
      if (PythonUnitTestDetectorsKt.isTestFunction(node)) return;
      final Property property = node.getProperty();
      if (property != null && (node == property.getSetter().valueOrNull() || node == property.getDeleter().valueOrNull())) {
        return;
      }
      final String name = node.getName();
      if (name != null && !name.startsWith("_")) checkDocString(node);
    }

    @Override
    public final void visitPyClass(@NotNull PyClass node) {
      if (PythonUnitTestDetectorsKt.isTestClass(node, myTypeEvalContext)) return;
      final String name = node.getName();
      if (name == null || name.startsWith("_")) {
        return;
      }
      checkDocString(node);
    }

    protected abstract void checkDocString(@NotNull PyDocStringOwner node);
  }
}
