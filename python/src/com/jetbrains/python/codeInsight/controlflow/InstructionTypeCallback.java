package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface InstructionTypeCallback {
  @Nullable
  PyType getType(TypeEvalContext context, @Nullable PsiElement anchor);
}
