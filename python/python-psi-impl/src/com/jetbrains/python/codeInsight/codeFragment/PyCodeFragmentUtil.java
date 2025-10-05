// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import com.intellij.codeInsight.codeFragment.CodeFragmentUtil;
import com.intellij.codeInsight.codeFragment.Position;
import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PyCodeFragmentUtil {
  private PyCodeFragmentUtil() {
  }

  public static @NotNull PyCodeFragment createCodeFragment(final @NotNull ScopeOwner owner,
                                                           final @NotNull PsiElement startInScope,
                                                           final @NotNull PsiElement endInScope,
                                                           final @Nullable PsiElement singleExpression)
    throws CannotCreateCodeFragmentException {

    final int start = startInScope.getTextOffset();
    final int end = endInScope.getTextOffset() + endInScope.getTextLength();
    final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
    final List<Instruction> graph = Arrays.asList(flow.getInstructions());
    final List<Instruction> subGraph = getFragmentSubGraph(graph, start, end);
    final AnalysisResult subGraphAnalysis = analyseSubGraph(subGraph, start, end);
    if ((subGraphAnalysis.regularExits > 0 && subGraphAnalysis.returns > 0) ||
        subGraphAnalysis.outerLoopBreaks > 0) {
      throw new CannotCreateCodeFragmentException(PyPsiBundle.message("refactoring.extract.method.error.interrupted.execution.flow"));
    }
    if (subGraphAnalysis.starImports > 0) {
      throw new CannotCreateCodeFragmentException(PyPsiBundle.message("refactoring.extract.method.error.star.import"));
    }

    final Set<String> globalWrites = getGlobalWrites(subGraph, owner);
    final Set<String> nonlocalWrites = getNonlocalWrites(subGraph, owner);

    final TypeEvalContext context = TypeEvalContext.userInitiated(startInScope.getProject(), startInScope.getContainingFile());
    final Set<String> inputNames = new LinkedHashSet<>();
    final Map<String, Pair<String, PyType>> inputTypes = new HashMap<>();
    for (PsiElement element : filterElementsInScope(getInputElements(subGraph, graph), owner)) {
      // Ignore "self" and "cls", they are generated automatically when extracting any method fragment
      if (resolvesToBoundMethodParameter(element)) {
        continue;
      }
      Pair<String, PyType> variable = getVariable(globalWrites, nonlocalWrites, element, inputNames, context);
      if (variable != null && variable.second != null) {
        String typeName = PythonDocumentationProvider.getTypeHint(variable.second, context);
        inputTypes.put(variable.first, Pair.create(typeName, variable.second));
      }
    }

    final Set<String> outputNames = new LinkedHashSet<>();
    final List<PyType> outputTypes = new ArrayList<>();
    for (PsiElement element : getOutputElements(subGraph, graph)) {
      Pair<String, PyType> variable = getVariable(globalWrites, nonlocalWrites, element, outputNames, context);
      if (variable != null) {
        outputTypes.add(variable.second);
      }
    }
    if (singleExpression != null) {
      PyType returnType = getType(singleExpression, context);
      outputTypes.add(returnType);
    }
    else if (PsiTreeUtil.getParentOfType(endInScope, PyStatement.class) instanceof PyReturnStatement returnStatement
             && returnStatement.getExpression() != null) {
      PyType returnType = getType(returnStatement.getExpression(), context);
      outputTypes.add(returnType);
    }
    final String outputTypeName = getOutputTypeName(startInScope, outputTypes, context);

    final boolean yieldsFound = subGraphAnalysis.yieldExpressions > 0;
    if (yieldsFound && LanguageLevel.forElement(owner).isPython2()) {
      throw new CannotCreateCodeFragmentException(PyPsiBundle.message("refactoring.extract.method.error.yield"));
    }
    final boolean isAsync = owner instanceof PyFunction && ((PyFunction)owner).isAsync();

    return new PyCodeFragment(inputNames, outputNames, inputTypes, outputTypeName, new LinkedHashSet<>(outputTypes),
                              globalWrites, nonlocalWrites, subGraphAnalysis.returns > 0, yieldsFound, isAsync);
  }

  private static @Nullable Pair<String, PyType> getVariable(@NotNull Set<String> globalWrites,
                                                            @NotNull Set<String> nonlocalWrites,
                                                            @NotNull PsiElement element,
                                                            @NotNull Set<String> variableNames,
                                                            @NotNull TypeEvalContext context) {
    String name = getName(element);
    if (name == null || globalWrites.contains(name) || nonlocalWrites.contains(name) || variableNames.contains(name)) {
      return null;
    }
    PyType type = getType(element, context);
    variableNames.add(name);
    return Pair.create(name, type);
  }

  private static @Nullable PyType getType(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyTypedElement typedElement) {
      PyType type = context.getType(typedElement);
      if (type != null && !(type instanceof PyStructuralType) && !PyNoneTypeKt.isNoneType(type)) {
        return type;
      }
    }
    return null;
  }

  private static @Nullable String getOutputTypeName(@NotNull PsiElement startInScope,
                                                    @NotNull List<PyType> outputTypes,
                                                    @NotNull TypeEvalContext context) {

    return switch (outputTypes.size()) {
      case 0 -> null;
      case 1 -> PythonDocumentationProvider.getTypeHint(outputTypes.get(0), context);
      default -> {
        PyType returnType = PyTupleType.create(startInScope, outputTypes);
        yield PythonDocumentationProvider.getTypeHint(returnType, context);
      }
    };
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
                  if (modifier == null || modifier == PyAstFunction.Modifier.CLASSMETHOD) {
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

  private static @Nullable String getName(@NotNull PsiElement element) {
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

  private static @NotNull List<Instruction> getFragmentSubGraph(@NotNull List<Instruction> graph, int start, int end) {
    List<Instruction> instructions = new ArrayList<>();
    for (Instruction instruction : graph) {
      final PsiElement element;
      if (instruction instanceof ConditionalInstruction conditionalInstruction) {
        element = conditionalInstruction.getCondition();
      }
      else {
        element = instruction.getElement();
      }
      if (element != null) {
        if (CodeFragmentUtil.getPosition(element, start, end) == Position.INSIDE) {
          instructions.add(instruction);
        }
      }
    }
    // Hack for including inner assert type instructions that can point to elements outside of the selected scope
    for (Instruction instruction : graph) {
      if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
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
    private final int regularExits;
    private final int returns;
    private final int outerLoopBreaks;
    private final int yieldExpressions;

    AnalysisResult(int starImports, int returns, int regularExits, int outerLoopBreaks, int yieldExpressions) {
      this.starImports = starImports;
      this.regularExits = regularExits;
      this.returns = returns;
      this.outerLoopBreaks = outerLoopBreaks;
      this.yieldExpressions = yieldExpressions;
    }
  }

  private static @NotNull AnalysisResult analyseSubGraph(@NotNull List<Instruction> subGraph, int start, int end) {
    int returnSources = 0;
    int regularSources = 0;
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

      if (returnStatement != null && CodeFragmentUtil.getPosition(returnStatement, start, end) == Position.INSIDE) {
        returnSources++;
      }
      else if (!isExceptTarget) {
        regularSources++;
      }
    }

    final Set<PsiElement> subGraphElements = getSubGraphElements(subGraph);
    for (PsiElement element : subGraphElements) {
      if (element instanceof PyFromImportStatement fromImportStatement) {
        if (fromImportStatement.getStarImportElement() != null) {
          starImports++;
        }
      }
      if (element instanceof PyContinueStatement || element instanceof PyBreakStatement) {
        final PyLoopStatement loopStatement = PyUtil.getCorrespondingLoop(element);
        if (loopStatement != null && !subGraphElements.contains(loopStatement)) {
          outerLoopBreaks++;
        }
      }
      if (element instanceof PyYieldExpression) {
        yieldExpressions++;
      }
    }

    return new AnalysisResult(starImports, returnSources, regularSources, outerLoopBreaks, yieldExpressions);
  }

  private static @NotNull Set<Pair<Instruction, Instruction>> getOutgoingEdges(@NotNull Collection<Instruction> subGraph) {
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

  public static @NotNull List<PsiElement> getInputElements(@NotNull List<Instruction> subGraph, @NotNull List<Instruction> graph) {
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

  private static @NotNull List<PsiElement> getOutputElements(@NotNull List<Instruction> subGraph, @NotNull List<Instruction> graph) {
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

  private static @NotNull List<PsiElement> filterElementsInScope(@NotNull Collection<PsiElement> elements, @NotNull ScopeOwner owner) {
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

  private static @NotNull Set<PsiElement> getSubGraphElements(@NotNull List<Instruction> subGraph) {
    final Set<PsiElement> result = new HashSet<>();
    for (Instruction instruction : subGraph) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        result.add(element);
      }
    }
    return result;
  }

  private static @NotNull Set<String> getGlobalWrites(@NotNull List<Instruction> instructions, @NotNull ScopeOwner owner) {
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
    return ContainerUtil.exists(PyPsiIndexUtil.findUsages(element, false),
                                usageInfo -> !subGraphElements.contains(usageInfo.getElement()));
  }

  private static @NotNull Set<String> getNonlocalWrites(@NotNull List<Instruction> instructions, @NotNull ScopeOwner owner) {
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

  private static @NotNull List<PsiElement> multiResolve(@NotNull PsiReference reference) {
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

  private static @NotNull List<Instruction> getReadInstructions(@NotNull List<Instruction> subGraph) {
    final List<Instruction> result = new ArrayList<>();
    for (Instruction instruction : subGraph) {
      if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
        if (readWriteInstruction.getAccess().isReadAccess()) {
          result.add(readWriteInstruction);
        }
      }
    }
    return result;
  }

  private static @NotNull List<Instruction> getWriteInstructions(@NotNull List<Instruction> subGraph) {
    final List<Instruction> result = new ArrayList<>();
    for (Instruction instruction : subGraph) {
      if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
        if (readWriteInstruction.getAccess().isWriteAccess()) {
          result.add(readWriteInstruction);
        }
      }
    }
    return result;
  }
}
