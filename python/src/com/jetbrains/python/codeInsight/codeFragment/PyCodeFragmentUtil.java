package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.codeInsight.codeFragment.CodeFragmentUtil;
import com.intellij.codeInsight.codeFragment.Position;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author oleg
 */
public class PyCodeFragmentUtil {
  private PyCodeFragmentUtil() {
  }

  @NotNull
  public static CodeFragment createCodeFragment(@NotNull final ScopeOwner owner,
                                                @NotNull final PsiElement startInScope,
                                                @NotNull final PsiElement endInScope) throws CannotCreateCodeFragmentException {
    final int start = startInScope.getTextOffset();
    final int end = endInScope.getTextOffset() + endInScope.getTextLength();
    final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
    if (flow == null) {
      throw new CannotCreateCodeFragmentException("Cannot determine execution flow for the code fragment");
    }

    final List<Instruction> subGraph = getFragmentSubGraph(flow, start, end);
    final AnalysisResult subGraphAnalysis = analyseSubGraph(subGraph, start, end);
    if (subGraphAnalysis.regularExits > 0 && subGraphAnalysis.returns > 0) {
      throw new CannotCreateCodeFragmentException(
        PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.execution.flow.is.interrupted"));
    }
    if (subGraphAnalysis.targetInstructions > 1) {
      throw new CannotCreateCodeFragmentException(
        PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.execution.flow.is.interrupted"));
    }
    if (subGraphAnalysis.starImports > 0) {
      throw new CannotCreateCodeFragmentException(
        PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.from.import.inside"));
    }

    final PyCodeFragmentBuilder builder = new PyCodeFragmentBuilder(owner, start, end);
    owner.acceptChildren(builder);
    return new CodeFragment(builder.inElements, builder.outElements, subGraphAnalysis.returns > 0);
  }

  @NotNull
  private static List<Instruction> getFragmentSubGraph(@NotNull ControlFlow flow, int start, int end) {
    List<Instruction> instructions = new ArrayList<Instruction>();
    for (Instruction instruction : flow.getInstructions()) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        if (CodeFragmentUtil.getPosition(element, start, end) == Position.INSIDE) {
          instructions.add(instruction);
        }
      }
    }
    // Hack for including inner assert type instructions that can point to elements outside of the selected scope
    for (Instruction instruction : flow.getInstructions()) {
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        if (readWriteInstruction.getAccess().isAssertTypeAccess()) {
          boolean innerAssertType = true;
          for (Instruction next : readWriteInstruction.allSucc()) {
            if (!instructions.contains(next)) {
              innerAssertType = false;
              break;
            }
          }
          if (innerAssertType && !instructions.contains(instruction)) {
            instructions.add(instruction);
          }
        }
      }
    }
    return instructions;
  }

  private static class AnalysisResult {
    private final int starImports;
    private final int targetInstructions;
    private final int regularExits;
    private final int returns;

    public AnalysisResult(int starImports, int targetInstructions, int returns, int regularExits) {
      this.starImports = starImports;
      this.targetInstructions = targetInstructions;
      this.regularExits = regularExits;
      this.returns = returns;
    }
  }

  @NotNull
  private static AnalysisResult analyseSubGraph(@NotNull List<Instruction> subGraph, int start, int end) {
    int returnSources = 0;
    int regularSources = 0;
    final Set<Instruction> targetInstructions = new HashSet<Instruction>();
    int starImports = 0;

    for (Pair<Instruction, Instruction> edge : getOutgoingEdges(subGraph)) {
      final Instruction sourceInstruction = edge.getFirst();
      final Instruction targetInstruction = edge.getSecond();
      final PsiElement source = sourceInstruction.getElement();
      final PsiElement target = targetInstruction.getElement();

      final PyReturnStatement returnStatement = PsiTreeUtil.getParentOfType(source, PyReturnStatement.class, false);
      final boolean isExceptTarget = target instanceof PyExceptPart || target instanceof PyFinallyPart;
      final boolean isLoopTarget = target instanceof PyWhileStatement || PyForStatementNavigator.getPyForStatementByIterable(target) != null;

      if (target != null && !isExceptTarget && !isLoopTarget) {
        targetInstructions.add(targetInstruction);
      }

      if (returnStatement != null && CodeFragmentUtil.getPosition(returnStatement, start, end) == Position.INSIDE) {
        returnSources++;
      }
      else if (!isExceptTarget) {
        regularSources++;
      }
    }

    for (Instruction instruction : subGraph) {
      final PsiElement element = instruction.getElement();
      if (element instanceof PyFromImportStatement) {
        final PyFromImportStatement fromImportStatement = (PyFromImportStatement)element;
        if (fromImportStatement.getStarImportElement() != null) {
          starImports++;
        }
      }
    }

    return new AnalysisResult(starImports, targetInstructions.size(), returnSources, regularSources);
  }

  @NotNull
  private static Set<Pair<Instruction, Instruction>> getOutgoingEdges(@NotNull Collection<Instruction> subGraph) {
    final Set<Pair<Instruction, Instruction>> outgoing = new HashSet<Pair<Instruction, Instruction>>();
    for (Instruction instruction : subGraph) {
      for (Instruction next : instruction.allSucc()) {
        if (!subGraph.contains(next)) {
          outgoing.add(Pair.create(instruction, next));
        }
      }
    }
    return outgoing;
  }
}