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

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.codeInsight.controlflow.*;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.impl.stubs.PyVersionSpecificStubBaseKt.evaluateVersionsForElement;

/**
 * @author Dennis.Ushakov
 */
public final class PyDefUseUtil {
  private PyDefUseUtil() {
  }

  private static final int MAX_CONTROL_FLOW_SIZE = 200;

  public static @NotNull List<Instruction> getLatestDefs(@NotNull ScopeOwner block,
                                                         @NotNull String varName,
                                                         @NotNull PsiElement anchor,
                                                         boolean acceptTypeAssertions,
                                                         boolean acceptImplicitImports,
                                                         @NotNull TypeEvalContext context) {
    return getLatestDefs(ControlFlowCache.getControlFlow(block), varName, anchor, acceptTypeAssertions, acceptImplicitImports, context);
  }


  public static @NotNull List<Instruction> getLatestDefs(@NotNull ControlFlow controlFlow,
                                                         @NotNull String varName,
                                                         @NotNull PsiElement anchor,
                                                         boolean acceptTypeAssertions,
                                                         boolean acceptImplicitImports,
                                                         @NotNull TypeEvalContext context) {
    final Instruction[] instructions = controlFlow.getInstructions();
    int startNum = findStartInstructionId(anchor, instructions);
    if (startNum < 0) {
      return Collections.emptyList();
    }

    LanguageLevel languageLevel = PyLanguageFacadeKt.getEffectiveLanguageLevel(anchor.getContainingFile());
    final Collection<Instruction> result = new LinkedHashSet<>();
    final HashMap<PyCallSiteExpression, ConditionalInstruction> pendingTypeGuard = new HashMap<>();
    ControlFlowUtil.iteratePrev(startNum, instructions,
                                instruction -> {
                                  if (instruction instanceof PyWithContextExitInstruction withExit) {
                                    if (!withExit.isSuppressingExceptions(context)) {
                                      return ControlFlowUtil.Operation.CONTINUE;
                                    }
                                  }
                                  if (acceptTypeAssertions && instruction instanceof CallInstruction callInstruction) {
                                    var typeGuardInstruction = pendingTypeGuard.get(instruction.getElement());
                                    if (typeGuardInstruction != null) {
                                      result.add(typeGuardInstruction);
                                      return ControlFlowUtil.Operation.CONTINUE;
                                    }
                                    if (isNotBackEdge(instruction.num(), startNum) && 
                                        context.getOrigin() == callInstruction.getElement().getContainingFile()) {
                                      var newContext = (MAX_CONTROL_FLOW_SIZE > instructions.length)
                                        ? TypeEvalContext.codeAnalysis(context.getOrigin().getProject(), context.getOrigin())
                                        : TypeEvalContext.codeInsightFallback(context.getOrigin().getProject());
                                      if (callInstruction.isNoReturnCall(newContext)) return ControlFlowUtil.Operation.CONTINUE;
                                    }
                                  }
                                  final PsiElement element = instruction.getElement();
                                  if (isNotBackEdge(instruction.num(), startNum)
                                      && acceptTypeAssertions && instruction instanceof ConditionalInstruction conditionalInstruction) {
                                    if (conditionalInstruction.getCondition() instanceof PyTypedElement typedElement && context.getOrigin() == typedElement.getContainingFile()) {
                                      var newContext = (MAX_CONTROL_FLOW_SIZE > instructions.length)
                                                       ? TypeEvalContext.codeAnalysis(context.getOrigin().getProject(), context.getOrigin())
                                                       : TypeEvalContext.codeInsightFallback(context.getOrigin().getProject());
                                      if (newContext.getType(typedElement) instanceof PyNarrowedType narrowedType && narrowedType.isBound()) {
                                        if (narrowedType.getQname().equals(varName)) {
                                          pendingTypeGuard.put(narrowedType.getOriginal(), conditionalInstruction);
                                        }
                                      }
                                    }
                                  }
                                  if (instruction instanceof ReadWriteInstruction rwInstruction) {
                                    final ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
                                    if (access.isWriteAccess() || 
                                        acceptTypeAssertions && access.isAssertTypeAccess() && isNotBackEdge(instruction.num(), startNum)) {
                                      final String name = elementName(element);
                                      if (Comparing.strEqual(name, varName)) {
                                        if (isReachableWithVersionChecks(rwInstruction, languageLevel)) {
                                          result.add(rwInstruction);
                                        }
                                        return ControlFlowUtil.Operation.CONTINUE;
                                      }
                                    }
                                  }
                                  else if (acceptImplicitImports && element instanceof PyImplicitImportNameDefiner implicit) {
                                    if (!implicit.multiResolveName(varName).isEmpty()) {
                                      if (isReachableWithVersionChecks(instruction, languageLevel)) {
                                        result.add(instruction);
                                      }
                                      return ControlFlowUtil.Operation.CONTINUE;
                                    }
                                  }
                                  return ControlFlowUtil.Operation.NEXT;
                                });
    return new ArrayList<>(result);
  }

  /**
   * New analysis handles back edges separately.
   * @see com.jetbrains.python.psi.impl.PyReferenceExpressionImpl#getTypeByControlFlow(String, TypeEvalContext, PyExpression, ScopeOwner) 
   */
  private static boolean isNotBackEdge(int instNum, int startNum) {
    if (Registry.is("python.use.better.control.flow.type.inference")) {
      return true;
    }
    return instNum < startNum;
  }

  private static int findStartInstructionId(@NotNull PsiElement startAnchor, Instruction @NotNull [] instructions) {
    PsiElement realCfgAnchor = startAnchor;
    final PyAugAssignmentStatement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(startAnchor);
    if (augAssignment != null) {
      realCfgAnchor = augAssignment;
    }
    int instr = ControlFlowUtil.findInstructionNumberByElement(instructions, realCfgAnchor);
    if (instr < 0) {
      return instr;
    }
    if (startAnchor instanceof PyTargetExpression) {
      Collection<Instruction> pred = instructions[instr].allPred();
      if (!pred.isEmpty()) {
        instr = pred.iterator().next().num();
      }
    }
    return instr;
  }

  private static boolean isReachableWithVersionChecks(@NotNull Instruction instruction, @NotNull LanguageLevel languageLevel) {
    PsiElement element = instruction.getElement();
    if (element == null) return true;
    Version version = new Version(languageLevel.getMajorVersion(), languageLevel.getMinorVersion(), 0);
    return evaluateVersionsForElement(element).contains(version);
  }

  private static @Nullable String elementName(PsiElement element) {
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

  public static PsiElement @NotNull [] getPostRefs(@NotNull ScopeOwner block, @NotNull PyTargetExpression var, PyExpression anchor) {
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

  private static void getPostRefs(@NotNull PyTargetExpression var,
                                  Instruction[] instructions,
                                  int instr,
                                  boolean @NotNull [] visited,
                                  @NotNull Collection<PyElement> result) {
    // TODO: Use ControlFlowUtil.process() for forwards CFG traversal
    if (visited[instr]) return;
    visited[instr] = true;
    if (instructions[instr] instanceof ReadWriteInstruction instruction) {
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
  public static boolean isDefinedBefore(final @NotNull PsiElement searched, final @NotNull PsiElement target) {
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
