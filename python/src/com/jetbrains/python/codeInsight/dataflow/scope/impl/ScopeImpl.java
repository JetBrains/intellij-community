// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python.codeInsight.dataflow.scope.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.jetbrains.python.psi.impl.PyPsiUtils;
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
  private volatile Map<String, Collection<PsiNamedElement>> myNamedElements;
  private volatile List<PyImportedNameDefiner> myImportedNameDefiners;  // Declarations which declare unknown set of imported names
  private volatile Set<String> myAugAssignments;
  private List<PyTargetExpression> myTargetExpressions;

  public ScopeImpl(final ScopeOwner flowOwner) {
    myFlowOwner = flowOwner;
  }

  private synchronized void computeFlow() {
    if (myFlow == null) {
      myFlow = ControlFlowCache.getControlFlow(myFlowOwner).getInstructions();
    }
  }

  @Override
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
      final DFAMapEngine<ScopeVariable> engine = new DFAMapEngine<>(myFlow, dfaInstance, semilattice);
      myCachedScopeVariables = engine.performDFA();
    }
  }

  @Override
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

  @Override
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

  @Override
  public boolean containsDeclaration(final String name) {
    if (myNamedElements == null || myImportedNameDefiners == null) {
      collectDeclarations();
    }
    if (isNonlocal(name)) {
      return false;
    }
    if (!getNamedElements(name, true).isEmpty()) {
      return true;
    }
    if (isAugAssignment(name)) {
      return true;
    }
    for (PyImportedNameDefiner definer : getImportedNameDefiners()) {
      if (!definer.multiResolveName(name).isEmpty()) {
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

  @NotNull
  @Override
  public Collection<PsiNamedElement> getNamedElements(String name, boolean includeNestedGlobals) {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    if (myNamedElements.containsKey(name)) {
      final Collection<PsiNamedElement> elements = myNamedElements.get(name);
      elements.forEach(PyPsiUtils::assertValid);
      return elements;
    }
    if (includeNestedGlobals && isGlobal(name)) {
      for (Scope scope : myNestedScopes) {
        final Collection<PsiNamedElement> globals = scope.getNamedElements(name, true);
        if (!globals.isEmpty()) {
          globals.forEach(PyPsiUtils::assertValid);
          return globals;
        }
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<PsiNamedElement> getNamedElements() {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    final List<PsiNamedElement> results = Lists.newArrayList();
    for (Collection<PsiNamedElement> elements : myNamedElements.values()) {
      results.addAll(elements);
    }
    return results;
  }

  @NotNull
  @Override
  public Collection<PyTargetExpression> getTargetExpressions() {
    if (myTargetExpressions == null) {
      collectDeclarations();
    }
    return myTargetExpressions;
  }

  private void collectDeclarations() {
    final Map<String, Collection<PsiNamedElement>> namedElements = new LinkedHashMap<>();
    final List<PyImportedNameDefiner> importedNameDefiners = new ArrayList<>();
    final List<Scope> nestedScopes = new ArrayList<>();
    final Set<String> globals = new HashSet<>();
    final Set<String> nonlocals = new HashSet<>();
    final Set<String> augAssignments = new HashSet<>();
    final List<PyTargetExpression> targetExpressions = new ArrayList<>();
    myFlowOwner.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(PyTargetExpression node) {
        targetExpressions.add(node);
        final PsiElement parent = node.getParent();
        if (!node.isQualified() && !(parent instanceof PyImportElement)) {
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
        visitDecorators(node.getDecoratorList());
        super.visitPyFunction(node);
      }

      @Override
      public void visitPyClass(PyClass node) {
        visitDecorators(node.getDecoratorList());
        super.visitPyClass(node);
      }

      @Override
      public void visitPyDecoratorList(PyDecoratorList node) {
      }

      @Override
      public void visitPyElement(PyElement node) {
        if (node instanceof PsiNamedElement && !(node instanceof PyKeywordArgument)) {
          final String name = node.getName();
          if (!namedElements.containsKey(name)) {
            namedElements.put(name, Sets.newLinkedHashSet());
          }
          namedElements.get(name).add((PsiNamedElement)node);
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

      private void visitDecorators(@Nullable PyDecoratorList list) {
        if (list != null) {
          for (PyDecorator decorator : list.getDecorators()) {
            decorator.accept(this);
          }
        }
      }
    });

    myNamedElements = namedElements;
    myImportedNameDefiners = importedNameDefiners;
    myNestedScopes = nestedScopes;
    myGlobals = globals;
    myNonlocals = nonlocals;
    myAugAssignments = augAssignments;
    myTargetExpressions = targetExpressions;
  }
}
