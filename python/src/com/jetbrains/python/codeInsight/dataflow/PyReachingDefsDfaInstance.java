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
package com.jetbrains.python.codeInsight.dataflow;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.map.DfaMapInstance;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyExceptPartNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author oleg
 */
public class PyReachingDefsDfaInstance implements DfaMapInstance<ScopeVariable> {
  // Use this its own map, because check in PyReachingDefsDfaSemilattice is important
  public static final DFAMap<ScopeVariable> INITIAL_MAP = new DFAMap<>();

  public DFAMap<ScopeVariable> fun(final DFAMap<ScopeVariable> map, final Instruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null || !((PyFile) element.getContainingFile()).getLanguageLevel().isPy3K()){
      return processReducedMap(map, instruction, element);
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

  private DFAMap<ScopeVariable> processReducedMap(DFAMap<ScopeVariable> map,
                                                  final Instruction instruction,
                                                  final PsiElement element) {
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
