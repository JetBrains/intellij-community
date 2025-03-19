// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * PSI-based location may point to function, but function may be situated in abstract class.
 * So, we need point to real class as well.
 *
 * @author Ilya.Kazakevich
 */
public final class PyPsiLocationWithFixedClass extends PsiLocation<PyFunction> {
  private final @NotNull PyClass myFixedClass;

  PyPsiLocationWithFixedClass(final @NotNull Project project,
                              final @NotNull PyFunction psiElement,
                              final @NotNull PyClass fixedClass) {
    super(project, psiElement);
    myFixedClass = fixedClass;
  }

  public @NotNull PyClass getFixedClass() {
    return myFixedClass;
  }
}
