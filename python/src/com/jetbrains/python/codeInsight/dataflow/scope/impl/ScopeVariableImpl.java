/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  private final String myName;
  private final Collection<PsiElement> myDeclarations;
  private final boolean isParameter;

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
