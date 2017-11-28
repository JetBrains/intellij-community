/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.util.ThreeState;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseDocstringInspection extends PyInspection {
  @NotNull
  @Override
  public abstract Visitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session);

  protected static abstract class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public final void visitPyFile(@NotNull PyFile node) {
      checkDocString(node);
    }

    @Override
    public final void visitPyFunction(@NotNull PyFunction node) {
      if (PythonUnitTestUtil.isTestFunction(node, ThreeState.UNSURE, myTypeEvalContext)) return;
      final Property property = node.getProperty();
      if (property != null && (node == property.getSetter().valueOrNull() || node == property.getDeleter().valueOrNull())) {
        return;
      }
      final String name = node.getName();
      if (name != null && !name.startsWith("_")) checkDocString(node);
    }

    @Override
    public final void visitPyClass(@NotNull PyClass node) {
      if (PythonUnitTestUtil.isTestClass(node, ThreeState.UNSURE, myTypeEvalContext)) return;
      final String name = node.getName();
      if (name == null || name.startsWith("_")) {
        return;
      }
      checkDocString(node);
    }

    protected abstract void checkDocString(@NotNull PyDocStringOwner node);
  }
}
