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

  @Override
  public String toString() {
    return (isParameter() ? "par" : "var") + "(" + myName+ ")[" + myDeclarations.size() + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ScopeVariableImpl)) return false;

    ScopeVariableImpl that = (ScopeVariableImpl)o;
    if (isParameter != that.isParameter) return false;
    if (myDeclarations != null ? !myDeclarations.equals(that.myDeclarations) : that.myDeclarations != null) return false;
    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }
}
