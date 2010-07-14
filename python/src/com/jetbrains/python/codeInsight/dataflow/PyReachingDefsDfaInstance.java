package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.DfaInstance;
import com.intellij.codeInsight.dataflow.map.DfaMapInstance;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyReachingDefsDfaInstance implements DfaMapInstance<ScopeVariable> {
  // Use this its own map, because check in PyReachingDefsDfaSemilattice is important
  public static final DFAMap<ScopeVariable> INITIAL_MAP = new DFAMap<ScopeVariable>();

  public DFAMap<ScopeVariable> fun(DFAMap<ScopeVariable> map, Instruction instruction) {
    final PsiElement element = instruction.getElement();
    String name = null;
    // Process readwrite instruction
    if (instruction instanceof ReadWriteInstruction && ((ReadWriteInstruction)instruction).getAccess().isWriteAccess()) {
      name = ((ReadWriteInstruction)instruction).getName();
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

  @NotNull
  public DFAMap<ScopeVariable> initial() {
    return INITIAL_MAP;
  }

  public boolean isForward() {
    return true;
  }
}
