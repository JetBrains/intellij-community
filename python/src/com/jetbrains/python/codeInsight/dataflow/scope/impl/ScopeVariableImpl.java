package com.jetbrains.python.codeInsight.dataflow.scope.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author oleg
 */
public class ScopeVariableImpl implements ScopeVariable {
  private String myName;
  private final Collection<PsiElement> myDeclarations;
  private boolean isParameter;

  public ScopeVariableImpl(final String name, final boolean parameter, final Collection<PsiElement> declarations) {
    myName = name;
    myDeclarations = declarations;
    isParameter = parameter;
  }

  public ScopeVariableImpl(final String name, final boolean parameter, PsiElement declaration) {
    this(name, parameter, Collections.singletonList(declaration));
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Collection<PsiElement> getDeclarations() {
    return myDeclarations;
  }

  public boolean isParameter() {
    return isParameter;
  }
}
