package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ConditionalInstruction
import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyImplicitImportNameDefiner
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList

class PyControlFlow(private val instructions: Array<Instruction>) : ControlFlow {
  private val elementToInstructionMap: Map<PsiElement?, Int> = buildElementToInstructionMap(instructions)
  private val predecessors: Array<Collection<Instruction>> = buildPredecessors(instructions)

  fun getInstruction(element: PsiElement): Int = elementToInstructionMap[element] ?: -1
  fun getPrev(instruction: Instruction): Collection<Instruction> = predecessors[instruction.num()]
  override fun getInstructions(): Array<Instruction> = instructions
}

private fun buildElementToInstructionMap(instructions: Array<Instruction>): Map<PsiElement?, Int> {
  val result = HashMap<PsiElement?, Int>()
  for (instruction in instructions) {
    result.putIfAbsent(instruction.getElement(), instruction.num())
  }
  return result
}

private fun buildPredecessors(instructions: Array<Instruction>): Array<Collection<Instruction>> {
  val predecessors = arrayOfNulls<IntList?>(instructions.size)
  for (instruction in instructions) {
    ProgressManager.checkCanceled()
    if (skip(instruction)) continue

    for (successor in instruction.allSucc()) {
      var current = successor
      while (true) {
        addEdge(predecessors, instruction.num(), current.num())
        if (!skip(current)) {
          break
        }
        current = current.allSucc().single()
      }
    }
  }

  return Array(predecessors.size) { index ->
    ProgressManager.checkCanceled()
    predecessors[index]?.sortWith(Comparator.reverseOrder())
    predecessors[index]?.map { instructions[it] } ?: emptyList()
  }
}

private fun addEdge(predecessors: Array<IntList?>, from: Int, to: Int) {
  var list = predecessors[to]
  if (list == null) {
    list = IntArrayList(1)
    predecessors[to] = list
  }
  list.add(from)
}

private fun skip(instruction: Instruction): Boolean {
  return when {
    instruction is ConditionalInstruction -> false
    instruction is CallInstruction -> false
    instruction is PyFinallyFailExitInstruction -> false
    instruction is PyRaiseInstruction -> false
    instruction is PyWithContextExitInstruction -> false
    instruction is RefutablePatternInstruction -> false
    instruction is ReadWriteInstruction && instruction.access != ReadWriteInstruction.ACCESS.READ -> false
    instruction.getElement() is PyImplicitImportNameDefiner -> false
    else -> instruction.allPred().size == 1 && instruction.allSucc().size == 1
  }
}
