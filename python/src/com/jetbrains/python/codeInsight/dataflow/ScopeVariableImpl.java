package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author oleg
 */
public class ScopeVariableImpl implements ScopeVariable {
  private String myName;
  private final Collection<PsiElement> myDeclarations;
  private PsiElement myScope;
  private boolean isParameter;

  public ScopeVariableImpl(final String name, final boolean parameter, final PsiElement scope, final Collection<PsiElement> declarations) {
    myName = name;
    myDeclarations = declarations;
    myScope = scope;
    isParameter = parameter;
  }

  public ScopeVariableImpl(final String name, final boolean parameter, final PsiElement scope, PsiElement declaration) {
    this(name, parameter, scope, Collections.singletonList(declaration));
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Collection<PsiElement> getDeclarations() {
    return myDeclarations;
  }

  public PsiElement getScope() {
    return myScope;
  }

  public boolean isParameter() {
    return isParameter;
  }
}
