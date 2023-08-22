/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public final class PyDefUseUtil {
  private PyDefUseUtil() {
  }

  @NotNull
  public static List<Instruction> getLatestDefs(ScopeOwner block, String varName, PsiElement anchor, boolean acceptTypeAssertions,
                                                boolean acceptImplicitImports) {
    return getLatestDefs(ControlFlowCache.getControlFlow(block), varName, anchor, acceptTypeAssertions, acceptImplicitImports);
  }


  @NotNull
  public static List<Instruction> getLatestDefs(ControlFlow controlFlow, String varName, PsiElement anchor, boolean acceptTypeAssertions,
                                                boolean acceptImplicitImports) {
    final Instruction[] instructions = controlFlow.getInstructions();
    final PyAugAssignmentStatement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(anchor);
    if (augAssignment != null) {
      anchor = augAssignment;
    }
    int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
    if (instr < 0) {
      return Collections.emptyList();
    }
    if (anchor instanceof PyTargetExpression) {
      Collection<Instruction> pred = instructions[instr].allPred();
      if (!pred.isEmpty()) {
        instr = pred.iterator().next().num();
      }
    }
    final Collection<Instruction> result = getLatestDefs(varName, instructions, instr, acceptTypeAssertions, acceptImplicitImports);
    return new ArrayList<>(result);
  }

  private static Collection<Instruction> getLatestDefs(final String varName, final Instruction[] instructions, final int instr,
                                                       final boolean acceptTypeAssertions, final boolean acceptImplicitImports) {
    final Collection<Instruction> result = new LinkedHashSet<>();
    ControlFlowUtil.iteratePrev(instr, instructions,
                                instruction -> {
                                  final PsiElement element = instruction.getElement();
                                  final PyImplicitImportNameDefiner implicit = PyUtil.as(element, PyImplicitImportNameDefiner.class);
                                  if (instruction instanceof ReadWriteInstruction rwInstruction) {
                                    final ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
                                    if (access.isWriteAccess() || acceptTypeAssertions && access.isAssertTypeAccess()) {
                                      final String name = elementName(element);
                                      if (Comparing.strEqual(name, varName)) {
                                        result.add(rwInstruction);
                                        return ControlFlowUtil.Operation.CONTINUE;
                                      }
                                    }
                                  }
                                  else if (acceptImplicitImports && implicit != null) {
                                    if (!implicit.multiResolveName(varName).isEmpty()) {
                                      result.add(instruction);
                                      return ControlFlowUtil.Operation.CONTINUE;
                                    }
                                  }
                                  return ControlFlowUtil.Operation.NEXT;
                                });
    return result;
  }

  @Nullable
  private static String elementName(PsiElement element) {
    if (element instanceof PyImportElement) {
      return ((PyImportElement) element).getVisibleName();
    }
    if (element instanceof PyReferenceExpression || element instanceof PyTargetExpression) {
      final QualifiedName qname = ((PyQualifiedExpression)element).asQualifiedName();
      if (qname != null) {
        return qname.toString();
      }
    }
    return element instanceof PyElement ? ((PyElement)element).getName() : null;
  }

  public static PsiElement @NotNull [] getPostRefs(ScopeOwner block, PyTargetExpression var, PyExpression anchor) {
    final ControlFlow controlFlow = ControlFlowCache.getControlFlow(block);
    final Instruction[] instructions = controlFlow.getInstructions();
    final int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor);
    if (instr < 0) {
      return PyElement.EMPTY_ARRAY;
    }
    final boolean[] visited = new boolean[instructions.length];
    final Collection<PyElement> result = new HashSet<>();
    for (Instruction instruction : instructions[instr].allSucc()) {
      getPostRefs(var, instructions, instruction.num(), visited, result);
    }
    return result.toArray(PyElement.EMPTY_ARRAY);
  }

  private static void getPostRefs(PyTargetExpression var,
                                  Instruction[] instructions,
                                  int instr,
                                  boolean[] visited,
                                  Collection<PyElement> result) {
    // TODO: Use ControlFlowUtil.process() for forwards CFG traversal
    if (visited[instr]) return;
    visited[instr] = true;
    if (instructions[instr] instanceof ReadWriteInstruction) {
      final ReadWriteInstruction instruction = (ReadWriteInstruction)instructions[instr];
      final PsiElement element = instruction.getElement();
      String name = elementName(element);
      if (Comparing.strEqual(name, var.getName())) {
        final ReadWriteInstruction.ACCESS access = instruction.getAccess();
        if (access.isWriteAccess()) {
          return;
        }
        result.add((PyElement)instruction.getElement());
      }
    }
    for (Instruction instruction : instructions[instr].allSucc()) {
      getPostRefs(var, instructions, instruction.num(), visited, result);
    }
  }

  /**
   * Iterates back through instructions starting at the second element and looks for the first one.
   *
   * @return false for elements from different scopes, true if searched is defined/imported before target
   */
  public static boolean isDefinedBefore(@NotNull final PsiElement searched, @NotNull final PsiElement target) {
    ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(searched);
    Ref<Boolean> definedBefore = Ref.create(false);
    if (scopeOwner != null && scopeOwner == ScopeUtil.getScopeOwner(target)) {
      Instruction[] instructions = ControlFlowCache.getControlFlow(scopeOwner).getInstructions();
      int index = ControlFlowUtil.findInstructionNumberByElement(instructions, target);
      if (index >= 0) {
        ControlFlowUtil.iteratePrev(index, instructions, instruction -> {
          if (instruction.getElement() == searched) {
            boolean isImport = searched instanceof PyImportedNameDefiner;
            boolean isWriteAccess = instruction instanceof ReadWriteInstruction && ((ReadWriteInstruction)instruction).getAccess().isWriteAccess();
            if (isImport || isWriteAccess) {
              definedBefore.set(true);
              return ControlFlowUtil.Operation.BREAK;
            }
          }
          return ControlFlowUtil.Operation.NEXT;
        });
      }
    }
    return definedBefore.get();
  }

  public static class InstructionNotFoundException extends RuntimeException {
  }
}
