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

import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.Semilattice;
import com.intellij.codeInsight.dataflow.map.MapSemilattice;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeVariableImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author oleg
 */
public class PyReachingDefsSemilattice implements MapSemilattice<ScopeVariable> {
  public boolean eq(DFAMap<ScopeVariable> e1, DFAMap<ScopeVariable> e2) {
    if (e1 == PyReachingDefsDfaInstance.INITIAL_MAP && e2 != PyReachingDefsDfaInstance.INITIAL_MAP ||
        e2 == PyReachingDefsDfaInstance.INITIAL_MAP && e1 != PyReachingDefsDfaInstance.INITIAL_MAP) {
      return false;
    }
    return e1.equals(e2);
  }

  public DFAMap<ScopeVariable> join(ArrayList<DFAMap<ScopeVariable>> ins) {
    if (ins.isEmpty()) {
      return DFAMap.empty();
    }
    if (ins.size() == 1) {
      return ins.get(0);
    }

    final Set<String> resultNames = getResultNames(ins);
    if (resultNames == null || resultNames.isEmpty()) {
      return new DFAMap<>();
    }

    final DFAMap<ScopeVariable> result = new DFAMap<>();
    for (String name : resultNames) {

      boolean isParameter = true;
      Set<PsiElement> declarations = new HashSet<>();

      // iterating over all maps
      for (DFAMap<ScopeVariable> map : ins) {
        final ScopeVariable variable = map.get(name);
        if (variable == null) {
          continue;
        }
        isParameter = isParameter && variable.isParameter();
        declarations.addAll(variable.getDeclarations());
      }
      final ScopeVariable scopeVariable = new ScopeVariableImpl(name, isParameter, declarations);
      result.put(name, scopeVariable);
    }
    return result;
  }

  @Nullable
  private static Set<String> getResultNames(final ArrayList<DFAMap<ScopeVariable>> ins) {
    // Compute intersection of all the names
    Set<String> names2Include = null;
    for (DFAMap<ScopeVariable> map : ins) {
      if (map == PyReachingDefsDfaInstance.INITIAL_MAP) {
        continue;
      }
      names2Include = map.intersectKeys(names2Include);
    }
    return names2Include;
  }
}
