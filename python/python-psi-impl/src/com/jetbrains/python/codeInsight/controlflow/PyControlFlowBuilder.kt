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
package com.jetbrains.python.codeInsight.controlflow

import com.google.common.collect.Lists
import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInsight.controlflow.TransparentInstruction
import com.intellij.codeInsight.controlflow.impl.ConditionalInstructionImpl
import com.intellij.codeInsight.controlflow.impl.TransparentInstructionImpl
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyAsPattern
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyAssignmentExpression
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyAugAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyBoolLiteralExpression
import com.jetbrains.python.psi.PyBreakStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCapturePattern
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyComprehensionElement
import com.jetbrains.python.psi.PyComprehensionForComponent
import com.jetbrains.python.psi.PyComprehensionIfComponent
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyConditionalStatementPart
import com.jetbrains.python.psi.PyContinueStatement
import com.jetbrains.python.psi.PyDecoratorList
import com.jetbrains.python.psi.PyDelStatement
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyForStatement
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyGroupPattern
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyLoopStatement
import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyNoneLiteralExpression
import com.jetbrains.python.psi.PyOrPattern
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyPassStatement
import com.jetbrains.python.psi.PyPattern
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PyStarImportElement
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.PyStatementPart
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTryExceptStatement
import com.jetbrains.python.psi.PyTryPart
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTupleParameter
import com.jetbrains.python.psi.PyTypeAliasStatement
import com.jetbrains.python.psi.PyTypeDeclarationStatement
import com.jetbrains.python.psi.PyTypeParameterList
import com.jetbrains.python.psi.PyValuePattern
import com.jetbrains.python.psi.PyWhileStatement
import com.jetbrains.python.psi.PyWithStatement
import com.jetbrains.python.psi.PyYieldExpression
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyImportStatementNavigator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isUnknown
import one.util.streamex.StreamEx

open class PyControlFlowBuilder(private val myLanguageLevel: LanguageLevel?) : PyRecursiveElementVisitor() {
  protected val builder: ControlFlowBuilder = ControlFlowBuilder()

  private var myTrueFalseNodes: TrueFalseNodes? = null

  // see com.jetbrains.python.PyPatternTypeTest.testMatchClassPatternShadowingCapture
  private val myPatternBindingNames: MutableList<String> = ArrayList()

  @JvmRecord
  private data class TrueFalseNodes(val trueNode: Instruction, val falseNode: Instruction)

  fun buildControlFlow(owner: ScopeOwner): PyControlFlow {
    val flow = builder.build(this, owner)
    val instructions = flow.instructions
    for (i in instructions.indices) {
      check(i == instructions[i]!!.num())
    }
    return PyControlFlow(instructions)
  }


  override fun visitPyFunction(node: PyFunction) {
    // Create node and stop here
    builder.startNode(node)

    visitParameterListExpressions(node.parameterList)
    visitDecorators(node.decoratorList)
    if (node.typeParameterList == null) {
      val annotation = node.annotation
      annotation?.acceptChildren(this)
    }

    val instruction = ReadWriteInstruction.write(
      this.builder, node, node.name
    )
    builder.addNodeAndCheckPending(instruction)
  }

  override fun visitPyDecoratorList(node: PyDecoratorList) {
  }

  private fun visitDecorators(list: PyDecoratorList?) {
    if (list != null) {
      for (decorator in list.decorators) {
        decorator.accept(this)
      }
    }
  }

  private fun visitParameterListExpressions(parameterList: PyParameterList) {
    val owner = parameterList.parent
    val visitAnnotations = owner is PyFunction && owner.typeParameterList == null
    visitNamedParameterExpressions(parameterList.parameters, visitAnnotations)
  }

  private fun visitNamedParameterExpressions(parameters: Array<out PyParameter>, visitAnnotations: Boolean) {
    for (parameter in parameters) {
      if (parameter is PyTupleParameter) {
        visitNamedParameterExpressions(parameter.contents, visitAnnotations)
      }
      else if (parameter is PyNamedParameter) {
        val defaultValue = parameter.defaultValue
        defaultValue?.accept(this)
        if (visitAnnotations) {
          val annotation = parameter.annotation
          annotation?.acceptChildren(this)
        }
      }
    }
  }

  override fun visitPyClass(node: PyClass) {
    // Create node and stop here
    builder.startNode(node)

    for (element in node.superClassExpressions) {
      element.accept(this)
    }
    visitDecorators(node.decoratorList)
    val instruction = ReadWriteInstruction.write(
      this.builder, node, node.name
    )
    builder.addNodeAndCheckPending(instruction)
  }

  override fun visitPyStatement(node: PyStatement) {
    builder.startNode(node)
    super.visitPyStatement(node)
  }

  override fun visitPyElement(node: PyElement) {
    if (node is PsiNamedElement && node !is PyKeywordArgument) {
      builder.startNode(node)
      builder.addNode(ReadWriteInstruction.newInstruction(this.builder, node, node.name, ReadWriteInstruction.ACCESS.WRITE))
    }
    super.visitPyElement(node)
  }

  override fun visitPyCallExpression(node: PyCallExpression) {
    super.visitPyCallExpression(node)

    val callInstruction = CallInstruction(
      this.builder, node
    )
    builder.addNodeAndCheckPending(callInstruction)

    if (node.isCalleeText(PyNames.ASSERT_IS_INSTANCE)) {
      addTypeAssertionNodes(node, true)
    }
  }

  override fun visitPySubscriptionExpression(node: PySubscriptionExpression) {
    builder.startNode(node)
    node.operand.accept(this)
    val expression = node.indexExpression
    expression?.accept(this)
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    val qualifier = node.qualifier
    if (qualifier != null) {
      qualifier.accept(this)
      return
    }
    if (PyImportStatementNavigator.getImportStatementByElement(node) != null) {
      return
    }

    val readWriteInstruction: ReadWriteInstruction
    if (PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null) {
      readWriteInstruction = ReadWriteInstruction.readWrite(this.builder, node, getName(node), augAssignmentTypeCallback(node))
    }
    else {
      readWriteInstruction = ReadWriteInstruction.newInstruction(this.builder, node, getName(node), ReadWriteInstruction.ACCESS.READ)
    }
    builder.addNodeAndCheckPending(readWriteInstruction)
  }

  override fun visitPyBoolLiteralExpression(node: PyBoolLiteralExpression) {
    val readWriteInstruction = ReadWriteInstruction.newInstruction(
      this.builder, node, node.text,
      ReadWriteInstruction.ACCESS.READ
    )
    builder.addNodeAndCheckPending(readWriteInstruction)
  }

  override fun visitPyNoneLiteralExpression(node: PyNoneLiteralExpression) {
    val readWriteInstruction = ReadWriteInstruction.newInstruction(
      this.builder, node, node.text,
      ReadWriteInstruction.ACCESS.READ
    )
    builder.addNodeAndCheckPending(readWriteInstruction)
  }

  override fun visitPyTypeDeclarationStatement(node: PyTypeDeclarationStatement) {
    builder.startNode(node)
    val annotation: PyAnnotation? = node.annotation
    annotation?.accept(this)
    node.target.accept(this)
  }

  override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
    builder.startNode(node)
    val value = node.assignedValue
    value?.accept(this)
    val annotation: PyAnnotation? = node.annotation
    annotation?.accept(this)
    for (expression in node.rawTargets) {
      expression.accept(this)
    }
  }

  override fun visitPyDelStatement(node: PyDelStatement) {
    builder.startNode(node)
    for (target in node.targets) {
      if (target is PyReferenceExpression) {
        val qualifier = target.qualifier
        if (qualifier != null) {
          qualifier.accept(this)
        }
        else {
          builder.addNode(
            ReadWriteInstruction.newInstruction(
              this.builder,
              target,
              target.name,
              ReadWriteInstruction.ACCESS.DELETE
            )
          )
        }
      }
      else {
        target.accept(this)
      }
    }
  }

  override fun visitPyAugAssignmentStatement(node: PyAugAssignmentStatement) {
    builder.startNode(node)
    val value = node.value
    value?.accept(this)
    node.target.accept(this)
  }

  override fun visitPyTargetExpression(node: PyTargetExpression) {
    val qName = node.asQualifiedName()
    if (qName != null) {
      val instruction = ReadWriteInstruction.newInstruction(
        this.builder, node, qName.toString(),
        ReadWriteInstruction.ACCESS.WRITE
      )
      builder.addNodeAndCheckPending(instruction)
    }

    val qualifier = node.qualifier
    qualifier?.accept(this)
  }

  override fun visitPyNamedParameter(node: PyNamedParameter) {
    val annotation = node.annotation
    annotation?.accept(this)
    val instruction = ReadWriteInstruction.write(
      this.builder, node, node.name
    )
    builder.addNodeAndCheckPending(instruction)
  }

  override fun visitPyAnnotation(node: PyAnnotation) {
    // Unless there is a type parameter list, return type and parameter annotations for functions are evaluated in their enclosing scope
    // and processed in visitPyFunction.
    // If there are type parameters, though, we need to put the corresponding instructions *inside* the function's scope to be able to
    // access them from annotations.
    val function = PsiTreeUtil.getParentOfType(node, PyFunction::class.java, true, PyStatement::class.java)
    if (function == null || function.typeParameterList != null) {
      super.visitPyAnnotation(node)
    }
  }

  override fun visitPyImportStatement(node: PyImportStatement) {
    visitPyImportStatementBase(node)
  }

  override fun visitPyFromImportStatement(node: PyFromImportStatement) {
    visitPyImportStatementBase(node)
    val starImportElement = node.starImportElement
    starImportElement?.accept(this)
  }

  override fun visitPyStarImportElement(node: PyStarImportElement) {
    builder.startNode(node)
  }

  private fun visitPyImportStatementBase(node: PyImportStatementBase) {
    builder.startNode(node)
    for (importElement in node.importElements) {
      val instruction = ReadWriteInstruction.write(
        this.builder, importElement, importElement.visibleName
      )
      builder.addNodeAndCheckPending(instruction)
    }
  }

  override fun visitPyConditionalExpression(node: PyConditionalExpression) {
    builder.startNode(node)
    val trueNode = addTransparentInstruction()
    val falseNode = addTransparentInstruction()
    val exitNode = addTransparentInstruction()
    val condition = node.condition
    if (condition != null) {
      visitCondition(condition, trueNode, falseNode)
    }
    val truePart = node.truePart
    val falsePart = node.falsePart
    if (truePart != null) {
      builder.prevInstruction = trueNode
      truePart.accept(this)
      builder.addEdge(builder.prevInstruction, exitNode)
    }
    if (falsePart != null) {
      builder.prevInstruction = falseNode
      falsePart.accept(this)
      builder.addEdge(builder.prevInstruction, exitNode)
    }
    builder.prevInstruction = exitNode
  }

  override fun visitPyMatchStatement(matchStatement: PyMatchStatement) {
    builder.startNode(matchStatement)
    val subject = matchStatement.subject
    subject?.accept(this)
    var nextClause = builder.prevInstruction
    var unreachable = false
    for (clause in matchStatement.caseClauses) {
      builder.prevInstruction = nextClause
      nextClause = addTransparentInstruction()

      myPatternBindingNames.clear()

      val pattern = clause.pattern
      if (pattern != null) {
        pattern.accept(this)
        addTypeAssertionNodes(clause, true, myPatternBindingNames)
      }

      val guard = clause.guardCondition
      if (guard != null) {
        val trueNode = addTransparentInstruction()
        visitCondition(guard, trueNode, nextClause)
        builder.prevInstruction = trueNode
        addTypeAssertionNodes(guard, true)
      }

      if (unreachable) {
        builder.addNode(PyUnreachableInstruction(this.builder, false))
      }
      if (pattern != null && pattern.isIrrefutable && (guard == null || PyEvaluator.evaluateAsBooleanNoResolve(guard, false))) {
        unreachable = true
      }
      builder.startNode(clause.statementList)
      clause.statementList.accept(this)
      builder.addPendingEdge(matchStatement, builder.prevInstruction)
      builder.updatePendingElementScope(clause.statementList, matchStatement)
    }
    builder.prevInstruction = nextClause
    builder.addNodeAndCheckPending(TransparentInstructionImpl(this.builder, matchStatement, ""))
    if (!builder.prevInstruction.allPred().isEmpty()) {
      addTypeAssertionNodes(matchStatement, false)
    }
    builder.addPendingEdge(matchStatement, builder.prevInstruction)
    builder.prevInstruction = null
  }

  override fun visitPyPattern(node: PyPattern) {
    val isRefutable = !node.isIrrefutable
    if (isRefutable) {
      builder.addNodeAndCheckPending(RefutablePatternInstruction(this.builder, node, false))
    }
    else {
      builder.startNode(node)
    }
    builder.addPendingEdge(node.parent, builder.prevInstruction)

    node.acceptChildren(this)
    builder.updatePendingElementScope(node, node.parent)

    if (isRefutable) {
      builder.addNode(RefutablePatternInstruction(this.builder, node, true))
    }
  }

  override fun visitPyOrPattern(node: PyOrPattern) {
    builder.addNodeAndCheckPending(RefutablePatternInstruction(this.builder, node, false))

    val onSuccess: TransparentInstruction = TransparentInstructionImpl(
      this.builder, node, "onSuccess"
    )
    val alternatives = node.alternatives
    val lastAlternative = alternatives.lastOrNull()

    for (alternative in alternatives) {
      alternative.accept(this)
      if (alternative !== lastAlternative) {
        // Allow next alternative to handle the fail edge of this alternative
        builder.updatePendingElementScope(node, alternative)
      }
      builder.addEdge(builder.prevInstruction, onSuccess)
      builder.prevInstruction = null
    }
    builder.addNode(onSuccess)
    builder.addNode(RefutablePatternInstruction(this.builder, node, true))
    builder.updatePendingElementScope(node, node.parent)
  }

  override fun visitPyClassPattern(node: PyClassPattern) {
    builder.addNodeAndCheckPending(RefutablePatternInstruction(this.builder, node, false))

    node.classNameReference.accept(this)
    builder.addPendingEdge(node.parent, builder.prevInstruction)

    node.argumentList.acceptChildren(this)
    builder.updatePendingElementScope(node, node.parent)

    builder.addNode(RefutablePatternInstruction(this.builder, node, true))
  }

  override fun visitPyValuePattern(node: PyValuePattern) {
    builder.addNodeAndCheckPending(RefutablePatternInstruction(this.builder, node, false))

    node.value.accept(this)
    builder.addPendingEdge(node.parent, builder.prevInstruction)

    builder.addNode(RefutablePatternInstruction(this.builder, node, true))
  }

  override fun visitPyAsPattern(node: PyAsPattern) {
    // AsPattern can't fail by itself – it fails only if its child fails.
    // So no need to create an additional fail edge
    builder.startNode(node)
    node.acceptChildren(this)
    if (node.getTarget() != null) {
      myPatternBindingNames.add(node.getTarget()!!.name!!)
    }
    builder.updatePendingElementScope(node, node.parent)
  }

  override fun visitPyCapturePattern(node: PyCapturePattern) {
    node.acceptChildren(this)
    // Although capture pattern is irrefutable, I add fail edge
    // here to add some connection to the next case clause.
    // Perhaps this can be reworked and simplified later.
    builder.addPendingEdge(node.parent, builder.prevInstruction)
    myPatternBindingNames.add(node.getTarget().name!!)
  }

  override fun visitPyGroupPattern(node: PyGroupPattern) {
    // GroupPattern can't fail by itself – it fails only if its child fails.
    // So no need to create an additional fail edge
    // Also no need for a dedicated node for GroupPattern itself
    node.acceptChildren(this)
    builder.updatePendingElementScope(node, node.parent)
  }

  override fun visitPyIfStatement(node: PyIfStatement) {
    builder.startNode(node)

    val exitInstructions: MutableList<Instruction?> = ArrayList()
    var alwaysTrueCondition: PyExpression? = null
    for (ifPart in StreamEx.of(node.getIfPart()).append(*node.elifParts)) {
      val thenNode = addTransparentInstruction()
      val elseNode = addTransparentInstruction()
      val condition = ifPart.condition
      if (condition != null) {
        visitCondition(condition, thenNode, elseNode)
      }
      builder.prevInstruction = thenNode

      val conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition, myLanguageLevel)
      val unreachable = alwaysTrueCondition != null || false == conditionResult
      if (unreachable) {
        val isUnreachableForTypeChecking: Boolean
        if (alwaysTrueCondition != null) {
          isUnreachableForTypeChecking = isTypeCheckingCondition(alwaysTrueCondition)
        }
        else {
          isUnreachableForTypeChecking = isTypeCheckingCondition(condition)
        }
        builder.addNode(PyUnreachableInstruction(this.builder, isUnreachableForTypeChecking))
      }
      if (true == conditionResult) {
        alwaysTrueCondition = condition
      }
      visitPyStatementPart(ifPart)

      if (!unreachable) {
        exitInstructions.add(builder.prevInstruction)
      }
      builder.prevInstruction = elseNode
    }

    val elsePart = node.elsePart
    if (elsePart != null) {
      if (alwaysTrueCondition != null) {
        builder.addNode(PyUnreachableInstruction(this.builder, isTypeCheckingCondition(alwaysTrueCondition)))
      }
      visitPyStatementPart(elsePart)
    }

    if (alwaysTrueCondition == null) {
      exitInstructions.add(builder.prevInstruction)
    }
    builder.prevInstruction = addTransparentInstruction(node)

    for (exitInstruction in Lists.reverse(exitInstructions)) {
      builder.addEdge(exitInstruction, builder.prevInstruction)
    }
  }

  override fun visitPyPrefixExpression(node: PyPrefixExpression) {
    if (myTrueFalseNodes != null && node.operator === PyTokenTypes.NOT_KEYWORD) {
      val operand = node.operand
      if (operand != null) {
        visitCondition(operand, myTrueFalseNodes!!.falseNode, myTrueFalseNodes!!.trueNode)
      }
    }
    else {
      super.visitPyPrefixExpression(node)
    }
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    val operator = node.operator
    if (operator === PyTokenTypes.AND_KEYWORD || operator === PyTokenTypes.OR_KEYWORD) {
      val left = node.leftExpression
      val right = node.rightExpression
      if (left == null || right == null) return

      builder.startNode(node)

      val trueNode: Instruction?
      val falseNode: Instruction?
      val exitNode: Instruction?
      if (myTrueFalseNodes == null) {
        exitNode = addTransparentInstruction()
        falseNode = exitNode
        trueNode = falseNode
      }
      else {
        trueNode = myTrueFalseNodes!!.trueNode
        falseNode = myTrueFalseNodes!!.falseNode
        exitNode = null
      }

      val rightNode: Instruction = addTransparentInstruction()
      if (operator === PyTokenTypes.AND_KEYWORD) {
        visitCondition(left, rightNode, falseNode)
      }
      else {
        visitCondition(left, trueNode, rightNode)
      }

      builder.prevInstruction = rightNode
      visitCondition(right, trueNode, falseNode)

      if (exitNode != null) {
        builder.prevInstruction = exitNode
      }
    }
    else {
      super.visitPyBinaryExpression(node)
    }
  }

  override fun visitPyWhileStatement(node: PyWhileStatement) {
    val entryNode = builder.startNode(node)

    val thenNode = addTransparentInstruction()
    val elseNode = addTransparentInstruction()

    val whilePart: PyConditionalStatementPart = node.whilePart
    val condition = whilePart.condition
    if (condition != null) {
      visitCondition(condition, thenNode, elseNode)
    }

    val conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition)

    builder.prevInstruction = if (false == conditionResult) null else thenNode
    visitPyStatementPart(whilePart)

    builder.checkPending(entryNode)
    builder.addEdge(builder.prevInstruction, entryNode)

    builder.prevInstruction = if (true == conditionResult) null else elseNode
    val elsePart = node.elsePart
    if (elsePart != null) {
      visitPyStatementPart(elsePart)
    }

    collectInternalPendingEdges(node)
  }

  override fun visitPyForStatement(node: PyForStatement) {
    builder.startNode(node)
    val forPart = node.forPart
    val source = forPart.source
    source?.accept(this)
    val head = builder.prevInstruction
    val elsePart = node.elsePart
    if (elsePart == null && !loopHasAtLeastOneIteration(node)) {
      builder.addPendingEdge(node, builder.prevInstruction)
    }
    val list = forPart.statementList
    val body: Instruction
    val target = forPart.target
    if (target != null) {
      body = builder.startNode(target)
      target.accept(this)
    }
    else {
      body = builder.startNode(list)
    }
    list.accept(this)
    if (builder.prevInstruction != null) {
      builder.addEdge(builder.prevInstruction, body) //loop
      builder.addPendingEdge(list, builder.prevInstruction) // exit
    }
    builder.processPending(ControlFlowBuilder.PendingProcessor { pendingScope: PsiElement?, instruction: Instruction? ->
      if (pendingScope != null && PsiTreeUtil.isAncestor(list, pendingScope, false)) {
        builder.addEdge(instruction, body) //loop
        builder.addPendingEdge(list, instruction) // exit
      }
      else {
        builder.addPendingEdge(pendingScope, instruction)
      }
    })
    builder.prevInstruction = head
    if (elsePart != null) {
      elsePart.accept(this)
      builder.addPendingEdge(node, builder.prevInstruction)
    }


    builder.flowAbrupted()
    collectInternalPendingEdges(node)
  }

  override fun visitPyBreakStatement(node: PyBreakStatement) {
    builder.startNode(node)
    val loop = node.loopStatement
    if (loop != null) {
      builder.addPendingEdge(loop, builder.prevInstruction)
    }
    else {
      builder.addPendingEdge(null, builder.prevInstruction)
    }
    builder.flowAbrupted()
  }

  override fun visitPyContinueStatement(node: PyContinueStatement) {
    builder.startNode(node)
    val loop = node.loopStatement
    if (loop != null) {
      val instruction = builder.findInstructionByElement(loop)
      if (instruction != null) {
        builder.addEdge(builder.prevInstruction, instruction)
      }
      else {
        builder.addPendingEdge(null, null)
      }

      // There is no edge between loop statement and next after loop instruction
      // when loop has at least one iteration
      // so `continue` is marked as one more last instruction in the loop
      // see visitPyWhileStatement
      // see visitPyForStatement
      if (loopHasAtLeastOneIteration(loop)) {
        builder.addPendingEdge(loop, builder.prevInstruction)
      }
    }
    builder.flowAbrupted()
  }

  override fun visitPyYieldExpression(node: PyYieldExpression) {
    builder.startNode(node)
    val expression = node.expression
    expression?.accept(this)
  }

  override fun visitPyRaiseStatement(node: PyRaiseStatement) {
    builder.startNode(node)
    val expressions = node.expressions
    for (expression in expressions) {
      expression.accept(this)
    }
    builder.addNode(PyRaiseInstruction(this.builder, node))
    builder.addPendingEdge(null, builder.prevInstruction)
    builder.flowAbrupted()
  }

  override fun visitPyReturnStatement(node: PyReturnStatement) {
    builder.startNode(node)
    val expression = node.expression
    expression?.accept(this)
    builder.addPendingEdge(null, builder.prevInstruction)
    builder.flowAbrupted()
  }

  override fun visitPyTryExceptStatement(node: PyTryExceptStatement) {
    builder.startNode(node)

    // Process try part
    val tryPart = node.tryPart
    builder.startNode(tryPart)
    tryPart.accept(this)

    // Goto else part after execution, or exit
    val elsePart = node.elsePart
    if (elsePart != null) {
      builder.startNode(elsePart)
      elsePart.accept(this)
    }
    builder.addPendingEdge(node, builder.prevInstruction)

    // Process except parts
    val exceptInstructions: MutableList<Instruction?> = ArrayList()
    var pendingBackup: MutableList<Pair<PsiElement?, Instruction?>> = ArrayList<Pair<PsiElement?, Instruction?>>()
    for (exceptPart in node.exceptParts) {
      pendingBackup.addAll(builder.pending)
      builder.pending = ArrayList<Pair<PsiElement?, Instruction?>?>()
      builder.flowAbrupted()
      val exceptInstruction = builder.startNode(exceptPart)
      exceptPart.accept(this)
      builder.addPendingEdge(node, builder.prevInstruction)
      exceptInstructions.add(exceptInstruction)
    }
    for (pair in pendingBackup) {
      builder.addPendingEdge(pair.first, pair.second)
    }

    val pendingNormalExits: MutableList<Pair<PsiElement?, Instruction?>> = ArrayList<Pair<PsiElement?, Instruction?>>()
    val finallyPart = node.finallyPart
    val finallyFailInstruction: Instruction?

    // Store pending normal exit instructions from try-except-else parts
    if (finallyPart != null) {
      builder.processPending(ControlFlowBuilder.PendingProcessor { pendingScope: PsiElement?, instruction: Instruction? ->
        val pendingElement = instruction!!.element
        if (pendingElement != null) {
          val isPending = PsiTreeUtil.isAncestor(node, pendingElement, false) &&
                          !PsiTreeUtil.isAncestor(finallyPart, pendingElement, false)
          if (isPending && pendingScope != null) {
            pendingNormalExits.add(Pair.createNonNull<PsiElement?, Instruction?>(pendingScope, instruction))
          }
          else {
            builder.addPendingEdge(pendingScope, instruction)
          }
        }
      })
    }

    // Finally-fail part handling
    if (finallyPart != null) {
      builder.flowAbrupted()
      finallyFailInstruction = builder.startNode(finallyPart)
      finallyPart.accept(this)
      builder.addNodeAndCheckPending(PyFinallyFailExitInstruction(this.builder, finallyFailInstruction))
      builder.addPendingEdge(null, builder.prevInstruction)
      builder.flowAbrupted()
    }
    else {
      finallyFailInstruction = null
    }

    // Create exception edges
    for (instruction in builder.instructions) {
      val e = instruction.element
      if (e == null || !canRaiseExceptions(instruction)) {
        continue
      }
      // All instructions inside the try part have edges to except and finally parts
      if (PsiTreeUtil.getParentOfType(e, PyTryPart::class.java, false) === tryPart) {
        for (inst in exceptInstructions) {
          builder.addEdge(instruction, inst)
        }
        if (finallyPart != null) {
          builder.addEdge(instruction, finallyFailInstruction)
        }
      }
      if (finallyPart != null) {
        // All instructions inside except parts have edges to the finally part
        for (exceptPart in node.exceptParts) {
          if (PsiTreeUtil.isAncestor(exceptPart, e, false)) {
            builder.addEdge(instruction, finallyFailInstruction)
          }
        }
        // All instructions inside the else part have edges to the finally part
        if (PsiTreeUtil.isAncestor(elsePart, e, false)) {
          builder.addEdge(instruction, finallyFailInstruction)
        }
      }
    }

    if (finallyPart != null) {
      builder.processPending(ControlFlowBuilder.PendingProcessor { pendingScope: PsiElement?, instruction: Instruction? ->
        val e = instruction!!.element
        if (e != null) {
          // Change the scope of pending edges from finally-fail part to point to the last instruction
          if (PsiTreeUtil.isAncestor(finallyPart, e, false)) {
            builder.addPendingEdge(null, instruction)
          }
          else if (pendingScope == null && PsiTreeUtil.isAncestor(node, e, false)) {
            builder.addEdge(instruction, finallyFailInstruction)
          }
          else {
            builder.addPendingEdge(pendingScope, instruction)
          }
        }
      })

      // Duplicate CFG for finally (-fail and -success) only if there are some successful exits from the
      // try part. Otherwise, a single CFG for finally provides the correct control flow
      val finallyInstruction: Instruction?
      if (!pendingNormalExits.isEmpty()) {
        // Finally-success part handling
        pendingBackup = ArrayList<Pair<PsiElement?, Instruction?>>(
          builder.pending
        )
        builder.pending = ArrayList<Pair<PsiElement?, Instruction?>?>()
        builder.flowAbrupted()
        val finallySuccessInstruction = builder.startNode(finallyPart)
        finallyPart.accept(this)
        for (pair in pendingBackup) {
          builder.addPendingEdge(pair.first, pair.second)
        }
        finallyInstruction = finallySuccessInstruction
      }
      else {
        finallyInstruction = finallyFailInstruction
      }

      // Connect normal exits from try and else parts to the finally part
      for (pendingScopeAndInstruction in pendingNormalExits) {
        val pendingScope = pendingScopeAndInstruction.first
        val instruction = pendingScopeAndInstruction.second

        builder.addEdge(instruction, finallyInstruction)

        // When instruction continues outside try-except statement scope
        // the last instruction in finally-block is marked as pointing to that continuation
        if (PsiTreeUtil.isAncestor(pendingScope, node, true)) {
          builder.addPendingEdge(pendingScope, builder.prevInstruction)
        }
      }
    }

    collectInternalPendingEdges(node)
  }

  override fun visitPyComprehensionElement(node: PyComprehensionElement) {
    var prevCondition: PyExpression? = null
    builder.startNode(node)
    val iterators: MutableList<Instruction?> = ArrayList()

    for (component in node.components) {
      if (component is PyComprehensionForComponent) {
        val iteratedList = component.getIteratedList<PyExpression>()
        val iteratorVariable = component.getIteratorVariable<PyExpression>()
        if (prevCondition != null) {
          builder.startConditionalNode(iteratedList, prevCondition, true)
          prevCondition = null
        }
        else {
          builder.startNode(iteratedList)
        }
        iteratedList.accept(this)

        // for-loop continue and exit
        for (i in iterators) {
          builder.addEdge(builder.prevInstruction, i)
        }
        builder.addPendingEdge(node, builder.prevInstruction)

        val iterator = builder.startNode(iteratorVariable)
        iteratorVariable.accept(this)

        // Inner "for" and "if" constructs will be linked to all outer iterators
        iterators.add(iterator)
      }
      else if (component is PyComprehensionIfComponent) {
        val condition = component.getTest<PyExpression?>()
        if (condition == null) {
          continue
        }
        if (prevCondition != null) {
          builder.startConditionalNode(condition, prevCondition, true)
        }
        else {
          builder.startNode(condition)
        }
        condition.accept(this)
        addTypeAssertionNodes(condition, true)

        // Condition is true for nested "for" and "if" constructs, next startNode() should create a conditional node
        prevCondition = condition

        // for-loop continue and exit
        for (i in iterators) {
          builder.addEdge(builder.prevInstruction, i)
        }
        builder.addPendingEdge(node, builder.prevInstruction)
      }
    }

    val result = node.resultExpression
    if (result != null) {
      if (prevCondition != null) {
        builder.startConditionalNode(result, prevCondition, true)
      }
      else {
        builder.startNode(result)
      }
      result.accept(this)

      // for-loop continue
      for (i in iterators) {
        builder.addEdge(builder.prevInstruction, i)
      }
    }

    collectInternalPendingEdges(node)
  }

  override fun visitPyAssertStatement(node: PyAssertStatement) {
    builder.startNode(node)
    val args = node.arguments
    for (arg in args) {
      arg.accept(this)
    }
    // assert False
    if (args.isNotEmpty()) {
      if (!PyEvaluator.evaluateAsBooleanNoResolve(args[0], true)) {
        builder.addNode(PyRaiseInstruction(this.builder, node))
        builder.addPendingEdge(null, builder.prevInstruction)
        builder.flowAbrupted()
        return
      }

      val trueNode = addTransparentInstruction(node)
      val falseNode = addTransparentInstruction(node)
      visitCondition(args[0], trueNode, falseNode)

      val raiseInstruction = PyRaiseInstruction(this.builder, node)
      builder.instructions.add(raiseInstruction)
      builder.addEdge(falseNode, raiseInstruction)

      builder.addPendingEdge(null, raiseInstruction)
      builder.prevInstruction = trueNode
    }
  }

  override fun visitPyLambdaExpression(node: PyLambdaExpression) {
    builder.startNode(node)
    visitParameterListExpressions(node.parameterList)
  }

  override fun visitPyWithStatement(node: PyWithStatement) {
    builder.startNode(node)

    val exits: MutableList<Instruction?> = ArrayList()
    for (item in node.withItems) {
      val itemStart = builder.instructions.size
      item.accept(this)
      val itemEnd = builder.instructions.size
      for (i in itemStart..<itemEnd) {
        val instruction = builder.instructions[i]
        val e = instruction.element
        if (e == null || !canRaiseExceptions(instruction) || !PsiTreeUtil.isAncestor(node, e, false)) {
          continue
        }
        for (exit in exits) {
          builder.addEdge(builder.instructions[i], exit)
        }
      }

      val nextExit = PyWithContextExitInstruction(this.builder, item)
      exits.add(nextExit)
      builder.instructions.add(nextExit)
      // ControlFlowUtil.iterate assumes nodes are added to CFG in order they are created
    }

    val toAllExits = addTransparentInstruction()
    val fromAllExits = addTransparentInstruction()
    for (exit in exits) {
      builder.addEdge(toAllExits, exit)
      builder.addEdge(exit, fromAllExits)
    }

    val stmtStart = builder.instructions.size
    node.statementList.accept(this)
    val stmtEnd = builder.instructions.size

    for (j in stmtStart..<stmtEnd) {
      val instruction = builder.instructions[j]
      val e = instruction.element
      if (e == null || !canRaiseExceptions(instruction) || !PsiTreeUtil.isAncestor(node, e, false)) {
        continue
      }
      builder.addEdge(instruction, toAllExits)
    }
    builder.addEdge(builder.prevInstruction, toAllExits)

    if (!exits.isEmpty()) {
      builder.addPendingEdge(node, fromAllExits)
    }
  }

  override fun visitPyAssignmentExpression(node: PyAssignmentExpression) {
    val assignedValue = node.assignedValue
    assignedValue?.accept(this)

    val target = node.target
    target?.accept(this)
  }

  override fun visitPyTypeAliasStatement(node: PyTypeAliasStatement) {
    builder.startNode(node)

    val instruction = ReadWriteInstruction.write(
      this.builder, node, node.name
    )
    builder.addNodeAndCheckPending(instruction)
  }

  override fun visitPyTypeParameterList(node: PyTypeParameterList) {}

  private fun visitCondition(expression: PyExpression, trueNode: Instruction, falseNode: Instruction) {
    val prevTrueFalseNodes = myTrueFalseNodes
    myTrueFalseNodes = TrueFalseNodes(trueNode, falseNode)
    expression.accept(this)
    myTrueFalseNodes = prevTrueFalseNodes

    val condition = PyPsiUtils.flattenParens(expression)
    if (condition != null && !isLogicalExpression(condition)) {
      addConditionalNode(expression, false, falseNode)
      addConditionalNode(expression, true, trueNode)
    }
  }

  private fun addConditionalNode(condition: PyExpression, result: Boolean, target: Instruction) {
    val prevInstruction = builder.prevInstruction
    builder.addNode(ConditionalInstructionImpl(this.builder, null, condition, result))
    addTypeAssertionNodes(condition, result)
    builder.addEdge(builder.prevInstruction, target)
    builder.prevInstruction = prevInstruction
  }

  private fun visitPyStatementPart(statementPart: PyStatementPart) {
    val statementList = statementPart.statementList
    builder.startNode(statementList)
    statementList.accept(this)
  }

  private fun addTypeAssertionNodes(condition: PyElement, positive: Boolean, ignoredNames: List<String>? = null) {
    val evaluator = PyTypeAssertionEvaluator(positive)
    condition.accept(evaluator)
    for (def in evaluator.definitions) {
      val e = def.element
      var name: String? = null
      if (e != null) {
        name = getName(e)
      }
      if (name != null && ignoredNames != null && ignoredNames.contains(name)) {
        continue
      }
      builder.addNode(ReadWriteInstruction.assertType(this.builder, e, name, def.typeEvalFunction))
    }
  }

  private fun addTransparentInstruction(element: PsiElement? = null): TransparentInstruction {
    val instruction = TransparentInstructionImpl(
      this.builder, element, ""
    )
    builder.instructions.add(instruction)
    return instruction
  }

  /**
   * Can be used to collect all pending edges
   * that we used to build CFG for `node`,
   * but are not relevant to other elements.
   * Is almost equivalent to this:
   *
   * <pre>`visitPy...(node); myBuilder.startNode(node.nextSibling); // collectInternalPendingEdges does this, without needing nextSibling `</pre>
   */
  private fun collectInternalPendingEdges(node: PyElement) {
    builder.addNode(TransparentInstructionImpl(this.builder, node, "")) // exit
    builder.processPending(ControlFlowBuilder.PendingProcessor { pendingScope: PsiElement?, instruction: Instruction? ->
      if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
        builder.addEdge(instruction, builder.prevInstruction) // to exit
      }
      else {
        builder.addPendingEdge(pendingScope, instruction)
      }
    })
  }

  companion object {
    private fun isTypeCheckingCondition(expression: PyExpression?): Boolean {
      return isTypeCheckingCheck(expression) || isVersionCheck(expression)
    }

    private fun isTypeCheckingCheck(expression: PyExpression?): Boolean {
      var expression = expression
      expression = PyPsiUtils.flattenParens(expression)
      if (expression is PyPrefixExpression && expression.operator === PyTokenTypes.NOT_KEYWORD) {
        return isTypeCheckingCondition(expression.operand)
      }
      return PyEvaluator.isTypeCheckingExpression(expression)
    }

    private fun isVersionCheck(expression: PyExpression?): Boolean {
      var expression = expression
      expression = PyPsiUtils.flattenParens(expression)
      if (expression is PyPrefixExpression && expression.operator === PyTokenTypes.NOT_KEYWORD) {
        return isVersionCheck(expression.operand)
      }
      if (expression is PyBinaryExpression) {
        val op = expression.operator
        if (PyTokenTypes.AND_KEYWORD == op || PyTokenTypes.OR_KEYWORD == op) {
          return isVersionCheck(expression.leftExpression) &&
                 isVersionCheck(expression.rightExpression)
        }
        if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(op)) {
          if (PyEvaluator.isSysVersionInfoExpression(expression.leftExpression) &&
              PyPsiUtils.flattenParens(expression.rightExpression) is PyTupleExpression
          ) {
            return true
          }
          if (PyPsiUtils.flattenParens(expression.leftExpression) is PyTupleExpression &&
              PyEvaluator.isSysVersionInfoExpression(expression.rightExpression)
          ) {
            return true
          }
        }
      }
      return false
    }

    private fun loopHasAtLeastOneIteration(loopStatement: PyLoopStatement): Boolean {
      val expression = when (loopStatement) {
        is PyForStatement -> loopStatement.forPart.source
        is PyWhileStatement -> loopStatement.whilePart.condition
        else -> null
      }

      return PyEvaluator.evaluateAsBooleanNoResolve(expression, false)
    }

    private fun isLogicalExpression(expression: PyExpression): Boolean {
      if (expression is PyBinaryExpression) {
        val operator = expression.operator
        return operator === PyTokenTypes.AND_KEYWORD || operator === PyTokenTypes.OR_KEYWORD
      }
      if (expression is PyPrefixExpression) {
        return expression.operator === PyTokenTypes.NOT_KEYWORD
      }
      return false
    }

    private fun canRaiseExceptions(instruction: Instruction): Boolean {
      if (instruction is ReadWriteInstruction) {
        return true
      }
      val element = instruction.element
      return !(element is PyReturnStatement && element.expression == null || element is PyContinueStatement
               || element is PyBreakStatement
               || element is PyPassStatement
               || element is PyStatementList)
    }

    private fun augAssignmentTypeCallback(target: PyReferenceExpression): InstructionTypeCallback {
      val statement = PsiTreeUtil.getParentOfType(target, PyAugAssignmentStatement::class.java)
      return InstructionTypeCallback { context: TypeEvalContext? ->
        val assignmentType = if (statement != null) context!!.getType(statement) else null
        Ref.create(if (!assignmentType.isUnknown) assignmentType else context!!.getType(target))
      }
    }

    private fun getName(expr: PyQualifiedExpression): String? {
      val qname = expr.asQualifiedName()
      return qname?.toString() ?: expr.name
    }
  }
}
