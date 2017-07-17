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

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyConstantExpressionEvaluator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class PyControlFlowBuilder extends PyRecursiveElementVisitor {
  private final ControlFlowBuilder myBuilder = new ControlFlowBuilder();

  public ControlFlow buildControlFlow(@NotNull final ScopeOwner owner) {
    return myBuilder.build(this, owner);
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
    final ReadWriteInstruction.ACCESS access = ReadWriteInstruction.ACCESS.WRITE;
    final QualifiedName qName = node.asQualifiedName();
    final String targetName = qName == null ? node.getName() : qName.toString();
    final ReadWriteInstruction instruction = ReadWriteInstruction.newInstruction(myBuilder, node, targetName, access);
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);

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

  private Instruction getPrevInstruction(final PyElement condition) {
    final Ref<Instruction> head = new Ref<>(myBuilder.prevInstruction);
    myBuilder.processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(condition, pendingScope, false)) {
        head.set(instruction);
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
    return head.get();
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
    final PyIfPart ifPart = node.getIfPart();
    PyExpression condition = ifPart.getCondition();
    PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
    if (condition != null) {
      condition.accept(this);
      condition.accept(assertionEvaluator);
    }
    // Set the head as the last instruction of condition
    PyElement lastCondition = condition;
    Instruction lastBranchingPoint = getPrevInstruction(condition);
    myBuilder.prevInstruction = lastBranchingPoint;
    final PyStatementList thenStatements = ifPart.getStatementList();
    myBuilder.startConditionalNode(thenStatements, condition, true);
    InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
    thenStatements.accept(this);
    myBuilder.processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(thenStatements, pendingScope, false)) {
        myBuilder.addPendingEdge(node, instruction);
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
    myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    for (final PyIfPart part : node.getElifParts()) {
      // Set the head as the false branch
      myBuilder.prevInstruction = lastBranchingPoint;
      myBuilder.startConditionalNode(part, lastCondition, false);
      condition = part.getCondition();
      assertionEvaluator = new PyTypeAssertionEvaluator();
      if (condition != null) {
        lastCondition = condition;
        lastBranchingPoint = getPrevInstruction(lastCondition);
        condition.accept(this);
        condition.accept(assertionEvaluator);
      }
      // Set the head as the last instruction of condition
      myBuilder.prevInstruction = getPrevInstruction(lastCondition);
      myBuilder.startConditionalNode(part, lastCondition, true);
      final PyStatementList statementList = part.getStatementList();
      InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
      statementList.accept(this);
      myBuilder.processPending((pendingScope, instruction) -> {
        if (pendingScope != null && PsiTreeUtil.isAncestor(part, pendingScope, false)) {
          myBuilder.addPendingEdge(node, instruction);
        }
        else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }
      });
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    final PyTypeAssertionEvaluator negativeAssertionEvaluator = new PyTypeAssertionEvaluator(false);
    final PyExpression ifCondition = ifPart.getCondition();
    // TODO: Add support for 'elif'
    if (ifCondition != null) {
      ifCondition.accept(negativeAssertionEvaluator);
    }
    final PyElsePart elseBranch = node.getElsePart();
    if (elseBranch != null) {
      // Set the head as the false branch
      myBuilder.prevInstruction = lastBranchingPoint;
      myBuilder.startConditionalNode(elseBranch, lastCondition, false);
      InstructionBuilder.addAssertInstructions(myBuilder, negativeAssertionEvaluator);
      elseBranch.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    else {
      myBuilder.prevInstruction = lastBranchingPoint;
      InstructionBuilder.addAssertInstructions(myBuilder, negativeAssertionEvaluator);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
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
      isStaticallyTrue = PyConstantExpressionEvaluator.evaluateBoolean(condition, false);
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
    myBuilder.prevInstruction = head;
    if (elsePart != null && !isStaticallyTrue) {
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
    if (elsePart == null) {
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
    final List<Instruction> exceptInstructions = emptyMutableList();
    List<Pair<PsiElement, Instruction>> pendingBackup = emptyMutableList();
    for (PyExceptPart exceptPart : node.getExceptParts()) {
      pendingBackup.addAll(myBuilder.pending);
      myBuilder.pending = emptyMutableList();
      myBuilder.flowAbrupted();
      final Instruction exceptInstruction = myBuilder.startNode(exceptPart);
      exceptPart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      exceptInstructions.add(exceptInstruction);
    }
    for (Pair<PsiElement, Instruction> pair : pendingBackup) {
      myBuilder.addPendingEdge(pair.first, pair.second);
    }

    final List<Instruction> normalExits = new ArrayList<>();
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
            normalExits.add(instruction);
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
    } else {
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

      // Duplicate CFG for finally (-fail and -success) only if there are some successfull exits from the
      // try part. Otherwise a single CFG for finally provides the correct control flow
      final Instruction finallyInstruction;
      if (!normalExits.isEmpty()) {
        // Finally-success part handling
        pendingBackup = emptyMutableList();
        pendingBackup.addAll(myBuilder.pending);
        myBuilder.pending = emptyMutableList();
        myBuilder.flowAbrupted();
        Instruction finallySuccessInstruction = myBuilder.startNode(finallyPart);
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
      for (Instruction instr : normalExits) {
        myBuilder.addEdge(instr, finallyInstruction);
      }
    }
  }

  private static <T> List<T> emptyMutableList() {
    return new ArrayList<>();
  }

  @Override
  public void visitPyComprehensionElement(final PyComprehensionElement node) {
    PyExpression prevCondition = null;
    myBuilder.startNode(node);
    List<Instruction> iterators = new ArrayList<>();

    for (PyComprehensionComponent component : node.getComponents()) {
      if (component instanceof PyComprehensionForComponent) {
        final PyComprehensionForComponent c = (PyComprehensionForComponent) component;
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
        final PyComprehensionIfComponent c = (PyComprehensionIfComponent) component;
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
    if (args.length >= 1 && !PyConstantExpressionEvaluator.evaluateBoolean(args[0], true)) {
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
    myBuilder.processPending((pendingScope, instruction) -> {
      final PsiElement element = instruction.getElement();
      if (element != null && PsiTreeUtil.isAncestor(node, element, true) &&
          PsiTreeUtil.getParentOfType(element, PyRaiseStatement.class) != null) {
        myBuilder.addPendingEdge(node, instruction);
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
  });
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

