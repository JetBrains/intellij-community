package com.jetbrains.python.codeInsight.dataflow.scope;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author oleg
 */
public interface ScopeVariable {
  @NotNull
  String getName();

  @NotNull
  Collection<PsiElement> getDeclarations();

  boolean isParameter();
}
