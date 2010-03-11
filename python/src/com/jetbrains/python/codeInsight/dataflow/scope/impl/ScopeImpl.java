package com.jetbrains.python.codeInsight.dataflow.scope.impl;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.DFAEngine;
import com.intellij.codeInsight.dataflow.DFAMap;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsDfaInstance;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsSemilattice;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author oleg
 */
public class ScopeImpl implements Scope {
  private final Instruction[] myFlow;
  private List<DFAMap<ScopeVariable>> myCachedScopeVariables;
  private Set<String> myGlobals;
  private final ScopeOwner myFlowOwner;

  public ScopeImpl(final ScopeOwner flowOwner) {
    myFlowOwner = flowOwner;
    myFlow = flowOwner.getControlFlow().getInstructions();
  }

  @NotNull
  public Collection<ScopeVariable> getDeclaredVariables(@NotNull final PsiElement anchorElement) {
    computeScopeVariables();
    for (int i = 0; i < myFlow.length; i++) {
      Instruction instruction = myFlow[i];
      final PsiElement element = instruction.getElement();
      if (element == anchorElement) {
        return myCachedScopeVariables.get(i).values();
      }
    }
    return Collections.emptyList();
  }

  public ScopeVariable getDeclaredVariable(@NotNull final PsiElement anchorElement,
                                           @NotNull final String name) {
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

  public List<DFAMap<ScopeVariable>> computeScopeVariables() {
    if (myCachedScopeVariables == null) {
      final PyReachingDefsDfaInstance dfaInstance = new PyReachingDefsDfaInstance();
      final PyReachingDefsSemilattice semilattice = new PyReachingDefsSemilattice();
      final DFAEngine<ScopeVariable> engine = new DFAEngine<ScopeVariable>(myFlow, dfaInstance, semilattice);
      myCachedScopeVariables = engine.performDFA();
    }
    return myCachedScopeVariables;
  }

  public boolean isGlobal(final String name) {
    if (myGlobals == null){
      myGlobals = computeGlobals(myFlowOwner);
    }
    return myGlobals.contains(name);
  }

  private static Set<String> computeGlobals(final PsiElement owner) {
    final Set<String> names = new HashSet<String>();
    owner.accept(new PyRecursiveElementVisitor(){
      @Override
      public void visitPyGlobalStatement(final PyGlobalStatement node) {
        for (PyReferenceExpression expression : node.getGlobals()) {
          names.add(expression.getReferencedName());
        }
      }
    });
    return names;
  }
}
