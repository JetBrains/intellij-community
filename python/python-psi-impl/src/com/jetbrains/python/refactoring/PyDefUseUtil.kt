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
package com.jetbrains.python.refactoring

import com.intellij.codeInsight.controlflow.ConditionalInstruction
import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.ControlFlowUtil
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.codeInsight.controlflow.CallInstruction
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache.getControlFlow
import com.jetbrains.python.codeInsight.controlflow.PyControlFlow
import com.jetbrains.python.codeInsight.controlflow.PyWithContextExitInstruction
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil.getScopeOwner
import com.jetbrains.python.psi.PyCallSiteOwner
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyImplicitImportNameDefiner
import com.jetbrains.python.psi.PyImportedNameDefiner
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator
import com.jetbrains.python.psi.types.PyNarrowedType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypeEvalContext.Companion.codeInsightFallback
import it.unimi.dsi.fastutil.ints.IntArrayList

/**
 * @author Dennis.Ushakov
 */
object PyDefUseUtil {
  // For very large control flows we keep using a lighter, project-cached context for the narrowing-detection lookup
  // below to avoid blowing the stack when re-entering type inference from inside a deep CFG walk (PY-73958).
  private const val MAX_CONTROL_FLOW_SIZE = 200

  @JvmStatic
  fun getLatestDefs(
    block: ScopeOwner,
    varName: String,
    anchor: PsiElement,
    acceptTypeAssertions: Boolean,
    acceptImplicitImports: Boolean,
    context: TypeEvalContext,
  ): LatestDefsResult {
    return getLatestDefs(
      getControlFlow(block), block, varName, anchor, acceptTypeAssertions, acceptImplicitImports,
      context
    )
  }

  @JvmStatic
  fun getLatestDefs(
    controlFlow: PyControlFlow,
    scopeOwner: ScopeOwner,
    varName: String,
    anchor: PsiElement,
    acceptTypeAssertions: Boolean,
    acceptImplicitImports: Boolean,
    context: TypeEvalContext,
  ): LatestDefsResult {
    val instructions = controlFlow.instructions
    val startNum = findStartInstructionId(anchor, controlFlow, scopeOwner)
    if (startNum < 0) {
      return LatestDefsResult.EMPTY
    }

    val varQname = QualifiedName.fromDottedString(varName)

    val result: MutableCollection<Instruction> = LinkedHashSet()
    val pendingTypeGuard = HashMap<PyCallSiteOwner?, ConditionalInstruction?>()
    val foundPrefixWrite = Ref(false)
    val foundPrefixCall = Ref(false)
    iteratePrev(startNum, controlFlow) { instruction ->
      if (instruction is PyWithContextExitInstruction) {
        if (!instruction.isSuppressingExceptions(context)) {
          return@iteratePrev ControlFlowUtil.Operation.CONTINUE
        }
      }
      if (acceptTypeAssertions && instruction is CallInstruction) {
        val typeGuardInstruction = pendingTypeGuard[instruction.element]
        if (typeGuardInstruction != null) {
          result.add(typeGuardInstruction)
          return@iteratePrev ControlFlowUtil.Operation.CONTINUE
        }
        if (isNotBackEdge(instruction.num(), startNum) &&
            context.origin === instruction.element.containingFile
        ) {
          val narrowingContext = chooseNarrowingContext(context, instructions.size)
          if (instruction.isNoReturnCall(narrowingContext)) return@iteratePrev ControlFlowUtil.Operation.CONTINUE
        }
      }
      if (isNotBackEdge(instruction!!.num(), startNum)
          && acceptTypeAssertions && instruction is ConditionalInstruction
      ) {
        val typedElement = instruction.condition
        if (typedElement is PyTypedElement &&
            context.origin === typedElement.containingFile
        ) {
          val narrowingContext = chooseNarrowingContext(context, instructions.size)
          val narrowedType = narrowingContext.getType(typedElement)
          if (narrowedType is PyNarrowedType && narrowedType.isBound()) {
            val narrowedQname: String? = narrowedType.qname
            if (narrowedQname != null) {
              if (isQualifiedBy(varQname, narrowedQname)) {
                foundPrefixWrite.set(true)
                return@iteratePrev ControlFlowUtil.Operation.BREAK
              }

              if (narrowedQname == varName) {
                pendingTypeGuard[narrowedType.original] = instruction
              }
            }
          }
        }
      }
      // A call with prefix as receiver or argument (e.g. self.reset() or foo(self)) may mutate attributes, so soft-invalidate narrowing (PY-88265)
      if (instruction is CallInstruction) {
        if (isCallOnPrefix(instruction, varQname)) {
          foundPrefixCall.set(true)
        }
      }
      if (instruction is ReadWriteInstruction) {
        val access = instruction.access
        if (access.isWriteAccess ||
            acceptTypeAssertions && access.isAssertTypeAccess && isNotBackEdge(instruction.num(), startNum)
        ) {
          val name = instruction.name

          if (name != null && isQualifiedBy(varQname, name)) {
            foundPrefixWrite.set(true)
            return@iteratePrev ControlFlowUtil.Operation.BREAK
          }

          if (Comparing.strEqual(name, varName)) {
            result.add(instruction)
            return@iteratePrev ControlFlowUtil.Operation.CONTINUE
          }
        }
      }
      else if (acceptImplicitImports && instruction.element is PyImplicitImportNameDefiner) {
        val implicit = instruction.element as PyImplicitImportNameDefiner
        if (!implicit.multiResolveName(varName).isEmpty()) {
          result.add(instruction)
          return@iteratePrev ControlFlowUtil.Operation.CONTINUE
        }
      }
      ControlFlowUtil.Operation.NEXT
    }
    if (foundPrefixWrite.get()) {
      return LatestDefsResult.EMPTY
    }
    return LatestDefsResult(ArrayList(result), foundPrefixCall.get())
  }

  /**
   * New analysis handles back edges separately.
   *
   * @see com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
   */
  private fun isNotBackEdge(instNum: Int, startNum: Int): Boolean {
    return instNum < startNum
  }

  /**
   * Reuse the caller's context for narrowing detection so the AssumptionContext set up by the
   * fixpoint isn't dropped (PY-89245). Fall back to a lighter context for oversized CFGs to keep
   * the stack-overflow guard from PY-73958.
   */
  private fun chooseNarrowingContext(context: TypeEvalContext, controlFlowSize: Int): TypeEvalContext {
    if (controlFlowSize >= MAX_CONTROL_FLOW_SIZE) {
      val origin = context.origin
      if (origin != null) {
        return codeInsightFallback(origin.project)
      }
    }
    return context
  }

  private fun isCallOnPrefix(callInstr: CallInstruction, varQname: QualifiedName): Boolean {
    val receiver = callInstr.element.getReceiver(null)
    if (isPrefixExpression(receiver, varQname)) return true
    for (arg in callInstr.element.arguments) {
      if (isPrefixExpression(arg, varQname)) return true
    }
    return false
  }

  private fun isPrefixExpression(expr: PyExpression?, varQname: QualifiedName): Boolean {
    if (expr is PyQualifiedExpression) {
      val exprQname = expr.asQualifiedName()
      return exprQname != null && varQname.componentCount > exprQname.componentCount && varQname.matchesPrefix(exprQname)
    }
    return false
  }

  private fun isQualifiedBy(varQname: QualifiedName, qualifier: String): Boolean {
    val elementQname = QualifiedName.fromDottedString(qualifier)
    return varQname.componentCount > elementQname.componentCount && varQname.matchesPrefix(elementQname)
  }

  private fun findStartInstructionId(startAnchor: PsiElement, flow: PyControlFlow, scopeOwner: ScopeOwner): Int {
    var realCfgAnchor: PsiElement? = startAnchor
    val augAssignment = PyAugAssignmentStatementNavigator.getStatementByTarget(startAnchor)
    if (augAssignment != null) {
      realCfgAnchor = augAssignment
    }
    var instr = -1
    var element = realCfgAnchor
    while (element != null && element !== scopeOwner) {
      instr = flow.getInstruction(element)
      if (instr >= 0) {
        break
      }
      element = element.parent
    }
    if (instr < 0) {
      return instr
    }
    if (startAnchor is PyTargetExpression) {
      val pred = flow.instructions[instr].allPred()
      if (!pred.isEmpty()) {
        instr = pred.iterator().next()!!.num()
      }
    }
    return instr
  }

  /**
   * Modified copy of [ControlFlowUtil.iteratePrev] that uses
   * [PyControlFlow.getPrev] instead of [Instruction.allPred]
   */
  private fun iteratePrev(
    startInstruction: Int,
    controlFlow: PyControlFlow,
    closure: (Instruction?) -> ControlFlowUtil.Operation,
  ) {
    val instructions = controlFlow.instructions
    val stack = IntArrayList(instructions.size)
    val visited = BooleanArray(instructions.size)

    visited[startInstruction] = true
    stack.push(startInstruction)
    var count = 0
    while (!stack.isEmpty) {
      count++
      if (count % 512 == 0) {
        ProgressManager.checkCanceled()
      }
      val num = stack.popInt()
      val instr = instructions[num]
      val nextOperation = closure(instr)
      // Just ignore previous instructions for the current node and move further
      if (nextOperation == ControlFlowUtil.Operation.CONTINUE) {
        continue
      }
      // STOP iteration
      if (nextOperation == ControlFlowUtil.Operation.BREAK) {
        break
      }
      // If we are here, we should process previous nodes in natural way
      assert(nextOperation == ControlFlowUtil.Operation.NEXT)
      val nextToProcess: Collection<Instruction> = controlFlow.getPrev(instr)
      for (pred in nextToProcess) {
        val predNum = pred.num()
        if (!visited[predNum]) {
          visited[predNum] = true
          stack.push(predNum)
        }
      }
    }
  }

  @JvmStatic
  fun getPostRefs(block: ScopeOwner, `var`: PyTargetExpression, anchor: PyExpression?): Array<PsiElement?> {
    val controlFlow: ControlFlow = getControlFlow(block)
    val instructions = controlFlow.instructions
    val instr = ControlFlowUtil.findInstructionNumberByElement(instructions, anchor)
    if (instr < 0) {
      return PyElement.EMPTY_ARRAY as Array<PsiElement?>
    }
    val visited = BooleanArray(instructions.size)
    val result: MutableCollection<PyElement?> = HashSet()
    for (instruction in instructions[instr].allSucc()) {
      getPostRefs(`var`, instructions, instruction.num(), visited, result)
    }
    return result.toTypedArray()
  }

  private fun getPostRefs(
    `var`: PyTargetExpression,
    instructions: Array<Instruction>,
    instr: Int,
    visited: BooleanArray,
    result: MutableCollection<PyElement?>,
  ) {
    // TODO: Use ControlFlowUtil.process() for forwards CFG traversal
    if (visited[instr]) return
    visited[instr] = true
    if (instructions[instr] is ReadWriteInstruction) {
      val instruction = instructions[instr] as ReadWriteInstruction
      if (Comparing.strEqual(instruction.name, `var`.name)) {
        val access: ReadWriteInstruction.ACCESS = instruction.access
        if (access.isWriteAccess) {
          return
        }
        result.add(instruction.element as PyElement?)
      }
    }
    for (instruction in instructions[instr].allSucc()) {
      getPostRefs(`var`, instructions, instruction.num(), visited, result)
    }
  }

  /**
   * Iterates back through instructions starting at the second element and looks for the first one.
   *
   * @return false for elements from different scopes, true if searched is defined/imported before target
   */
  @JvmStatic
  fun isDefinedBefore(searched: PsiElement, target: PsiElement): Boolean {
    val scopeOwner = getScopeOwner(searched)
    val definedBefore = Ref(false)
    if (scopeOwner != null && scopeOwner === getScopeOwner(target)) {
      val instructions = getControlFlow(scopeOwner).instructions
      val index = ControlFlowUtil.findInstructionNumberByElement(instructions, target)
      if (index >= 0) {
        ControlFlowUtil.iteratePrev(index, instructions) { instruction ->
          if (instruction.element === searched) {
            val isImport = searched is PyImportedNameDefiner
            val isWriteAccess =
              instruction is ReadWriteInstruction && instruction.access.isWriteAccess
            if (isImport || isWriteAccess) {
              definedBefore.set(true)
              return@iteratePrev ControlFlowUtil.Operation.BREAK
            }
          }
          ControlFlowUtil.Operation.NEXT
        }
      }
    }
    return definedBefore.get()
  }

  @JvmRecord
  data class LatestDefsResult(val defs: List<Instruction>, val foundPrefixCall: Boolean) {
    companion object {
      val EMPTY: LatestDefsResult = LatestDefsResult(emptyList(), false)
    }
  }

  class InstructionNotFoundException : RuntimeException()
}
