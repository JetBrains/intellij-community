/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.DFALimitExceededException;
import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.map.DFAMapEngine;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsDfaInstance;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsSemilattice;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author oleg
 */
public class ScopeImpl implements Scope {
  private volatile Instruction[] myFlow;
  private volatile List<DFAMap<ScopeVariable>> myCachedScopeVariables;
  private volatile Set<String> myGlobals;
  private volatile Set<String> myNonlocals;
  private volatile List<Scope> myNestedScopes;
  private final ScopeOwner myFlowOwner;
  private volatile Map<String, PsiNamedElement> myNamedElements;
  private volatile List<PyImportedNameDefiner> myImportedNameDefiners;  // Declarations which declare unknown set of imported names
  private volatile Set<String> myAugAssignments;

  public ScopeImpl(final ScopeOwner flowOwner) {
    myFlowOwner = flowOwner;
  }

  private synchronized void computeFlow() {
    if (myFlow == null) {
      myFlow = ControlFlowCache.getControlFlow(myFlowOwner).getInstructions();
    }
  }

  public ScopeVariable getDeclaredVariable(@NotNull final PsiElement anchorElement,
                                           @NotNull final String name) throws DFALimitExceededException {
    computeScopeVariables();
    for (int i = 0; i < myFlow.length; i++) {
      Instruction instruction = myFlow[i];
      final PsiElement element = instruction.getElement();
      if (element == anchorElement) {
        return myCachedScopeVariables.get(i).get(name);
      }
    }
    return null;
  }

  private synchronized void computeScopeVariables() throws DFALimitExceededException {
    computeFlow();
    if (myCachedScopeVariables == null) {
      final PyReachingDefsDfaInstance dfaInstance = new PyReachingDefsDfaInstance();
      final PyReachingDefsSemilattice semilattice = new PyReachingDefsSemilattice();
      final DFAMapEngine<ScopeVariable> engine = new DFAMapEngine<ScopeVariable>(myFlow, dfaInstance, semilattice);
      myCachedScopeVariables = engine.performDFA();
    }
  }

  public boolean isGlobal(final String name) {
    if (myGlobals == null || myNestedScopes == null) {
      collectDeclarations();
    }
    if (myGlobals.contains(name)) {
      return true;
    }
    for (Scope scope : myNestedScopes) {
      if (scope.isGlobal(name)) {
        return true;
      }
    }
    return false;
  }

  public boolean isNonlocal(final String name) {
    if (myNonlocals == null || myNestedScopes == null) {
      collectDeclarations();
    }
    return myNonlocals.contains(name);
  }

  private boolean isAugAssignment(final String name) {
    if (myAugAssignments == null || myNestedScopes == null) {
      collectDeclarations();
    }
    return myAugAssignments.contains(name);
  }

  public boolean containsDeclaration(final String name) {
    if (myNamedElements == null || myImportedNameDefiners == null) {
      collectDeclarations();
    }
    if (isNonlocal(name)) {
      return false;
    }
    if (getNamedElement(name) != null) {
      return true;
    }
    if (isAugAssignment(name)) {
      return true;
    }
    for (NameDefiner definer : getImportedNameDefiners()) {
      if (definer.getElementNamed(name) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public List<PyImportedNameDefiner> getImportedNameDefiners() {
    if (myImportedNameDefiners == null) {
      collectDeclarations();
    }
    return myImportedNameDefiners;
  }

  @Nullable
  @Override
  public PsiNamedElement getNamedElement(String name) {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    final PsiNamedElement element = myNamedElements.get(name);
    if (element != null) {
      return element;
    }
    if (isGlobal(name)) {
      for (Scope scope : myNestedScopes) {
        final PsiNamedElement global = scope.getNamedElement(name);
        if (global != null) {
          return global;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<PsiNamedElement> getNamedElements() {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    return myNamedElements.values();
  }

  private void collectDeclarations() {
    final Map<String, PsiNamedElement> namedElements = new HashMap<String, PsiNamedElement>();
    final List<PyImportedNameDefiner> importedNameDefiners = new ArrayList<PyImportedNameDefiner>();
    final List<Scope> nestedScopes = new ArrayList<Scope>();
    final Set<String> globals = new HashSet<String>();
    final Set<String> nonlocals = new HashSet<String>();
    final Set<String> augAssignments = new HashSet<String>();
    myFlowOwner.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(PyTargetExpression node) {
        final PsiElement parent = node.getParent();
        if (node.getQualifier() == null && !(parent instanceof PyImportElement)) {
          super.visitPyTargetExpression(node);
        }
      }

      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        if (PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null) {
          augAssignments.add(node.getName());
        }
        super.visitPyReferenceExpression(node);
      }

      @Override
      public void visitPyGlobalStatement(PyGlobalStatement node) {
        for (PyTargetExpression expression : node.getGlobals()) {
          final String name = expression.getReferencedName();
          globals.add(name);
          namedElements.put(name, expression);
        }
        super.visitPyGlobalStatement(node);
      }

      @Override
      public void visitPyNonlocalStatement(PyNonlocalStatement node) {
        for (PyTargetExpression expression : node.getVariables()) {
          nonlocals.add(expression.getReferencedName());
        }
        super.visitPyNonlocalStatement(node);
      }

      @Override
      public void visitPyFunction(PyFunction node) {
        for (PyParameter parameter : node.getParameterList().getParameters()) {
          final PyExpression defaultValue = parameter.getDefaultValue();
          if (defaultValue != null) {
            defaultValue.accept(this);
          }
        }
        super.visitPyFunction(node);
      }

      @Override
      public void visitPyElement(PyElement node) {
        if (node instanceof PsiNamedElement && !(node instanceof PyKeywordArgument)) {
          namedElements.put(node.getName(), (PsiNamedElement)node);
        }
        if (node instanceof PyImportedNameDefiner) {
          importedNameDefiners.add((PyImportedNameDefiner)node);
        }
        if (node instanceof ScopeOwner) {
          final Scope scope = ControlFlowCache.getScope((ScopeOwner)node);
          nestedScopes.add(scope);
        }
        else {
          super.visitPyElement(node);
        }
      }
    });

    Collections.reverse(importedNameDefiners);

    myNamedElements = namedElements;
    myImportedNameDefiners = importedNameDefiners;
    myNestedScopes = nestedScopes;
    myGlobals = globals;
    myNonlocals = nonlocals;
    myAugAssignments = augAssignments;
  }
}
