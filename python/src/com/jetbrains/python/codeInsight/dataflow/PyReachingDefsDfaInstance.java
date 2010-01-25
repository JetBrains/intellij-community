package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.DFAMap;
import com.intellij.codeInsight.dataflow.DfaInstance;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.WriteInstruction;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author oleg
 */
public class PyReachingDefsDfaInstance implements DfaInstance<ScopeVariable> {
  // Use this its own map, because check in PyReachingDefsDfaSemilattice is important
  public static final DFAMap<ScopeVariable> INITIAL_MAP = new DFAMap<ScopeVariable>();

  public DFAMap<ScopeVariable> fun(final DFAMap<ScopeVariable> map, Instruction instruction) {
    final PsiElement element = instruction.getElement();
    //if (element == null || element.getUserData(ReferenceCompletionUtil.REFERENCE_BEING_COMPLETED)!=null){
    //  return map;
    //}

    // Scope reduction
    final DFAMap<ScopeVariable> reducedMap = new DFAMap<ScopeVariable>();
    for (Map.Entry<String, ScopeVariable> entry : map.entrySet()) {
      final ScopeVariable value = entry.getValue();
      if (element != null && PsiTreeUtil.isAncestor(value.getScope(), element, false)){
        reducedMap.put(entry.getKey(), value);
      }
    }

    return processReducedMap(reducedMap, instruction, element);
  }

  private static DFAMap<ScopeVariable> processReducedMap(final DFAMap<ScopeVariable> map,
                                                         final Instruction instruction,
                                                         final PsiElement element) {
    if (instruction instanceof WriteInstruction) {
      final WriteInstruction wInstruction = (WriteInstruction)instruction;
      final String name = wInstruction.getName();
      final ScopeVariable variable = map.get(name);
      // If parameter
      if (UsageAnalyzer.isParameter(element)) {
        final PsiElement scope = ScopeUtil.getScopeElement(element);
        final ScopeVariable scopeVariable = new ScopeVariableImpl(name, true, scope, element);
        map.put(name, scopeVariable);
      } else {
        final ScopeVariableImpl scopeVariable;
        final boolean isParameter = variable != null && variable.isParameter();
        if (variable == null) {
          final PsiElement scope = ScopeUtil.getScopeElement(element);
          scopeVariable = new ScopeVariableImpl(name, isParameter, scope, element);
        } else {
          scopeVariable = new ScopeVariableImpl(name, isParameter, variable.getScope(),
                                                variable.getDeclarations());
        }
        map.put(name, scopeVariable);
      }
    }
    return map;
  }

  @NotNull
  public DFAMap<ScopeVariable> initial() {
    return INITIAL_MAP;
  }

  public boolean isForward() {
    return true;
  }
}
