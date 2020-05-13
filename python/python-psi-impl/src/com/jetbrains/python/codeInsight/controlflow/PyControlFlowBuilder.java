/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.controlflow;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import kotlin.Triple;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PyControlFlowBuilder extends PyRecursiveElementVisitor {

  @NotNull
  private static final Set<String> EXCEPTION_SUPPRESSORS = ImmutableSet.of("suppress", "assertRaises", "assertRaisesRegex");

  private final ControlFlowBuilder myBuilder = new ControlFlowBuilder();

  public ControlFlow buildControlFlow(@NotNull final ScopeOwner owner) {
    return myBuilder.build(this, owner);
  }

  @NotNull
  protected ControlFlowBuilder getBuilder() {
    return this.myBuilder;
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    // Create node and stop here
    myBuilder.startNode(node);
    visitParameterListExpressions(node.getParameterList());
    visitDecorators(node.getDecoratorList());
    final PyAnnotation annotation = node.getAnnotation();
    if (annotation != null) {
      annotation.accept(this);
    }

    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyDecoratorList(PyDecoratorList node) {
  }

  private void visitDecorators(PyDecoratorList list) {
    if (list != null) {
      for (PyDecorator decorator : list.getDecorators()) {
        decorator.accept(this);
      }
    }
  }

  private void visitParameterListExpressions(PyParameterList parameterList) {
    ParamHelper.walkDownParamArray(parameterList.getParameters(), new ParamHelper.ParamVisitor() {
      @Override
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        final PyExpression defaultValue = param.getDefaultValue();
        if (defaultValue != null) {
          defaultValue.accept(PyControlFlowBuilder.this);
        }
        final PyAnnotation annotation = param.getAnnotation();
        if (annotation != null) {
          annotation.accept(PyControlFlowBuilder.this);
        }
      }
    });
  }

  @Override
  public void visitPyClass(final PyClass node) {
    // Create node and stop here
    myBuilder.startNode(node);
    for (PsiElement element : node.getSuperClassExpressions()) {
      element.accept(this);
    }
    visitDecorators(node.getDecoratorList());
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyStatement(final PyStatement node) {
    myBuilder.startNode(node);
    super.visitPyStatement(node);
  }

  @Override
  public void visitPyElement(PyElement node) {
    if (node instanceof PsiNamedElement && !(node instanceof PyKeywordArgument)) {
      myBuilder.startNode(node);
      myBuilder.addNode(ReadWriteInstruction.newInstruction(myBuilder, node, node.getName(), ReadWriteInstruction.ACCESS.WRITE));
    }
    super.visitPyElement(node);
  }

  @Override
  public void visitPyCallExpression(final PyCallExpression node) {
    final PyExpression callee = node.getCallee();
    // Flow abrupted
    final String repr = PyUtil.getReadableRepr(callee, true);
    if (callee != null && ("sys.exit".equals(repr) ||
                           "self.fail".equals(repr))) {
      callee.accept(this);
      for (PyExpression expression : node.getArguments()) {
        expression.accept(this);
      }
      abruptFlow(node);
    }
    else {
      super.visitPyCallExpression(node);
    }
    if (node.isCalleeText(PyNames.ASSERT_IS_INSTANCE)) {
      final PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
      node.accept(assertionEvaluator);
      InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
    }
  }

  @Override
  public void visitPySubscriptionExpression(PySubscriptionExpression node) {
    myBuilder.startNode(node);
    node.getOperand().accept(this);
    final PyExpression expression = node.getIndexExpression();
    if (expression != null) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyReferenceExpression(final PyReferenceExpression node) {
    final PyExpression qualifier = node.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
      return;
    }
    if (PyImportStatementNavigator.getImportStatementByElement(node) != null) {
      return;
    }

    final ReadWriteInstruction.ACCESS access = PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null
                                               ? ReadWriteInstruction.ACCESS.READWRITE
                                               : ReadWriteInstruction.ACCESS.READ;
    final ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getName(), access);
    myBuilder.addNode(readWriteInstruction);
    myBuilder.checkPending(readWriteInstruction);
  }

  @Override
  public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
    final ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getText(),
                                                                                          ReadWriteInstruction.ACCESS.READ);
    myBuilder.addNode(readWriteInstruction);
    myBuilder.checkPending(readWriteInstruction);
  }

  @Override
  public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
    final ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getText(),
                                                                                          ReadWriteInstruction.ACCESS.READ);
    myBuilder.addNode(readWriteInstruction);
    myBuilder.checkPending(readWriteInstruction);
  }

  @Override
  public void visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node) {
    myBuilder.startNode(node);
    final PyAnnotation annotation = node.getAnnotation();
    if (annotation != null) {
      annotation.accept(this);
    }
    node.getTarget().accept(this);
  }

  @Override
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    myBuilder.startNode(node);
    final PyExpression value = node.getAssignedValue();
    if (value != null) {
      value.accept(this);
    }
    final PyAnnotation annotation = node.getAnnotation();
    if (annotation != null) {
      annotation.accept(this);
    }
    for (PyExpression expression : node.getRawTargets()) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyDelStatement(PyDelStatement node) {
    myBuilder.startNode(node);
    for (PyExpression target : node.getTargets()) {
      if (target instanceof PyReferenceExpression){
        PyReferenceExpression expr = (PyReferenceExpression)target;
        myBuilder.addNode(ReadWriteInstruction.newInstruction(myBuilder, target, expr.getName(), ReadWriteInstruction.ACCESS.DELETE));
        PyExpression qualifier = expr.getQualifier();
        if (qualifier != null) {
          qualifier.accept(this);
        }
      }
      else {
        target.accept(this);
      }
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
    myBuilder.startNode(node);
    final PyExpression value = node.getValue();
    if (value != null) {
      value.accept(this);
    }
    node.getTarget().accept(this);
  }

  @Override
  public void visitPyTargetExpression(final PyTargetExpression node) {
    final QualifiedName qName = node.asQualifiedName();
    if (qName != null) {
      final ReadWriteInstruction instruction = ReadWriteInstruction.newInstruction(myBuilder, node, qName.toString(),
                                                                                   ReadWriteInstruction.ACCESS.WRITE);
      myBuilder.addNode(instruction);
      myBuilder.checkPending(instruction);
    }

    final PyExpression qualifier = node.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
    }
  }

  @Override
  public void visitPyNamedParameter(final PyNamedParameter node) {
    final PyExpression defaultValue = node.getDefaultValue();
    if (defaultValue != null) {
      defaultValue.accept(this);
    }
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyImportStatement(final PyImportStatement node) {
    visitPyImportStatementBase(node);
  }

  @Override
  public void visitPyFromImportStatement(PyFromImportStatement node) {
    visitPyImportStatementBase(node);
    final PyStarImportElement starImportElement = node.getStarImportElement();
    if (starImportElement != null) {
      starImportElement.accept(this);
    }
  }

  @Override
  public void visitPyStarImportElement(PyStarImportElement node) {
    myBuilder.startNode(node);
  }

  private void visitPyImportStatementBase(PyImportStatementBase node) {
    myBuilder.startNode(node);
    for (PyImportElement importElement : node.getImportElements()) {
      final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, importElement, importElement.getVisibleName());
      myBuilder.addNode(instruction);
      myBuilder.checkPending(instruction);
    }
  }

  @NotNull
  private List<Pair<PsiElement, Instruction>> getPrevInstructions(@Nullable PyElement condition) {
    final List<Pair<PsiElement, Instruction>> result = ContainerUtil.newArrayList(Pair.create(condition, myBuilder.prevInstruction));
    myBuilder.processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(condition, pendingScope, false)) {
        result.add(Pair.create(pendingScope, instruction));
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
    return result;
  }

  @Override
  public void visitPyConditionalExpression(PyConditionalExpression node) {
    myBuilder.startNode(node);
    final PyExpression condition = node.getCondition();
    final PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
    if (condition != null) {
      condition.accept(this);
      condition.accept(assertionEvaluator);
    }
    final Instruction branchingPoint = myBuilder.prevInstruction;
    final PyExpression truePart = node.getTruePart();
    final PyExpression falsePart = node.getFalsePart();
    if (truePart != null) {
      InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
      truePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    if (falsePart != null) {
      myBuilder.prevInstruction = branchingPoint;
      falsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
  }

  @Override
  public void visitPyIfStatement(final PyIfStatement node) {
    myBuilder.startNode(node);

    PyExpression lastCondition = null; // last visited condition
    List<Pair<PsiElement, Instruction>> lastBranchingPoints = Collections.emptyList(); // outcoming edges from the last visited condition
    final List<Boolean> conditionResults = new ArrayList<>(); // visited conditions results

    final PyIfPart firstIfPart = node.getIfPart();

    for (PyIfPart part : StreamEx.of(firstIfPart).append(node.getElifParts())) {
      if (part != firstIfPart) {
        // first `if` could not be considered as some `if` inheritor
        if (!ContainerUtil.exists(conditionResults, Boolean.TRUE::equals)) {
          // edges to `if` would be created below if there were no conditions evaluated to `True` earlier
          lastBranchingPoints.forEach(pair -> myBuilder.addPendingEdge(pair.getFirst(), pair.getSecond()));
        }
        myBuilder.prevInstruction = null;

        myBuilder.startConditionalNode(part, lastCondition, false);
      }

      final Triple<PyExpression, List<Pair<PsiElement, Instruction>>, Boolean> currentPartResults = visitPyIfPart(part, node);
      lastCondition = currentPartResults.getFirst();
      lastBranchingPoints = currentPartResults.getSecond();
      conditionResults.add(currentPartResults.getThird());
    }

    final PyTypeAssertionEvaluator negativeAssertionEvaluator = new PyTypeAssertionEvaluator(false);
    final PyExpression firstIfPartCondition = firstIfPart.getCondition();
    // TODO: Add support for 'elif'
    if (firstIfPartCondition != null) {
      firstIfPartCondition.accept(negativeAssertionEvaluator);
    }

    final PyElsePart elseBranch = node.getElsePart();
    if (elseBranch != null) {
      if (!ContainerUtil.exists(conditionResults, Boolean.TRUE::equals)) {
        // edges to `else` would be created below if there were no conditions evaluated to `True` earlier
        lastBranchingPoints.forEach(pair -> myBuilder.addPendingEdge(pair.getFirst(), pair.getSecond()));
      }
      myBuilder.prevInstruction = null;

      final PyStatementList statements = elseBranch.getStatementList();

      myBuilder.startConditionalNode(statements, lastCondition, false);
      InstructionBuilder.addAssertInstructions(myBuilder, negativeAssertionEvaluator);
      statements.accept(this);

      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    else if (ContainerUtil.getLastItem(conditionResults) != Boolean.TRUE) {
      // if last condition was evaluated to `True`, all outcoming `if` statement edges were correctly processed in the last `if`

      myBuilder.prevInstruction = null;

      final Instruction instruction =
        ContainerUtil.getFirstItem(InstructionBuilder.addAssertInstructions(myBuilder, negativeAssertionEvaluator));
      if (instruction != null) {
        lastBranchingPoints.forEach(p -> myBuilder.addEdge(p.getSecond(), instruction));
      }
      else {
        lastBranchingPoints.forEach(pair -> myBuilder.addPendingEdge(pair.getFirst(), pair.getSecond()));
      }

      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
  }

  @NotNull
  private Triple<PyExpression, List<Pair<PsiElement, Instruction>>, Boolean> visitPyIfPart(@NotNull PyIfPart part,
                                                                                           @NotNull PyIfStatement node) {
    final PyExpression condition = part.getCondition();
    final PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
    final Boolean conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition);

    if (condition != null) {
      condition.accept(this);
      condition.accept(assertionEvaluator);
    }

    final List<Pair<PsiElement, Instruction>> branchingPoints = getPrevInstructions(condition);
    if (conditionResult != Boolean.FALSE) {
      // edges to the statement list under `if` would be created below if condition were not evaluated to `False`
      branchingPoints.forEach(pair -> myBuilder.addPendingEdge(pair.getFirst(), pair.getSecond()));
    }
    myBuilder.prevInstruction = null;

    visitPyIfPartStatements(part, assertionEvaluator, node);

    return new Triple<>(condition, branchingPoints, conditionResult);
  }

  private void visitPyIfPartStatements(@NotNull PyIfPart part,
                                       @NotNull PyTypeAssertionEvaluator assertionEvaluator,
                                       @NotNull PyIfStatement node) {
    final PyStatementList statements = part.getStatementList();

    myBuilder.startConditionalNode(statements, part.getCondition(), true);
    InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
    statements.accept(this);

    myBuilder.processPending(
      (pendingScope, instruction) -> {
        if (pendingScope != null && PsiTreeUtil.isAncestor(statements, pendingScope, false)) {
          myBuilder.addPendingEdge(node, instruction);
        }
        else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }
      }
    );

    myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
  }

  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    final PyElementType operator = node.getOperator();
    if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
      myBuilder.startNode(node);
      final PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator(operator == PyTokenTypes.AND_KEYWORD);

      final PyExpression left = node.getLeftExpression();
      if (left != null) {
        left.accept(this);
        left.accept(assertionEvaluator);
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      }

      final PyExpression right = node.getRightExpression();
      if (right != null) {
        InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
        right.accept(this);
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      }
    }
    else {
      super.visitPyBinaryExpression(node);
    }
  }

  @Override
  public void visitPyWhileStatement(final PyWhileStatement node) {
    final Instruction instruction = myBuilder.startNode(node);
    final PyWhilePart whilePart = node.getWhilePart();
    final PyExpression condition = whilePart.getCondition();
    boolean isStaticallyTrue = false;
    if (condition != null) {
      condition.accept(this);
      isStaticallyTrue = loopHasAtLeastOneIteration(node);
    }
    final Instruction head = myBuilder.prevInstruction;
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null && !isStaticallyTrue) {
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    final PyStatementList list = whilePart.getStatementList();
    myBuilder.startConditionalNode(list, condition, true);
    list.accept(this);
    // Loop edges
    if (myBuilder.prevInstruction != null) {
      myBuilder.addEdge(myBuilder.prevInstruction, instruction);
    }
    myBuilder.checkPending(instruction);

    if (elsePart != null) {
      myBuilder.prevInstruction = !isStaticallyTrue ? head : null;
      elsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }

    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyForStatement(final PyForStatement node) {
    myBuilder.startNode(node);
    final PyForPart forPart = node.getForPart();
    final PyExpression source = forPart.getSource();
    if (source != null) {
      source.accept(this);
    }
    final Instruction head = myBuilder.prevInstruction;
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null && !loopHasAtLeastOneIteration(node)) {
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    final PyStatementList list = forPart.getStatementList();
    final Instruction body;
    final PyExpression target = forPart.getTarget();
    if (target != null) {
      body = myBuilder.startNode(target);
      target.accept(this);
    }
    else {
      body = myBuilder.startNode(list);
    }
    list.accept(this);
    if (myBuilder.prevInstruction != null) {
      myBuilder.addEdge(myBuilder.prevInstruction, body);  //loop
      myBuilder.addPendingEdge(list, myBuilder.prevInstruction); // exit
    }
    myBuilder.processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(list, pendingScope, false)) {
        myBuilder.addEdge(instruction, body);  //loop
        myBuilder.addPendingEdge(list, instruction); // exit
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
    myBuilder.prevInstruction = head;
    if (elsePart != null) {
      elsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    myBuilder.flowAbrupted();
  }

  private static boolean loopHasAtLeastOneIteration(@NotNull PyLoopStatement loopStatement) {
    final PyExpression expression = loopStatement instanceof PyForStatement
                                    ? ((PyForStatement)loopStatement).getForPart().getSource()
                                    : loopStatement instanceof PyWhileStatement
                                      ? ((PyWhileStatement)loopStatement).getWhilePart().getCondition()
                                      : null;

    return PyEvaluator.evaluateAsBooleanNoResolve(expression, false);
  }

  @Override
  public void visitPyBreakStatement(final PyBreakStatement node) {
    myBuilder.startNode(node);
    final PyLoopStatement loop = node.getLoopStatement();
    if (loop != null) {
      myBuilder.addPendingEdge(loop, myBuilder.prevInstruction);
    }
    else {
      myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    }
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyContinueStatement(final PyContinueStatement node) {
    myBuilder.startNode(node);
    final PyLoopStatement loop = node.getLoopStatement();
    if (loop != null) {
      final Instruction instruction = myBuilder.findInstructionByElement(loop);
      if (instruction != null) {
        myBuilder.addEdge(myBuilder.prevInstruction, instruction);
      }
      else {
        myBuilder.addPendingEdge(null, null);
      }

      // There is no edge between loop statement and next after loop instruction
      // when loop has at least one iteration
      // so `continue` is marked as one more last instruction in the loop
      // see visitPyWhileStatement
      // see visitPyForStatement
      if (loopHasAtLeastOneIteration(loop)) {
        myBuilder.addPendingEdge(loop, myBuilder.prevInstruction);
      }
    }
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyYieldExpression(PyYieldExpression node) {
    myBuilder.startNode(node);
    final PyExpression expression = node.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyRaiseStatement(final PyRaiseStatement node) {
    myBuilder.startNode(node);
    final PyExpression[] expressions = node.getExpressions();
    for (PyExpression expression : expressions) {
      expression.accept(this);
    }

    myBuilder.processPending((pendingScope, instruction) -> {
      final PsiElement pendingElement = instruction.getElement();
      if (pendingElement != null && PsiTreeUtil.isAncestor(node, pendingElement, false)) {
        myBuilder.addEdge(null, instruction);
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyReturnStatement(final PyReturnStatement node) {
    myBuilder.startNode(node);
    final PyExpression expression = node.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
    abruptFlow(node);
  }

  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    myBuilder.startNode(node);

    // Process try part
    final PyTryPart tryPart = node.getTryPart();
    myBuilder.startNode(tryPart);
    tryPart.accept(this);

    // Goto else part after execution, or exit
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart != null) {
      myBuilder.startNode(elsePart);
      elsePart.accept(this);
    }
    myBuilder.addPendingEdge(node, myBuilder.prevInstruction);

    // Process except parts
    final List<Instruction> exceptInstructions = new ArrayList<>();
    List<Pair<PsiElement, Instruction>> pendingBackup = new ArrayList<>();
    for (PyExceptPart exceptPart : node.getExceptParts()) {
      pendingBackup.addAll(myBuilder.pending);
      myBuilder.pending = new ArrayList<>();
      myBuilder.flowAbrupted();
      final Instruction exceptInstruction = myBuilder.startNode(exceptPart);
      exceptPart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      exceptInstructions.add(exceptInstruction);
    }
    for (Pair<PsiElement, Instruction> pair : pendingBackup) {
      myBuilder.addPendingEdge(pair.first, pair.second);
    }

    final List<Pair<PsiElement, Instruction>> pendingNormalExits = new ArrayList<>();
    final PyFinallyPart finallyPart = node.getFinallyPart();
    final Instruction finallyFailInstruction;

    // Store pending normal exit instructions from try-except-else parts
    if (finallyPart != null) {
      myBuilder.processPending((pendingScope, instruction) -> {
        final PsiElement pendingElement = instruction.getElement();
        if (pendingElement != null) {
          final boolean isPending = PsiTreeUtil.isAncestor(node, pendingElement, false) &&
                                    !PsiTreeUtil.isAncestor(finallyPart, pendingElement, false);
          if (isPending && pendingScope != null) {
            pendingNormalExits.add(Pair.createNonNull(pendingScope, instruction));
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
    }

    // Finally-fail part handling
    if (finallyPart != null) {
      myBuilder.flowAbrupted();
      finallyFailInstruction = myBuilder.startNode(finallyPart);
      finallyPart.accept(this);
      myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
      myBuilder.flowAbrupted();
    }
    else {
      finallyFailInstruction = null;
    }

    // Create exception edges
    for (Instruction instruction : myBuilder.instructions) {
      final PsiElement e = instruction.getElement();
      if (e == null || !canRaiseExceptions(instruction)) {
        continue;
      }
      // All instructions inside the try part have edges to except and finally parts
      if (PsiTreeUtil.getParentOfType(e, PyTryPart.class, false) == tryPart) {
        for (Instruction inst : exceptInstructions) {
          myBuilder.addEdge(instruction, inst);
        }
        if (finallyPart != null) {
          myBuilder.addEdge(instruction, finallyFailInstruction);
        }
      }
      if (finallyPart != null) {
        // All instructions inside except parts have edges to the finally part
        for (PyExceptPart exceptPart : node.getExceptParts()) {
          if (PsiTreeUtil.isAncestor(exceptPart, e, false)) {
            myBuilder.addEdge(instruction, finallyFailInstruction);
          }
        }
        // All instructions inside the else part have edges to the finally part
        if (PsiTreeUtil.isAncestor(elsePart, e, false)) {
          myBuilder.addEdge(instruction, finallyFailInstruction);
        }
      }
    }

    if (finallyPart != null) {
      myBuilder.processPending((pendingScope, instruction) -> {
        final PsiElement e = instruction.getElement();
        if (e != null) {
          // Change the scope of pending edges from finally-fail part to point to the last instruction
          if (PsiTreeUtil.isAncestor(finallyPart, e, false)) {
            myBuilder.addPendingEdge(null, instruction);
          }
          // Connect pending fail edges to the finally-fail part
          else if (pendingScope == null && PsiTreeUtil.isAncestor(node, e, false)) {
            myBuilder.addEdge(instruction, finallyFailInstruction);
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });

      // Duplicate CFG for finally (-fail and -success) only if there are some successful exits from the
      // try part. Otherwise a single CFG for finally provides the correct control flow
      final Instruction finallyInstruction;
      if (!pendingNormalExits.isEmpty()) {
        // Finally-success part handling
        pendingBackup = new ArrayList<>(myBuilder.pending);
        myBuilder.pending = new ArrayList<>();
        myBuilder.flowAbrupted();
        final Instruction finallySuccessInstruction = myBuilder.startNode(finallyPart);
        finallyPart.accept(this);
        for (Pair<PsiElement, Instruction> pair : pendingBackup) {
          myBuilder.addPendingEdge(pair.first, pair.second);
        }
        finallyInstruction = finallySuccessInstruction;
      }
      else {
        finallyInstruction = finallyFailInstruction;
      }

      // Connect normal exits from try and else parts to the finally part
      for (Pair<PsiElement, Instruction> pendingScopeAndInstruction : pendingNormalExits) {
        final PsiElement pendingScope = pendingScopeAndInstruction.first;
        final Instruction instruction = pendingScopeAndInstruction.second;

        myBuilder.addEdge(instruction, finallyInstruction);

        // When instruction continues outside of try-except statement scope
        // the last instruction in finally-block is marked as pointing to that continuation
        if (PsiTreeUtil.isAncestor(pendingScope, node, true)) {
          myBuilder.addPendingEdge(pendingScope, myBuilder.prevInstruction);
        }
      }
    }
  }

  @Override
  public void visitPyComprehensionElement(final PyComprehensionElement node) {
    PyExpression prevCondition = null;
    myBuilder.startNode(node);
    List<Instruction> iterators = new ArrayList<>();

    for (PyComprehensionComponent component : node.getComponents()) {
      if (component instanceof PyComprehensionForComponent) {
        final PyComprehensionForComponent c = (PyComprehensionForComponent)component;
        final PyExpression iteratedList = c.getIteratedList();
        final PyExpression iteratorVariable = c.getIteratorVariable();
        if (prevCondition != null) {
          myBuilder.startConditionalNode(iteratedList, prevCondition, true);
          prevCondition = null;
        }
        else {
          myBuilder.startNode(iteratedList);
        }
        iteratedList.accept(this);

        // for-loop continue and exit
        for (Instruction i : iterators) {
          myBuilder.addEdge(myBuilder.prevInstruction, i);
        }
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction);

        final Instruction iterator = myBuilder.startNode(iteratorVariable);
        iteratorVariable.accept(this);

        // Inner "for" and "if" constructs will be linked to all outer iterators
        iterators.add(iterator);
      }
      else if (component instanceof PyComprehensionIfComponent) {
        final PyComprehensionIfComponent c = (PyComprehensionIfComponent)component;
        final PyExpression condition = c.getTest();
        if (condition == null) {
          continue;
        }
        if (prevCondition != null) {
          myBuilder.startConditionalNode(condition, prevCondition, true);
        }
        else {
          myBuilder.startNode(condition);
        }
        final PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
        condition.accept(this);
        condition.accept(assertionEvaluator);
        InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);

        // Condition is true for nested "for" and "if" constructs, next startNode() should create a conditional node
        prevCondition = condition;

        // for-loop continue and exit
        for (Instruction i : iterators) {
          myBuilder.addEdge(myBuilder.prevInstruction, i);
        }
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      }
    }

    final PyExpression result = node.getResultExpression();
    if (result != null) {
      if (prevCondition != null) {
        myBuilder.startConditionalNode(result, prevCondition, true);
      }
      else {
        myBuilder.startNode(result);
      }
      result.accept(this);

      // for-loop continue
      for (Instruction i : iterators) {
        myBuilder.addEdge(myBuilder.prevInstruction, i);
      }
    }
  }

  @Override
  public void visitPyAssertStatement(final PyAssertStatement node) {
    myBuilder.startNode(node);
    super.visitPyAssertStatement(node);
    final PyExpression[] args = node.getArguments();
    // assert False
    if (args.length >= 1 && !PyEvaluator.evaluateAsBooleanNoResolve(args[0], true)) {
      abruptFlow(node);
      return;
    }
    PyTypeAssertionEvaluator evaluator = new PyTypeAssertionEvaluator();
    node.acceptChildren(evaluator);
    InstructionBuilder.addAssertInstructions(myBuilder, evaluator);
  }

  @Override
  public void visitPyLambdaExpression(final PyLambdaExpression node) {
    myBuilder.startNode(node);
    visitParameterListExpressions(node.getParameterList());
  }

  @Override
  public void visitPyWithStatement(final PyWithStatement node) {
    super.visitPyWithStatement(node);

    final boolean suppressor = StreamEx
      .of(node.getWithItems())
      .map(PyWithItem::getExpression)
      .select(PyCallExpression.class)
      .map(PyCallExpression::getCallee)
      .select(PyReferenceExpression.class)
      .anyMatch(it -> EXCEPTION_SUPPRESSORS.contains(it.getReferencedName()));

    myBuilder.processPending((pendingScope, instruction) -> {
      final PsiElement element = instruction.getElement();
      if (element != null &&
          PsiTreeUtil.isAncestor(node, element, true) &&
          (suppressor && canRaiseExceptions(instruction) || PsiTreeUtil.getParentOfType(element, PyRaiseStatement.class) != null)) {
        myBuilder.addPendingEdge(node, instruction);
      }
      myBuilder.addPendingEdge(pendingScope, instruction);
    });
  }

  @Override
  public void visitPyAssignmentExpression(PyAssignmentExpression node) {
    final PyExpression assignedValue = node.getAssignedValue();
    if (assignedValue != null) assignedValue.accept(this);

    final PyTargetExpression target = node.getTarget();
    if (target != null) target.accept(this);
  }

  private void abruptFlow(final PsiElement node) {
    // Here we process pending instructions!!!
    myBuilder.processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
        myBuilder.addPendingEdge(null, instruction);
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  private static boolean canRaiseExceptions(final Instruction instruction) {
    if (instruction instanceof ReadWriteInstruction) {
      return true;
    }
    return !PsiTreeUtil.instanceOf(instruction.getElement(),
                                   PyStatementList.class);
  }
}

