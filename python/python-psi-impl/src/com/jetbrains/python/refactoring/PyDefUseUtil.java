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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.controlflow.CallInstruction;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlow;
import com.jetbrains.python.codeInsight.controlflow.PyWithContextExitInstruction;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCallSiteOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImplicitImportNameDefiner;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.types.PyNarrowedType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * @author Dennis.Ushakov
 */
public final class PyDefUseUtil {
  private PyDefUseUtil() {
  }

  // For very large control flows we keep using a lighter, project-cached context for the narrowing-detection lookup
  // below to avoid blowing the stack when re-entering type inference from inside a deep CFG walk (PY-73958).
  private static final int MAX_CONTROL_FLOW_SIZE = 200;

  public record LatestDefsResult(@NotNull List<Instruction> defs, boolean foundPrefixCall) {
    static  LatestDefsResult EMPTY = new LatestDefsResult(Collections.emptyList(), false);
  }

  public static @NotNull LatestDefsResult getLatestDefs(@NotNull ScopeOwner block,
                                                        @NotNull String varName,
                                                        @NotNull PsiElement anchor,
                                                        boolean acceptTypeAssertions,
                                                        boolean acceptImplicitImports,
                                                        @NotNull TypeEvalContext context) {
    return getLatestDefs(ControlFlowCache.getControlFlow(block), block, varName, anchor, acceptTypeAssertions, acceptImplicitImports,
                         context);
  }

  public static @NotNull LatestDefsResult getLatestDefs(@NotNull PyControlFlow controlFlow,
                                                              @NotNull ScopeOwner scopeOwner,
                                                              @NotNull String varName,
                                                              @NotNull PsiElement anchor,
                                                              boolean acceptTypeAssertions,
                                                              boolean acceptImplicitImports,
                                                              @NotNull TypeEvalContext context) {
    final Instruction[] instructions = controlFlow.getInstructions();
    int startNum = findStartInstructionId(anchor, controlFlow, scopeOwner);
    if (startNum < 0) {
      return LatestDefsResult.EMPTY;
    }

    QualifiedName varQname = QualifiedName.fromDottedString(varName);

    final Collection<Instruction> result = new LinkedHashSet<>();
    final HashMap<PyCallSiteOwner, ConditionalInstruction> pendingTypeGuard = new HashMap<>();
    final Ref<@NotNull Boolean> foundPrefixWrite = Ref.create(false);
    final Ref<@NotNull Boolean> foundPrefixCall = Ref.create(false);
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
                    if (isNotBackEdge(instruction.num(), startNum) &&
                        context.getOrigin() == callInstruction.getElement().getContainingFile()) {
                      TypeEvalContext narrowingContext = chooseNarrowingContext(context, instructions.length);
                      if (callInstruction.isNoReturnCall(narrowingContext)) return ControlFlowUtil.Operation.CONTINUE;
                    }
                  }
                  if (isNotBackEdge(instruction.num(), startNum)
                      && acceptTypeAssertions && instruction instanceof ConditionalInstruction conditionalInstruction) {
                    if (conditionalInstruction.getCondition() instanceof PyTypedElement typedElement &&
                        context.getOrigin() == typedElement.getContainingFile()) {
                      TypeEvalContext narrowingContext = chooseNarrowingContext(context, instructions.length);
                      if (narrowingContext.getType(typedElement) instanceof PyNarrowedType narrowedType && narrowedType.isBound()) {
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
                  // A call with prefix as receiver or argument (e.g. self.reset() or foo(self)) may mutate attributes, so soft-invalidate narrowing (PY-88265)
                  if (instruction instanceof CallInstruction callInstr) {
                    if (isCallOnPrefix(callInstr, varQname)) {
                      foundPrefixCall.set(true);
                    }
                  }
                  if (instruction instanceof ReadWriteInstruction rwInstruction) {
                    final ReadWriteInstruction.ACCESS access = rwInstruction.getAccess();
                    if (access.isWriteAccess() ||
                        acceptTypeAssertions && access.isAssertTypeAccess() && isNotBackEdge(instruction.num(), startNum)) {

                      final String name = rwInstruction.getName();

                      if (name != null && isQualifiedBy(varQname, name)) {
                        foundPrefixWrite.set(true);
                        return ControlFlowUtil.Operation.BREAK;
                      }

                      if (Comparing.strEqual(name, varName)) {
                        result.add(rwInstruction);
                        return ControlFlowUtil.Operation.CONTINUE;
                      }
                    }
                  }
                  else if (acceptImplicitImports && instruction.getElement() instanceof PyImplicitImportNameDefiner implicit) {
                    if (!implicit.multiResolveName(varName).isEmpty()) {
                      result.add(instruction);
                      return ControlFlowUtil.Operation.CONTINUE;
                    }
                  }
                  return ControlFlowUtil.Operation.NEXT;
                });
    if (foundPrefixWrite.get()) {
      return LatestDefsResult.EMPTY;
    }
    return new LatestDefsResult(new ArrayList<>(result), foundPrefixCall.get());
  }

  /**
   * New analysis handles back edges separately.
   *
   * @see com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
   */
  private static boolean isNotBackEdge(int instNum, int startNum) {
    return instNum < startNum;
  }

  /**
   * Reuse the caller's context for narrowing detection so the AssumptionContext set up by the
   * fixpoint isn't dropped (PY-89245). Fall back to a lighter context for oversized CFGs to keep
   * the stack-overflow guard from PY-73958.
   */
  private static @NotNull TypeEvalContext chooseNarrowingContext(@NotNull TypeEvalContext context, int controlFlowSize) {
    if (controlFlowSize >= MAX_CONTROL_FLOW_SIZE) {
      PsiFile origin = context.getOrigin();
      if (origin != null) {
        return TypeEvalContext.codeInsightFallback(origin.getProject());
      }
    }
    return context;
  }

  private static boolean isCallOnPrefix(@NotNull CallInstruction callInstr, @NotNull QualifiedName varQname) {
    PyExpression receiver = callInstr.getElement().getReceiver(null);
    if (isPrefixExpression(receiver, varQname)) return true;
    for (PyExpression arg : callInstr.getElement().getArguments()) {
      if (isPrefixExpression(arg, varQname)) return true;
    }
    return false;
  }

  private static boolean isPrefixExpression(@Nullable PyExpression expr, @NotNull QualifiedName varQname) {
    if (expr instanceof PyQualifiedExpression qualifiedExpr) {
      QualifiedName exprQname = qualifiedExpr.asQualifiedName();
      return exprQname != null && varQname.getComponentCount() > exprQname.getComponentCount()
             && varQname.matchesPrefix(exprQname);
    }
    return false;
  }

  private static boolean isQualifiedBy(QualifiedName varQname, @NotNull String qualifier) {
    QualifiedName elementQname = QualifiedName.fromDottedString(qualifier);
    return varQname.getComponentCount() > elementQname.getComponentCount() && varQname.matchesPrefix(elementQname);
  }

  private static int findStartInstructionId(@NotNull PsiElement startAnchor, @NotNull PyControlFlow flow, @NotNull ScopeOwner scopeOwner) {
    PsiElement realCfgAnchor = startAnchor;
    final PyAugAssignmentStatement augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(startAnchor);
    if (augAssignment != null) {
      realCfgAnchor = augAssignment;
    }
    int instr = -1;
    for (PsiElement element = realCfgAnchor; element != null && element != scopeOwner; element = element.getParent()) {
      instr = flow.getInstruction(element);
      if (instr >= 0) {
        break;
      }
    }
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
