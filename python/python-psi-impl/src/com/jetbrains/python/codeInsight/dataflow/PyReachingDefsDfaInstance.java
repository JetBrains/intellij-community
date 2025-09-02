// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.map.DfaMapInstance;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.CallInstruction;
import com.jetbrains.python.codeInsight.controlflow.PyDataFlowKt;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PyReachingDefsDfaInstance implements DfaMapInstance<ScopeVariable> {
  // Use this its own map, because check in PyReachingDefsDfaSemilattice is important
  public static final DFAMap<ScopeVariable> INITIAL_MAP = new DFAMap<>();
  public static final DFAMap<ScopeVariable> UNREACHABLE_MARKER = new DFAMap<>();

  private final TypeEvalContext myContext;

  public PyReachingDefsDfaInstance(@NotNull TypeEvalContext typeEvalContext) {
    myContext = typeEvalContext;
  }

  @Override
  public DFAMap<ScopeVariable> fun(final DFAMap<ScopeVariable> map, final Instruction instruction) {
    if (map == UNREACHABLE_MARKER)  return map;
    final PsiElement element = instruction.getElement();
    if (element == null || ((PyFile) element.getContainingFile()).getLanguageLevel().isPython2()){
      return processReducedMap(map, instruction, element);
    }
    if (PyDataFlowKt.isUnreachableForInspection(element, myContext)) {
      return UNREACHABLE_MARKER;
    }
    if (instruction instanceof CallInstruction callInstruction) {
      if (callInstruction.isNoReturnCall(myContext)) {
        return UNREACHABLE_MARKER;
      }
    }
    // Scope reduction
    final DFAMap<ScopeVariable> reducedMap = new DFAMap<>();
    for (Map.Entry<String, ScopeVariable> entry : map.entrySet()) {
      final ScopeVariable value = entry.getValue();
      // Support PEP-3110. (PY-1408)
      if (value.isParameter()){
        final PsiElement declaration = value.getDeclarations().iterator().next();
        final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(declaration);
        if (exceptPart != null){
          if (!PsiTreeUtil.isAncestor(exceptPart, element, false)){
            continue;
          }
        }
      }
      reducedMap.put(entry.getKey(), value);
    }

    return processReducedMap(reducedMap, instruction, element);
  }

  private static DFAMap<ScopeVariable> processReducedMap(DFAMap<ScopeVariable> map,
                                                         final Instruction instruction,
                                                         final PsiElement element) {
    if (element != null && element.getParent() instanceof PyTypeDeclarationStatement) {
      return map;
    }

    String name = null;
    // Process readwrite instruction
    if (instruction instanceof ReadWriteInstruction rwInstruction) {
      if (rwInstruction.getAccess().isWriteAccess()) {
        name = ((ReadWriteInstruction)instruction).getName();
      }
      else if (rwInstruction.getAccess().isDeleteAccess() && instruction.getElement() instanceof PyReferenceExpression) {
        map = map.asWritable();
        map.remove(((ReadWriteInstruction)instruction).getName());
        return map;
      }
    }
    // Processing PyFunction
    else if (element instanceof PyFunction){
      name = ((PyFunction)element).getName();
    }
    if (name == null){
      return map;
    }
    final ScopeVariable variable = map.get(name);

    // Parameter case
    final PsiElement parameterScope = ScopeUtil.getParameterScope(element);
    if (parameterScope != null) {
      final ScopeVariable scopeVariable = new ScopeVariableImpl(name, true, element);
      map = map.asWritable();
      map.put(name, scopeVariable);
    }
    // Local variable case
    else {
      final ScopeVariableImpl scopeVariable;
      final boolean isParameter = variable != null && variable.isParameter();
      if (variable == null) {
        scopeVariable = new ScopeVariableImpl(name, isParameter, element);
      } else {
        scopeVariable = new ScopeVariableImpl(name, isParameter, variable.getDeclarations());
      }
      map = map.asWritable();
      map.put(name, scopeVariable);
    }
    return map;
  }

  @Override
  public @NotNull DFAMap<ScopeVariable> initial() {
    return INITIAL_MAP;
  }
}
