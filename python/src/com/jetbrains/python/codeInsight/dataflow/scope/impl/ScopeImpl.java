package com.jetbrains.python.codeInsight.dataflow.scope.impl;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.DFALimitExceededException;
import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.map.DFAMapEngine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsDfaInstance;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsSemilattice;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author oleg
 */
public class ScopeImpl implements Scope {
  private final Instruction[] myFlow;
  private List<DFAMap<ScopeVariable>> myCachedScopeVariables;
  private Set<String> myGlobals;
  private Set<String> myNonlocals;
  private final ScopeOwner myFlowOwner;
  private Set<String> myAllNames;
  private static final Logger LOG = Logger.getInstance(ScopeImpl.class.getName());

  public ScopeImpl(final ScopeOwner flowOwner) {
    myFlowOwner = flowOwner;
    myFlow = ControlFlowCache.getControlFlow(flowOwner).getInstructions();
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
      final DFAMapEngine<ScopeVariable> engine = new DFAMapEngine<ScopeVariable>(myFlow, dfaInstance, semilattice);
      try {
        myCachedScopeVariables = engine.performDFA();
      }
      catch (DFALimitExceededException e) {
        LOG.warn(e);
        myCachedScopeVariables = Collections.emptyList();
      }
    }
    return myCachedScopeVariables;
  }

  public boolean isGlobal(final String name) {
    if (myGlobals == null){
      myGlobals = computeGlobals(myFlowOwner);
    }
    return myGlobals.contains(name);
  }

  public boolean isNonlocal(final String name) {
    if (myNonlocals == null){
      myNonlocals = computeNonlocals(myFlowOwner);
    }
    return myNonlocals.contains(name);
  }

  public boolean containsDeclaration(final String name) {
    if (myAllNames == null){
      myAllNames = computeAllNames();
    }
    return myAllNames.contains(name) && !isNonlocal(name);
  }

  @NotNull
  public Collection<ScopeVariable> getAllDeclaredVariables() {
    final List<DFAMap<ScopeVariable>> vars = computeScopeVariables();
    final int n = vars.size();
    if (n > 0) {
      return vars.get(n - 1).values();
    }
    return Collections.emptyList();
  }

  private Set<String> computeAllNames() {
    final Set<String> names = new HashSet<String>();
    for (Instruction instruction : myFlow) {
      if (instruction instanceof ReadWriteInstruction && ((ReadWriteInstruction)instruction).getAccess().isWriteAccess()){
        names.add(((ReadWriteInstruction)instruction).getName());
      }
    }
    return names;
  }

  private static Set<String> computeGlobals(final PsiElement owner) {
    final Set<String> names = new HashSet<String>();
    owner.accept(new PyRecursiveElementVisitor(){
      @Override
      public void visitPyGlobalStatement(final PyGlobalStatement node) {
        for (PyTargetExpression expression : node.getGlobals()) {
          names.add(expression.getReferencedName());
        }
      }
    });
    return names;
  }

  private static Set<String> computeNonlocals(final ScopeOwner owner) {
    final Set<String> names = new HashSet<String>();
    owner.accept(new PyRecursiveElementVisitor(){
      @Override
      public void visitPyNonlocalStatement(final PyNonlocalStatement node) {
        if (ScopeUtil.getScopeOwner(node) == owner) {
          for (PyTargetExpression expression : node.getVariables()) {
            names.add(expression.getReferencedName());
          }
        }
      }
    });
    return names;
  }
}
