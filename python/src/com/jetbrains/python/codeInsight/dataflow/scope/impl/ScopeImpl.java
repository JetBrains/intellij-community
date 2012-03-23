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
  private volatile List<NameDefiner> myNameDefiners;  // declarations which declare unknown set of names, such as 'from ... import *'

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

  private synchronized List<DFAMap<ScopeVariable>> computeScopeVariables() throws DFALimitExceededException {
    computeFlow();
    if (myCachedScopeVariables == null) {
      final PyReachingDefsDfaInstance dfaInstance = new PyReachingDefsDfaInstance();
      final PyReachingDefsSemilattice semilattice = new PyReachingDefsSemilattice();
      final DFAMapEngine<ScopeVariable> engine = new DFAMapEngine<ScopeVariable>(myFlow, dfaInstance, semilattice);
      myCachedScopeVariables = engine.performDFA();
    }
    return myCachedScopeVariables;
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

  public boolean containsDeclaration(final String name) {
    if (myNamedElements == null || myNameDefiners == null) {
      collectDeclarations();
    }
    if (isNonlocal(name)) {
      return false;
    }
    if (getNamedElement(name) != null) {
      return true;
    }
    for (NameDefiner definer : getNameDefiners()) {
      if (definer.getElementNamed(name) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public List<NameDefiner> getNameDefiners() {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    return myNameDefiners;
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
    final List<NameDefiner> nameDefiners = new ArrayList<NameDefiner>();
    final List<Scope> nestedScopes = new ArrayList<Scope>();
    final Set<String> globals = new HashSet<String>();
    final Set<String> nonlocals = new HashSet<String>();
    myFlowOwner.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(PyTargetExpression node) {
        final PsiElement parent = node.getParent();
        if (node.getQualifier() == null && !(parent instanceof PyImportElement)) {
          super.visitPyTargetExpression(node);
        }
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
      public void visitPyElement(PyElement node) {
        if (node instanceof PsiNamedElement) {
          namedElements.put(node.getName(), (PsiNamedElement)node);
        }
        // TODO: NameDefiners should be used only for defining lazily evaluated names
        if (node instanceof NameDefiner && !(node instanceof PsiNamedElement ||
                                             node instanceof PyParameterList)) {
          nameDefiners.add((NameDefiner)node);
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
    myNamedElements = namedElements;
    myNameDefiners = nameDefiners;
    myNestedScopes = nestedScopes;
    myGlobals = globals;
    myNonlocals = nonlocals;
  }
}
