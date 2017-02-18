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
package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import com.intellij.codeInsight.codeFragment.CodeFragmentUtil;
import com.intellij.codeInsight.codeFragment.Position;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author oleg
 */
public class PyCodeFragmentUtil {
  private PyCodeFragmentUtil() {
  }

  @NotNull
  public static PyCodeFragment createCodeFragment(@NotNull final ScopeOwner owner,
                                                  @NotNull final PsiElement startInScope,
                                                  @NotNull final PsiElement endInScope) throws CannotCreateCodeFragmentException {
    final int start = startInScope.getTextOffset();
    final int end = endInScope.getTextOffset() + endInScope.getTextLength();
    final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
    if (flow == null) {
      throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.undetermined.execution.flow"));
    }
    final List<Instruction> graph = Arrays.asList(flow.getInstructions());
    final List<Instruction> subGraph = getFragmentSubGraph(graph, start, end);
    final AnalysisResult subGraphAnalysis = analyseSubGraph(subGraph, start, end);
    if ((subGraphAnalysis.regularExits > 0 && subGraphAnalysis.returns > 0) ||
        subGraphAnalysis.targetInstructions > 1 ||
        subGraphAnalysis.outerLoopBreaks > 0) {
      throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.interrupted.execution.flow"));
    }
    if (subGraphAnalysis.starImports > 0) {
      throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.star.import"));
    }

    final Set<String> globalWrites = getGlobalWrites(subGraph, owner);
    final Set<String> nonlocalWrites = getNonlocalWrites(subGraph, owner);

    final Set<String> inputNames = new HashSet<>();
    for (PsiElement element : filterElementsInScope(getInputElements(subGraph, graph), owner)) {
      final String name = getName(element);
      if (name != null) {
        // Ignore "self" and "cls", they are generated automatically when extracting any method fragment
        if (resolvesToBoundMethodParameter(element)) {
          continue;
        }
        if (globalWrites.contains(name) || nonlocalWrites.contains(name)) {
          continue;
        }
        inputNames.add(name);
      }
    }

    final Set<String> outputNames = new HashSet<>();
    for (PsiElement element : getOutputElements(subGraph, graph)) {
      final String name = getName(element);
      if (name != null) {
        if (globalWrites.contains(name) || nonlocalWrites.contains(name)) {
          continue;
        }
        outputNames.add(name);
      }
    }

    final boolean yieldsFound = subGraphAnalysis.yieldExpressions > 0;
    if (yieldsFound && LanguageLevel.forElement(owner).isOlderThan(LanguageLevel.PYTHON33)) {
      throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.yield"));
    }
    final boolean isAsync = owner instanceof PyFunction && ((PyFunction)owner).isAsync();

    return new PyCodeFragment(inputNames, outputNames, globalWrites, nonlocalWrites, subGraphAnalysis.returns > 0, yieldsFound, isAsync);
  }

  private static boolean resolvesToBoundMethodParameter(@NotNull PsiElement element) {
    if (PyPsiUtils.isMethodContext(element)) {
      final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      if (function != null) {
        final PsiReference reference = element.getReference();
        if (reference != null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PyParameter) {
            final PyParameterList parameterList = PsiTreeUtil.getParentOfType(resolved, PyParameterList.class);
            if (parameterList != null) {
              final PyParameter[] parameters = parameterList.getParameters();
              if (parameters.length > 0) {
                if (resolved == parameters[0]) {
                  final PyFunction.Modifier modifier = function.getModifier();
                  if (modifier == null || modifier == PyFunction.Modifier.CLASSMETHOD) {
                    return true;
                  }
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  @Nullable
  private static String getName(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PyImportElement) {
      return ((PyImportElement)element).getVisibleName();
    }
    else if (element instanceof PyElement) {
      return ((PyElement)element).getName();
    }
    return null;
  }

  @NotNull
  private static List<Instruction> getFragmentSubGraph(@NotNull List<Instruction> graph, int start, int end) {
    List<Instruction> instructions = new ArrayList<>();
    for (Instruction instruction : graph) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        if (CodeFragmentUtil.getPosition(element, start, end) == Position.INSIDE) {
          instructions.add(instruction);
        }
      }
    }
    // Hack for including inner assert type instructions that can point to elements outside of the selected scope
    for (Instruction instruction : graph) {
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
    private final int outerLoopBreaks;
    private final int yieldExpressions;

    public AnalysisResult(int starImports, int targetInstructions, int returns, int regularExits, int outerLoopBreaks, int yieldExpressions) {
      this.starImports = starImports;
      this.targetInstructions = targetInstructions;
      this.regularExits = regularExits;
      this.returns = returns;
      this.outerLoopBreaks = outerLoopBreaks;
      this.yieldExpressions = yieldExpressions;
    }
  }

  @NotNull
  private static AnalysisResult analyseSubGraph(@NotNull List<Instruction> subGraph, int start, int end) {
    int returnSources = 0;
    int regularSources = 0;
    final Set<Instruction> targetInstructions = new HashSet<>();
    int starImports = 0;
    int outerLoopBreaks = 0;
    int yieldExpressions = 0;

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

    final Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
    for (PsiElement element : subGraphElements) {
      if (element instanceof PyFromImportStatement) {
        final PyFromImportStatement fromImportStatement = (PyFromImportStatement)element;
        if (fromImportStatement.getStarImportElement() != null) {
          starImports++;
        }
      }
      if (element instanceof PyContinueStatement || element instanceof PyBreakStatement) {
        final PyLoopStatement loopStatement = PsiTreeUtil.getParentOfType(element, PyLoopStatement.class);
        if (loopStatement != null && !subGraphElements.contains(loopStatement)) {
          outerLoopBreaks++;
        }
      }
      if (element instanceof PyYieldExpression) {
        yieldExpressions++;
      }
    }

    return new AnalysisResult(starImports, targetInstructions.size(), returnSources, regularSources, outerLoopBreaks, yieldExpressions);
  }

  @NotNull
  private static Set<Pair<Instruction, Instruction>> getOutgoingEdges(@NotNull Collection<Instruction> subGraph) {
    final Set<Pair<Instruction, Instruction>> outgoing = new HashSet<>();
    for (Instruction instruction : subGraph) {
      for (Instruction next : instruction.allSucc()) {
        if (!subGraph.contains(next)) {
          outgoing.add(Pair.create(instruction, next));
        }
      }
    }
    return outgoing;
  }

  @NotNull
  public static List<PsiElement> getInputElements(@NotNull List<Instruction> subGraph, @NotNull List<Instruction> graph) {
    final List<PsiElement> result = new ArrayList<>();
    final Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
    for (Instruction instruction : getReadInstructions(subGraph)) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        final PsiReference reference = element.getReference();
        if (reference != null) {
          for (PsiElement resolved : multiResolve(reference)) {
            if (!subGraphElements.contains(resolved)) {
              result.add(element);
              break;
            }
          }
        }
      }
    }
    final List<PsiElement> outputElements = getOutputElements(subGraph, graph);
    for (Instruction instruction : getWriteInstructions(subGraph)) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        final PsiReference reference = element.getReference();
        if (reference != null) {
          for (PsiElement resolved : multiResolve(reference)) {
            if (!subGraphElements.contains(resolved) && outputElements.contains(element)) {
              result.add(element);
            }
          }
        }
      }
    }
    return result;
  }

  @NotNull
  private static List<PsiElement> getOutputElements(@NotNull List<Instruction> subGraph, @NotNull List<Instruction> graph) {
    final List<PsiElement> result = new ArrayList<>();
    final List<Instruction> outerGraph = new ArrayList<>();
    for (Instruction instruction : graph) {
      if (!subGraph.contains(instruction)) {
        outerGraph.add(instruction);
      }
    }
    final Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
    for (Instruction instruction : getReadInstructions(outerGraph)) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        final PsiReference reference = element.getReference();
        if (reference != null) {
          for (PsiElement resolved : multiResolve(reference)) {
            if (subGraphElements.contains(resolved)) {
              result.add(resolved);
            }
          }
        }
      }
    }
    return result;
  }

  @NotNull
  private static List<PsiElement> filterElementsInScope(@NotNull Collection<PsiElement> elements, @NotNull ScopeOwner owner) {
    final List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : elements) {
      final PsiReference reference = element.getReference();
      if (reference != null) {
        final PsiElement resolved = reference.resolve();
        if (resolved != null && ScopeUtil.getScopeOwner(resolved) == owner && !(owner instanceof PsiFile)) {
          result.add(element);
        }
      }
    }
    return result;
  }

  @NotNull
  private static Set<PsiElement> getSubGraphElements(@NotNull List<Instruction> subGraph) {
    final Set<PsiElement> result = new HashSet<>();
    for (Instruction instruction : subGraph) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        result.add(element);
      }
    }
    return result;
  }

  @NotNull
  private static Set<String> getGlobalWrites(@NotNull List<Instruction> instructions, @NotNull ScopeOwner owner) {
    final Scope scope = ControlFlowCache.getScope(owner);
    final Set<String> globalWrites = new LinkedHashSet<>();
    for (Instruction instruction : getWriteInstructions(instructions)) {
      if (instruction instanceof ReadWriteInstruction) {
        final String name = ((ReadWriteInstruction)instruction).getName();
        final PsiElement element = instruction.getElement();
        if (scope.isGlobal(name) ||
            (owner instanceof PsiFile && element instanceof PsiNamedElement && isUsedOutside((PsiNamedElement)element, instructions))) {
          globalWrites.add(name);
        }
      }
    }
    return globalWrites;
  }

  private static boolean isUsedOutside(@NotNull PsiNamedElement element, @NotNull List<Instruction> subGraph) {
    final Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
    return ContainerUtil.exists(PyRefactoringUtil.findUsages(element, false),
                                usageInfo -> !subGraphElements.contains(usageInfo.getElement()));
  }

  @NotNull
  private static Set<String> getNonlocalWrites(@NotNull List<Instruction> instructions, @NotNull ScopeOwner owner) {
    final Scope scope = ControlFlowCache.getScope(owner);
    final Set<String> nonlocalWrites = new LinkedHashSet<>();
    for (Instruction instruction : getWriteInstructions(instructions)) {
      if (instruction instanceof ReadWriteInstruction) {
        final String name = ((ReadWriteInstruction)instruction).getName();
        if (scope.isNonlocal(name)) {
          nonlocalWrites.add(name);
        }
      }
    }
    return nonlocalWrites;
  }

  @NotNull
  private static List<PsiElement> multiResolve(@NotNull PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      final List<PsiElement> resolved = new ArrayList<>();
      for (ResolveResult result : results) {
        final PsiElement element = result.getElement();
        if (element != null) {
          resolved.add(element);
        }
      }
      for (PsiElement element : resolved) {
        if (element instanceof PyClass) {
          return Collections.singletonList(element);
        }
      }
      return resolved;
    }
    final PsiElement element = reference.resolve();
    if (element != null) {
      return Collections.singletonList(element);
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<Instruction> getReadInstructions(@NotNull List<Instruction> subGraph) {
    final List<Instruction> result = new ArrayList<>();
    for (Instruction instruction : subGraph) {
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        if (readWriteInstruction.getAccess().isReadAccess()) {
          result.add(readWriteInstruction);
        }
      }
    }
    return result;
  }

  @NotNull
  private static List<Instruction> getWriteInstructions(@NotNull List<Instruction> subGraph) {
    final List<Instruction> result = new ArrayList<>();
    for (Instruction instruction : subGraph) {
      if (instruction instanceof ReadWriteInstruction) {
        final ReadWriteInstruction readWriteInstruction = (ReadWriteInstruction)instruction;
        if (readWriteInstruction.getAccess().isWriteAccess()) {
          result.add(readWriteInstruction);
        }
      }
    }
    return result;
  }
}