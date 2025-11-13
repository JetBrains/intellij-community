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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Version;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.codeInsight.controlflow.*;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

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


  public static @NotNull List<Instruction> getLatestDefs(@NotNull PyControlFlow controlFlow,
                                                         @NotNull String varName,
                                                         @NotNull PsiElement anchor,
                                                         boolean acceptTypeAssertions,
                                                         boolean acceptImplicitImports,
                                                         @NotNull TypeEvalContext context) {
    final Instruction[] instructions = controlFlow.getInstructions();
    int startNum = findStartInstructionId(anchor, controlFlow);
    if (startNum < 0) {
      return Collections.emptyList();
    }

    QualifiedName varQname = QualifiedName.fromDottedString(varName);

    LanguageLevel languageLevel = PyLanguageFacadeKt.getEffectiveLanguageLevel(anchor.getContainingFile());
    final Collection<Instruction> result = new LinkedHashSet<>();
    final HashMap<PyCallSiteExpression, ConditionalInstruction> pendingTypeGuard = new HashMap<>();
    final Ref<@NotNull Boolean> foundPrefixWrite = Ref.create(false);
    iteratePrev(startNum, controlFlow,
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
                    if (instruction.num() < startNum &&
                        context.getOrigin() == callInstruction.getElement().getContainingFile()) {
                      var newContext = (MAX_CONTROL_FLOW_SIZE > instructions.length)
                                       ? TypeEvalContext.codeAnalysis(context.getOrigin().getProject(), context.getOrigin())
                                       : TypeEvalContext.codeInsightFallback(context.getOrigin().getProject());
                      if (callInstruction.isNoReturnCall(newContext)) return ControlFlowUtil.Operation.CONTINUE;
                    }
                  }
                  if (instruction.num() < startNum
                      && acceptTypeAssertions && instruction instanceof ConditionalInstruction conditionalInstruction) {
                    if (conditionalInstruction.getCondition() instanceof PyTypedElement typedElement &&
                        context.getOrigin() == typedElement.getContainingFile()) {
                      var newContext = (MAX_CONTROL_FLOW_SIZE > instructions.length)
                                       ? TypeEvalContext.codeAnalysis(context.getOrigin().getProject(), context.getOrigin())
                                       : TypeEvalContext.codeInsightFallback(context.getOrigin().getProject());
                      if (newContext.getType(typedElement) instanceof PyNarrowedType narrowedType && narrowedType.isBound()) {
                        String narrowedQname = narrowedType.getQname();
                        if (narrowedQname != null) {
                          if (isQualifiedBy(varQname, narrowedQname)) {
                            foundPrefixWrite.set(true);
                            return ControlFlowUtil.Operation.BREAK;
                          }

                          if (narrowedQname.equals(varName)) {
                            pendingTypeGuard.put(narrowedType.getOriginal(), conditionalInstruction);
                          }
                        }
                      }
                    }
                  }
                  if (instruction instanceof ReadWriteInstruction rwInstruction) {
                    final ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
                    if (access.isWriteAccess() ||
                        acceptTypeAssertions && access.isAssertTypeAccess() && instruction.num() < startNum) {

                      final String name = rwInstruction.getName();

                      if (name != null && isQualifiedBy(varQname, name)) {
                        if (isReachableWithVersionChecks(rwInstruction, languageLevel)) {
                          foundPrefixWrite.set(true);
                          return ControlFlowUtil.Operation.BREAK;
                        }
                      }

                      if (Comparing.strEqual(name, varName)) {
                        if (isReachableWithVersionChecks(rwInstruction, languageLevel)) {
                          result.add(rwInstruction);
                        }
                        return ControlFlowUtil.Operation.CONTINUE;
                      }
                    }
                  }
                  else if (acceptImplicitImports && instruction.getElement() instanceof PyImplicitImportNameDefiner implicit) {
                    if (!implicit.multiResolveName(varName).isEmpty()) {
                      if (isReachableWithVersionChecks(instruction, languageLevel)) {
                        result.add(instruction);
                      }
                      return ControlFlowUtil.Operation.CONTINUE;
                    }
                  }
                  return ControlFlowUtil.Operation.NEXT;
                });
    if (foundPrefixWrite.get()) {
      return Collections.emptyList();
    }
    return new ArrayList<>(result);
  }

  private static boolean isQualifiedBy(QualifiedName varQname, @NotNull String qualifier) {
    QualifiedName elementQname = QualifiedName.fromDottedString(qualifier);
    return varQname.getComponentCount() > elementQname.getComponentCount() && varQname.matchesPrefix(elementQname);
  }

  private static int findStartInstructionId(@NotNull PsiElement startAnchor, @NotNull PyControlFlow flow) {
    PsiElement realCfgAnchor = startAnchor;
    final PyAugAssignmentStatement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(startAnchor);
    if (augAssignment != null) {
      realCfgAnchor = augAssignment;
    }
    int instr = flow.getInstruction(realCfgAnchor);
    if (instr < 0) {
      return instr;
    }
    if (startAnchor instanceof PyTargetExpression) {
      Collection<Instruction> pred = flow.getInstructions()[instr].allPred();
      if (!pred.isEmpty()) {
        instr = pred.iterator().next().num();
      }
    }
    return instr;
  }

  /**
   * Modified copy of {@link ControlFlowUtil#iteratePrev(int, Instruction[], com.intellij.util.Function)} that uses
   * {@link PyControlFlow#getPrev(Instruction)} instead of {@link Instruction#allPred()}
   */
  private static void iteratePrev(final int startInstruction,
                                  final @NotNull PyControlFlow controlFlow,
                                  final @NotNull Function<? super Instruction, ControlFlowUtil.Operation> closure) {
    Instruction[] instructions = controlFlow.getInstructions();
    //noinspection SSBasedInspection
    final IntArrayList stack = new IntArrayList(instructions.length);
    final boolean[] visited = new boolean[instructions.length];

    visited[startInstruction] = true;
    stack.push(startInstruction);
    int count = 0;
    while (!stack.isEmpty()) {
      count++;
      if (count % 512 == 0) {
        ProgressManager.checkCanceled();
      }
      final int num = stack.popInt();
      final Instruction instr = instructions[num];
      final ControlFlowUtil.Operation nextOperation = closure.apply(instr);
      // Just ignore previous instructions for the current node and move further
      if (nextOperation == ControlFlowUtil.Operation.CONTINUE) {
        continue;
      }
      // STOP iteration
      if (nextOperation == ControlFlowUtil.Operation.BREAK) {
        break;
      }
      // If we are here, we should process previous nodes in natural way
      assert nextOperation == ControlFlowUtil.Operation.NEXT;
      Collection<Instruction> nextToProcess = controlFlow.getPrev(instr);
      for (Instruction pred : nextToProcess) {
        final int predNum = pred.num();
        if (!visited[predNum]) {
          visited[predNum] = true;
          stack.push(predNum);
        }
      }
    }
  }

  private static boolean isReachableWithVersionChecks(@NotNull Instruction instruction, @NotNull LanguageLevel languageLevel) {
    PsiElement element = instruction.getElement();
    if (element == null) return true;
    Version version = new Version(languageLevel.getMajorVersion(), languageLevel.getMinorVersion(), 0);
    return evaluateVersionsForElement(element).contains(version);
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
      if (Comparing.strEqual(instruction.getName(), var.getName())) {
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
            boolean isWriteAccess =
              instruction instanceof ReadWriteInstruction && ((ReadWriteInstruction)instruction).getAccess().isWriteAccess();
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
