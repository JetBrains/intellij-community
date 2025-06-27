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

import com.google.common.collect.Lists;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.controlflow.TransparentInstruction;
import com.intellij.codeInsight.controlflow.impl.ConditionalInstructionImpl;
import com.intellij.codeInsight.controlflow.impl.TransparentInstructionImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.types.PyNeverType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyControlFlowBuilder extends PyRecursiveElementVisitor {

  private final ControlFlowBuilder myBuilder = new ControlFlowBuilder();

  private @Nullable TrueFalseNodes myTrueFalseNodes;
  
  // see com.jetbrains.python.PyPatternTypeTest.testMatchClassPatternShadowingCapture
  private final @NotNull List<String> myPatternBindingNames = new ArrayList<>();

  private record TrueFalseNodes(@NotNull Instruction trueNode, @NotNull Instruction falseNode) {}

  public ControlFlow buildControlFlow(final @NotNull ScopeOwner owner) {
    return myBuilder.build(this, owner);
  }

  protected @NotNull ControlFlowBuilder getBuilder() {
    return this.myBuilder;
  }


  @Override
  public void visitPyFunction(final @NotNull PyFunction node) {
    // Create node and stop here
    myBuilder.startNode(node);

    visitParameterListExpressions(node.getParameterList());
    visitDecorators(node.getDecoratorList());
    if (node.getTypeParameterList() == null) {
      final PyAnnotation annotation = node.getAnnotation();
      if (annotation != null) {
        annotation.acceptChildren(this);
      }
    }

    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNodeAndCheckPending(instruction);
  }

  @Override
  public void visitPyDecoratorList(@NotNull PyDecoratorList node) {
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
        if (parameterList.getParent() instanceof PyFunction function && function.getTypeParameterList() == null) {
          final PyAnnotation annotation = param.getAnnotation();
          if (annotation != null) {
            annotation.acceptChildren(PyControlFlowBuilder.this);
          }
        }
      }
    });
  }

  @Override
  public void visitPyClass(final @NotNull PyClass node) {
    // Create node and stop here
    myBuilder.startNode(node);

    for (PsiElement element : node.getSuperClassExpressions()) {
      element.accept(this);
    }
    visitDecorators(node.getDecoratorList());
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNodeAndCheckPending(instruction);
  }

  @Override
  public void visitPyStatement(final @NotNull PyStatement node) {
    myBuilder.startNode(node);
    super.visitPyStatement(node);
  }

  @Override
  public void visitPyElement(@NotNull PyElement node) {
    if (node instanceof PsiNamedElement && !(node instanceof PyKeywordArgument)) {
      myBuilder.startNode(node);
      myBuilder.addNode(ReadWriteInstruction.newInstruction(myBuilder, node, node.getName(), ReadWriteInstruction.ACCESS.WRITE));
    }
    super.visitPyElement(node);
  }

  @Override
  public void visitPyCallExpression(final @NotNull PyCallExpression node) {
    super.visitPyCallExpression(node);

    var callInstruction = new CallInstruction(myBuilder, node);
    myBuilder.addNodeAndCheckPending(callInstruction);

    if (node.isCalleeText(PyNames.ASSERT_IS_INSTANCE)) {
      addTypeAssertionNodes(node, true);
    }
  }

  @Override
  public void visitPySubscriptionExpression(@NotNull PySubscriptionExpression node) {
    myBuilder.startNode(node);
    node.getOperand().accept(this);
    final PyExpression expression = node.getIndexExpression();
    if (expression != null) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyReferenceExpression(final @NotNull PyReferenceExpression node) {
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
    myBuilder.addNodeAndCheckPending(readWriteInstruction);
  }

  @Override
  public void visitPyBoolLiteralExpression(@NotNull PyBoolLiteralExpression node) {
    final ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getText(),
                                                                                          ReadWriteInstruction.ACCESS.READ);
    myBuilder.addNodeAndCheckPending(readWriteInstruction);
  }

  @Override
  public void visitPyNoneLiteralExpression(@NotNull PyNoneLiteralExpression node) {
    final ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getText(),
                                                                                          ReadWriteInstruction.ACCESS.READ);
    myBuilder.addNodeAndCheckPending(readWriteInstruction);
  }

  @Override
  public void visitPyTypeDeclarationStatement(@NotNull PyTypeDeclarationStatement node) {
    myBuilder.startNode(node);
    final PyAnnotation annotation = node.getAnnotation();
    if (annotation != null) {
      annotation.accept(this);
    }
    node.getTarget().accept(this);
  }

  @Override
  public void visitPyAssignmentStatement(final @NotNull PyAssignmentStatement node) {
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
  public void visitPyDelStatement(@NotNull PyDelStatement node) {
    myBuilder.startNode(node);
    for (PyExpression target : node.getTargets()) {
      if (target instanceof PyReferenceExpression expr) {
        PyExpression qualifier = expr.getQualifier();
        if (qualifier != null) {
          qualifier.accept(this);
        }
        else {
          myBuilder.addNode(ReadWriteInstruction.newInstruction(myBuilder, target, expr.getName(), ReadWriteInstruction.ACCESS.DELETE));
        }
      }
      else {
        target.accept(this);
      }
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(final @NotNull PyAugAssignmentStatement node) {
    myBuilder.startNode(node);
    final PyExpression value = node.getValue();
    if (value != null) {
      value.accept(this);
    }
    node.getTarget().accept(this);
  }

  @Override
  public void visitPyTargetExpression(final @NotNull PyTargetExpression node) {
    final QualifiedName qName = node.asQualifiedName();
    if (qName != null) {
      final ReadWriteInstruction instruction = ReadWriteInstruction.newInstruction(myBuilder, node, qName.toString(),
                                                                                   ReadWriteInstruction.ACCESS.WRITE);
      myBuilder.addNodeAndCheckPending(instruction);
    }

    final PyExpression qualifier = node.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
    }
  }

  @Override
  public void visitPyNamedParameter(final @NotNull PyNamedParameter node) {
    PyAnnotation annotation = node.getAnnotation();
    if (annotation != null) {
      annotation.accept(this);
    }
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNodeAndCheckPending(instruction);
  }

  @Override
  public void visitPyAnnotation(@NotNull PyAnnotation node) {
    // Unless there is a type parameter list, return type and parameter annotations for functions are evaluated in their enclosing scope
    // and processed in visitPyFunction.
    // If there are type parameters, though, we need to put the corresponding instructions *inside* the function's scope to be able to
    // access them from annotations. 
    PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyStatement.class);
    if (function == null || function.getTypeParameterList() != null) {
      super.visitPyAnnotation(node);
    }
  }

  @Override
  public void visitPyImportStatement(final @NotNull PyImportStatement node) {
    visitPyImportStatementBase(node);
  }

  @Override
  public void visitPyFromImportStatement(@NotNull PyFromImportStatement node) {
    visitPyImportStatementBase(node);
    final PyStarImportElement starImportElement = node.getStarImportElement();
    if (starImportElement != null) {
      starImportElement.accept(this);
    }
  }

  @Override
  public void visitPyStarImportElement(@NotNull PyStarImportElement node) {
    myBuilder.startNode(node);
  }

  private void visitPyImportStatementBase(PyImportStatementBase node) {
    myBuilder.startNode(node);
    for (PyImportElement importElement : node.getImportElements()) {
      final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, importElement, importElement.getVisibleName());
      myBuilder.addNodeAndCheckPending(instruction);
    }
  }

  @Override
  public void visitPyConditionalExpression(@NotNull PyConditionalExpression node) {
    myBuilder.startNode(node);
    TransparentInstruction trueNode = addTransparentInstruction();
    TransparentInstruction falseNode = addTransparentInstruction();
    TransparentInstruction exitNode = addTransparentInstruction();
    final PyExpression condition = node.getCondition();
    if (condition != null) {
      visitCondition(condition, trueNode, falseNode);
    }
    final PyExpression truePart = node.getTruePart();
    final PyExpression falsePart = node.getFalsePart();
    if (truePart != null) {
      myBuilder.prevInstruction = trueNode;
      truePart.accept(this);
      myBuilder.addEdge(myBuilder.prevInstruction, exitNode);
    }
    if (falsePart != null) {
      myBuilder.prevInstruction = falseNode;
      falsePart.accept(this);
      myBuilder.addEdge(myBuilder.prevInstruction, exitNode);
    }
    myBuilder.prevInstruction = exitNode;
  }

  @Override
  public void visitPyMatchStatement(@NotNull PyMatchStatement matchStatement) {
    myBuilder.startNode(matchStatement);
    PyExpression subject = matchStatement.getSubject();
    String subjectName = PyTypeAssertionEvaluator.getAssertionTargetName(subject);
    if (subject != null) {
      subject.accept(this);
    }
    Instruction nextClause = myBuilder.prevInstruction;
    boolean unreachable = false;
    for (PyCaseClause clause : matchStatement.getCaseClauses()) {
      myBuilder.prevInstruction = nextClause;
      nextClause = addTransparentInstruction();
      
      myPatternBindingNames.clear();
      
      PyPattern pattern = clause.getPattern();
      if (pattern != null) {
        pattern.accept(this);
        if (!myPatternBindingNames.contains(subjectName)) {
          addTypeAssertionNodes(clause, true);
        }
      }
      
      PyExpression guard = clause.getGuardCondition();
      if (guard != null) {
        TransparentInstruction trueNode = addTransparentInstruction();
        visitCondition(guard, trueNode, nextClause);
        myBuilder.prevInstruction = trueNode;
        addTypeAssertionNodes(guard, true);
      }

      if (unreachable) {
        addAssertTypeNever();
      }
      if (pattern != null && pattern.isIrrefutable()) {
        unreachable = true;
      }
      myBuilder.startNode(clause.getStatementList());
      clause.getStatementList().accept(this);
      myBuilder.addPendingEdge(matchStatement, myBuilder.prevInstruction);
      myBuilder.updatePendingElementScope(clause.getStatementList(), matchStatement);
    }
    myBuilder.prevInstruction = nextClause;
    myBuilder.addNodeAndCheckPending(new TransparentInstructionImpl(myBuilder, matchStatement, ""));
    if (!myBuilder.prevInstruction.allPred().isEmpty()) {
      addTypeAssertionNodes(matchStatement, false);
    }
    myBuilder.addPendingEdge(matchStatement, myBuilder.prevInstruction);
    myBuilder.prevInstruction = null;
  }

  @Override
  public void visitPyPattern(@NotNull PyPattern node) {
    boolean isRefutable = !node.isIrrefutable();
    if (isRefutable) {
      myBuilder.addNodeAndCheckPending(new RefutablePatternInstruction(myBuilder, node, false));
    }
    else {
      myBuilder.startNode(node);
    }
    myBuilder.addPendingEdge(node.getParent(), myBuilder.prevInstruction);

    node.acceptChildren(this);
    myBuilder.updatePendingElementScope(node, node.getParent());

    if (isRefutable) {
      myBuilder.addNode(new RefutablePatternInstruction(myBuilder, node, true));
    }
  }

  @Override
  public void visitPyOrPattern(@NotNull PyOrPattern node) {
    myBuilder.addNodeAndCheckPending(new RefutablePatternInstruction(myBuilder, node, false));

    TransparentInstruction onSuccess = new TransparentInstructionImpl(myBuilder, node, "onSuccess");
    List<PyPattern> alternatives = node.getAlternatives();
    PyPattern lastAlternative = ContainerUtil.getLastItem(alternatives);

    for (PyPattern alternative : alternatives) {
      alternative.accept(this);
      if (alternative != lastAlternative) {
        // Allow next alternative to handle the fail edge of this alternative
        myBuilder.updatePendingElementScope(node, alternative);
      }
      myBuilder.addEdge(myBuilder.prevInstruction, onSuccess);
      myBuilder.prevInstruction = null;
    }
    myBuilder.addNode(onSuccess);
    myBuilder.addNode(new RefutablePatternInstruction(myBuilder, node, true));
    myBuilder.updatePendingElementScope(node, node.getParent());
  }

  @Override
  public void visitPyClassPattern(@NotNull PyClassPattern node) {
    myBuilder.addNodeAndCheckPending(new RefutablePatternInstruction(myBuilder, node, false));

    node.getClassNameReference().accept(this);
    myBuilder.addPendingEdge(node.getParent(), myBuilder.prevInstruction);

    node.getArgumentList().acceptChildren(this);
    myBuilder.updatePendingElementScope(node, node.getParent());

    myBuilder.addNode(new RefutablePatternInstruction(myBuilder, node, true));
  }

  @Override
  public void visitPyValuePattern(@NotNull PyValuePattern node) {
    myBuilder.addNodeAndCheckPending(new RefutablePatternInstruction(myBuilder, node, false));

    node.getValue().accept(this);
    myBuilder.addPendingEdge(node.getParent(), myBuilder.prevInstruction);

    myBuilder.addNode(new RefutablePatternInstruction(myBuilder, node, true));
  }

  @Override
  public void visitPyAsPattern(@NotNull PyAsPattern node) {
    // AsPattern can't fail by itself – it fails only if its child fails.
    // So no need to create an additional fail edge
    myBuilder.startNode(node);
    node.acceptChildren(this);
    if (node.getTarget() != null) {
      myPatternBindingNames.add(node.getTarget().getName());
    }
    myBuilder.updatePendingElementScope(node, node.getParent());
  }

  @Override
  public void visitPyCapturePattern(@NotNull PyCapturePattern node) {
    node.acceptChildren(this);
    // Although capture pattern is irrefutable, I add fail edge
    // here to add some connection to the next case clause.
    // Perhaps this can be reworked and simplified later.
    myBuilder.addPendingEdge(node.getParent(), myBuilder.prevInstruction);
    myPatternBindingNames.add(node.getTarget().getName());
  }

  @Override
  public void visitPyGroupPattern(@NotNull PyGroupPattern node) {
    // GroupPattern can't fail by itself – it fails only if its child fails.
    // So no need to create an additional fail edge 
    // Also no need for a dedicated node for GroupPattern itself
    node.acceptChildren(this);
    myBuilder.updatePendingElementScope(node, node.getParent());
  }

  @Override
  public void visitPyIfStatement(final @NotNull PyIfStatement node) {
    myBuilder.startNode(node);

    List<Instruction> exitInstructions = new ArrayList<>();
    boolean unreachable = false;
    for (PyIfPart ifPart : StreamEx.of(node.getIfPart()).append(node.getElifParts())) {
      TransparentInstruction thenNode = addTransparentInstruction();
      TransparentInstruction elseNode = addTransparentInstruction();
      PyExpression condition = ifPart.getCondition();
      if (condition != null) {
        visitCondition(condition, thenNode, elseNode);
      }
      myBuilder.prevInstruction = thenNode;

      Boolean conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition);
      if (unreachable || Boolean.FALSE.equals(conditionResult)) {
        // Condition is always False, or some previous condition is always True.
        addAssertTypeNever();
      }
      if (Boolean.TRUE.equals(conditionResult)) {
        unreachable = true;
      }
      visitPyStatementPart(ifPart);

      exitInstructions.add(myBuilder.prevInstruction);
      myBuilder.prevInstruction = elseNode;
    }

    final PyElsePart elsePart = node.getElsePart();
    if (elsePart != null) {
      if (unreachable) {
        addAssertTypeNever();
      }
      visitPyStatementPart(elsePart);
    }

    exitInstructions.add(myBuilder.prevInstruction);
    myBuilder.prevInstruction = addTransparentInstruction(node);

    for (Instruction exitInstruction : Lists.reverse(exitInstructions)) {
      myBuilder.addEdge(exitInstruction, myBuilder.prevInstruction);
    }
  }

  @Override
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
    if (myTrueFalseNodes != null && node.getOperator() == PyTokenTypes.NOT_KEYWORD) {
      PyExpression operand = node.getOperand();
      if (operand != null) {
        visitCondition(operand, myTrueFalseNodes.falseNode, myTrueFalseNodes.trueNode);
      }
    }
    else {
      super.visitPyPrefixExpression(node);
    }
  }

  @Override
  public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
    PyElementType operator = node.getOperator();
    if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
      final PyExpression left = node.getLeftExpression();
      final PyExpression right = node.getRightExpression();
      if (left == null || right == null) return;

      myBuilder.startNode(node);

      Instruction trueNode;
      Instruction falseNode;
      Instruction exitNode;
      if (myTrueFalseNodes == null) {
        trueNode = falseNode = exitNode = addTransparentInstruction();
      }
      else {
        trueNode = myTrueFalseNodes.trueNode;
        falseNode = myTrueFalseNodes.falseNode;
        exitNode = null;
      }

      Instruction rightNode = addTransparentInstruction();
      if (operator == PyTokenTypes.AND_KEYWORD) {
        visitCondition(left, rightNode, falseNode);
      }
      else {
        visitCondition(left, trueNode, rightNode);
      }

      myBuilder.prevInstruction = rightNode;
      visitCondition(right, trueNode, falseNode);

      if (exitNode != null) {
        myBuilder.prevInstruction = exitNode;
      }
    }
    else {
      super.visitPyBinaryExpression(node);
    }
  }

  @Override
  public void visitPyWhileStatement(final @NotNull PyWhileStatement node) {
    Instruction entryNode = myBuilder.startNode(node);

    TransparentInstruction thenNode = addTransparentInstruction();
    TransparentInstruction elseNode = addTransparentInstruction();

    final PyConditionalStatementPart whilePart = node.getWhilePart();
    final PyExpression condition = whilePart.getCondition();
    if (condition != null) {
      visitCondition(condition, thenNode, elseNode);
    }

    final Boolean conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition);

    myBuilder.prevInstruction = Boolean.FALSE.equals(conditionResult) ? null : thenNode;
    visitPyStatementPart(whilePart);

    myBuilder.checkPending(entryNode);
    myBuilder.addEdge(myBuilder.prevInstruction, entryNode);

    myBuilder.prevInstruction = Boolean.TRUE.equals(conditionResult) ? null : elseNode;
    PyElsePart elsePart = node.getElsePart();
    if (elsePart != null) {
      visitPyStatementPart(elsePart);
    }

    collectInternalPendingEdges(node);
  }

  @Override
  public void visitPyForStatement(final @NotNull PyForStatement node) {
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
    collectInternalPendingEdges(node);
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
  public void visitPyBreakStatement(final @NotNull PyBreakStatement node) {
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
  public void visitPyContinueStatement(final @NotNull PyContinueStatement node) {
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
  public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
    myBuilder.startNode(node);
    final PyExpression expression = node.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyRaiseStatement(final @NotNull PyRaiseStatement node) {
    myBuilder.startNode(node);
    final PyExpression[] expressions = node.getExpressions();
    for (PyExpression expression : expressions) {
      expression.accept(this);
    }
    myBuilder.addNode(new PyRaiseInstruction(myBuilder, node));
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyReturnStatement(final @NotNull PyReturnStatement node) {
    myBuilder.startNode(node);
    final PyExpression expression = node.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyTryExceptStatement(final @NotNull PyTryExceptStatement node) {
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
      myBuilder.addNodeAndCheckPending(new PyFinallyFailExitInstruction(myBuilder, finallyFailInstruction));
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
      // try part. Otherwise, a single CFG for finally provides the correct control flow
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

        // When instruction continues outside try-except statement scope
        // the last instruction in finally-block is marked as pointing to that continuation
        if (PsiTreeUtil.isAncestor(pendingScope, node, true)) {
          myBuilder.addPendingEdge(pendingScope, myBuilder.prevInstruction);
        }
      }
    }

    collectInternalPendingEdges(node);
  }

  @Override
  public void visitPyComprehensionElement(final @NotNull PyComprehensionElement node) {
    PyExpression prevCondition = null;
    myBuilder.startNode(node);
    List<Instruction> iterators = new ArrayList<>();

    for (PyComprehensionComponent component : node.getComponents()) {
      if (component instanceof PyComprehensionForComponent c) {
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
      else if (component instanceof PyComprehensionIfComponent c) {
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
        condition.accept(this);
        addTypeAssertionNodes(condition, true);

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
    
    collectInternalPendingEdges(node);
  }

  @Override
  public void visitPyAssertStatement(final @NotNull PyAssertStatement node) {
    myBuilder.startNode(node);
    final PyExpression[] args = node.getArguments();
    for (PyExpression arg : args) {
      arg.accept(this);
    }
    // assert False
    if (args.length >= 1) {
      if (!PyEvaluator.evaluateAsBooleanNoResolve(args[0], true)) {
        myBuilder.addNode(new PyRaiseInstruction(myBuilder, node));
        myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
        myBuilder.flowAbrupted();
        return;
      }

      TransparentInstruction trueNode = addTransparentInstruction(node);
      TransparentInstruction falseNode = addTransparentInstruction(node);
      visitCondition(args[0], trueNode, falseNode);

      PyRaiseInstruction raiseInstruction = new PyRaiseInstruction(myBuilder, node);
      myBuilder.instructions.add(raiseInstruction);
      myBuilder.addEdge(falseNode, raiseInstruction);

      myBuilder.addPendingEdge(null, raiseInstruction);
      myBuilder.prevInstruction = trueNode;
    }
  }

  @Override
  public void visitPyLambdaExpression(final @NotNull PyLambdaExpression node) {
    myBuilder.startNode(node);
    visitParameterListExpressions(node.getParameterList());
  }

  @Override
  public void visitPyWithStatement(final @NotNull PyWithStatement node) {
    myBuilder.startNode(node);

    List<Instruction> exits = new ArrayList<>();
    for (var item : node.getWithItems()) {
      int itemStart = myBuilder.instructions.size();
      item.accept(this);
      int itemEnd = myBuilder.instructions.size();
      for (int i = itemStart; i < itemEnd; i++) {
        final Instruction instruction = myBuilder.instructions.get(i);
        final PsiElement e = instruction.getElement();
        if (e == null || !canRaiseExceptions(instruction) || !PsiTreeUtil.isAncestor(node, e, false)) {
          continue;
        }
        for (var exit : exits) {
          myBuilder.addEdge(myBuilder.instructions.get(i), exit);
        }
      }

      var nextExit = new PyWithContextExitInstruction(myBuilder, item);
      exits.add(nextExit);
      myBuilder.instructions.add(nextExit);
      // ControlFlowUtil.iterate assumes nodes are added to CFG in order they are created
    }

    final var toAllExits = addTransparentInstruction();
    final var fromAllExits = addTransparentInstruction();
    for (var exit : exits) {
      myBuilder.addEdge(toAllExits, exit);
      myBuilder.addEdge(exit, fromAllExits);
    }

    int stmtStart = myBuilder.instructions.size();
    node.getStatementList().accept(this);
    int stmtEnd = myBuilder.instructions.size();

    for (int j = stmtStart; j < stmtEnd; j++) {
      final Instruction instruction = myBuilder.instructions.get(j);
      final PsiElement e = instruction.getElement();
      if (e == null || !canRaiseExceptions(instruction) || !PsiTreeUtil.isAncestor(node, e, false)) {
        continue;
      }
      myBuilder.addEdge(instruction, toAllExits);
    }

    // Checks if exit nodes will have at least one predecessor
    if (exits.size() > 1 || !toAllExits.allPred().isEmpty()) {
      myBuilder.addPendingEdge(node, fromAllExits);
    }
  }

  @Override
  public void visitPyAssignmentExpression(@NotNull PyAssignmentExpression node) {
    final PyExpression assignedValue = node.getAssignedValue();
    if (assignedValue != null) assignedValue.accept(this);

    final PyTargetExpression target = node.getTarget();
    if (target != null) target.accept(this);
  }

  @Override
  public void visitPyTypeAliasStatement(@NotNull PyTypeAliasStatement node) {
    myBuilder.startNode(node);

    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNodeAndCheckPending(instruction);
  }

  @Override
  public void visitPyTypeParameterList(@NotNull PyTypeParameterList node) { }

  private void visitCondition(@NotNull PyExpression expression, @NotNull Instruction trueNode, @NotNull Instruction falseNode) {
    TrueFalseNodes prevTrueFalseNodes = myTrueFalseNodes;
    myTrueFalseNodes = new TrueFalseNodes(trueNode, falseNode);
    expression.accept(this);
    myTrueFalseNodes = prevTrueFalseNodes;

    final PyExpression condition = PyPsiUtils.flattenParens(expression);
    if (condition != null && !isLogicalExpression(condition)) {
      addConditionalNode(expression, false, falseNode);
      addConditionalNode(expression, true, trueNode);
    }
  }

  private static boolean isLogicalExpression(@NotNull PyExpression expression) {
    if (expression instanceof PyBinaryExpression binaryExpression) {
      PyElementType operator = binaryExpression.getOperator();
      return operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD;
    }
    if (expression instanceof PyPrefixExpression prefixExpression) {
      return prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD;
    }
    return false;
  }

  private void addConditionalNode(@NotNull PyExpression condition, boolean result, @NotNull Instruction target) {
    Instruction prevInstruction = myBuilder.prevInstruction;
    myBuilder.addNode(new ConditionalInstructionImpl(myBuilder, null, condition, result));
    addTypeAssertionNodes(condition, result);
    myBuilder.addEdge(myBuilder.prevInstruction, target);
    myBuilder.prevInstruction = prevInstruction;
  }

  private void visitPyStatementPart(@NotNull PyStatementPart statementPart) {
    PyStatementList statementList = statementPart.getStatementList();
    myBuilder.startNode(statementList);
    statementList.accept(this);
  }

  private static boolean canRaiseExceptions(@NotNull Instruction instruction) {
    if (instruction instanceof ReadWriteInstruction) {
      return true;
    }
    PsiElement element = instruction.getElement();
    return !(element instanceof PyReturnStatement returnStatement && returnStatement.getExpression() == null
             || element instanceof PyContinueStatement
             || element instanceof PyBreakStatement
             || element instanceof PyPassStatement
             || element instanceof PyStatementList);
  }

  private void addTypeAssertionNodes(@NotNull PyElement condition, boolean positive) {
    final PyTypeAssertionEvaluator evaluator = new PyTypeAssertionEvaluator(positive);
    condition.accept(evaluator);
    for (PyTypeAssertionEvaluator.Assertion def : evaluator.getDefinitions()) {
      final PyQualifiedExpression e = def.getElement();
      String name = null;
      if (e != null) {
        final QualifiedName qname = e.asQualifiedName();
        name = qname != null ? qname.toString() : e.getName();
      }
      myBuilder.addNode(ReadWriteInstruction.assertType(myBuilder, e, name, def.getTypeEvalFunction()));
    }
  }

  private TransparentInstruction addTransparentInstruction() {
    return addTransparentInstruction(null);
  }
  
  private TransparentInstruction addTransparentInstruction(@Nullable PsiElement element) {
    TransparentInstructionImpl instruction = new TransparentInstructionImpl(myBuilder, element, "");
    myBuilder.instructions.add(instruction);
    return instruction;
  }

  /**
   * Can be used to mark a branch as unreachable.
   */
  private void addAssertTypeNever() {
    myBuilder.addNode(ReadWriteInstruction.assertType(myBuilder, null, null, context -> Ref.create(PyNeverType.NEVER)));
  }

  /**
   * Can be used to collect all pending edges  
   * that we used to build CFG for `node`,
   * but are not relevant to other elements.
   * Is almost equivalent to this:
   * 
   * <pre>{@code
   * visitPy...(node);
   * myBuilder.startNode(node.nextSibling); // collectInternalPendingEdges does this, without needing nextSibling
   * }</pre>
   */
  private void collectInternalPendingEdges(@NotNull PyElement node) {
    myBuilder.addNode(new TransparentInstructionImpl(myBuilder, node, "")); // exit
    myBuilder.processPending((pendingScope, instruction) -> {
      if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
        myBuilder.addEdge(instruction, myBuilder.prevInstruction); // to exit
      }
      else {
        myBuilder.addPendingEdge(pendingScope, instruction);
      }
    });
  }
}
