package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.controlflow.impl.InstructionImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class PyControlFlowBuilder extends PyRecursiveElementVisitor {

  private final ControlFlowBuilder myBuilder = new ControlFlowBuilder();

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//// Control flow builder staff
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public ControlFlow buildControlFlow(@NotNull final ScopeOwner owner) {
    return myBuilder.build(this, owner);
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    // Stop here
  }

  @Override
  public void visitPyClass(final PyClass node) {
    // Stop here
  }

  @Override
  public void visitPyStatement(final PyStatement node) {
    myBuilder.startNode(node);
    super.visitPyStatement(node);
  }

  @Override
  public void visitPyCallExpression(final PyCallExpression node) {
    if ("exit".equals(node.getCallee().getText())){
      for (PyExpression expression : node.getArguments()) {
        expression.accept(this);
      }

      // Here we process pending instructions!!!
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
            myBuilder.addPendingEdge(null, instruction);
          } else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
      myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
      myBuilder.flowAbrupted();
    } else {
      super.visitPyCallExpression(node);
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
    if (qualifier != null){
      qualifier.accept(this);
      return;
    }
    if (PyImportStatementNavigator.getImportStatementByElement(node) != null){
      return;
    }

    final ReadWriteInstruction.ACCESS access = PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null
                                               ? ReadWriteInstruction.ACCESS.READWRITE
                                               : ReadWriteInstruction.ACCESS.READ;
    final ReadWriteInstruction readWriteInstruction = new ReadWriteInstruction(myBuilder, node, node.getName(), access);
    myBuilder.addNode(readWriteInstruction);
    myBuilder.checkPending(readWriteInstruction);
  }

  @Override
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    myBuilder.startNode(node);
    final PyExpression value = node.getAssignedValue();
    if (value != null) {
      value.accept(this);
    }
    for (PyExpression expression : node.getTargets()) {
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
    final PsiElement[] children = node.getChildren();
    // Case of non qualified reference
    if (children.length == 0){
      final ReadWriteInstruction.ACCESS access = node.getParent() instanceof PySliceExpression 
                                                 ? ReadWriteInstruction.ACCESS.READ : ReadWriteInstruction.ACCESS.WRITE;
      final ReadWriteInstruction instruction = new ReadWriteInstruction(myBuilder, node, node.getName(), access);
      myBuilder.addNode(instruction);
      myBuilder.checkPending(instruction);
    } else {
      for (PsiElement child : children) {
        child.accept(this);
      }
    }
  }

  @Override
  public void visitPyNamedParameter(final PyNamedParameter node) {
    final ReadWriteInstruction instruction = new ReadWriteInstruction(myBuilder, node, node.getName(), ReadWriteInstruction.ACCESS.WRITE);
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyImportStatement(final PyImportStatement node) {
    myBuilder.startNode(node);
    for (PyImportElement importElement : node.getImportElements()) {
      final PyReferenceExpression importReference = importElement.getImportReference();
      if (importReference != null) {
        final ReadWriteInstruction instruction =
          new ReadWriteInstruction(myBuilder, importElement, importReference.getReferencedName(), ReadWriteInstruction.ACCESS.WRITE);
        myBuilder.addNode(instruction);
        myBuilder.checkPending(instruction);
      }
    }
  }

  private Instruction getPrevInstruction(final PyElement condition) {
    final Ref<Instruction> head = new Ref<Instruction>(myBuilder.prevInstruction);
    myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        if (pendingScope != null && PsiTreeUtil.isAncestor(condition, pendingScope, false)) {
          head.set(instruction);
        }
        else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }
      }
    });
    return head.get();
  }

  @Override
  public void visitPyIfStatement(final PyIfStatement node) {
    myBuilder.startNode(node);
    final PyIfPart ifPart = node.getIfPart();
    PyExpression condition = ifPart.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    // Set the head as the last instruction of condition
    Instruction head = getPrevInstruction(condition);
    myBuilder.prevInstruction = head;
    final PyStatementList thenStatements = ifPart.getStatementList();
    if (thenStatements != null) {
      myBuilder.startConditionalNode(thenStatements, condition, true);
      thenStatements.accept(this);
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(thenStatements, pendingScope, false)) {
            myBuilder.addPendingEdge(node, instruction);
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    for (PyIfPart part : node.getElifParts()) {
      // restore head
      myBuilder.prevInstruction = head;
      condition = part.getCondition();
      if (condition != null) {
        condition.accept(this);
      }
      // Set the head as the last instruction of condition
      head = getPrevInstruction(condition);
      myBuilder.prevInstruction = head;
      myBuilder.startConditionalNode(ifPart, condition, true);
      final PyStatementList statementList = part.getStatementList();
      if (statementList != null) {
        statementList.accept(this);
      }
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(ifPart, pendingScope, false)) {
            myBuilder.addPendingEdge(node, instruction);
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    // restore head
    myBuilder.prevInstruction = head;
    final PyElsePart elseBranch = node.getElsePart();
    if (elseBranch != null) {
      myBuilder.startConditionalNode(elseBranch, condition, false);
      elseBranch.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }

  }

  @Override
  public void visitPyWhileStatement(final PyWhileStatement node) {
    final Instruction instruction = myBuilder.startNode(node);
    final PyWhilePart whilePart = node.getWhilePart();
    final PyExpression condition = whilePart.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    final Instruction head = getPrevInstruction(condition);
    myBuilder.prevInstruction = head;

    // if condition was false
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null) {
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }

    final PyStatementList statementList = whilePart.getStatementList();
    if (statementList != null) {
      myBuilder.startConditionalNode(statementList, condition, true);
      statementList.accept(this);
    }
    if (myBuilder.prevInstruction != null) {
      myBuilder.addEdge(myBuilder.prevInstruction, instruction); //loop
    }
    // else part
    if (elsePart != null) {
      myBuilder.startConditionalNode(statementList, condition, false);
      elsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    myBuilder.flowAbrupted();
    myBuilder.checkPending(instruction); //check for breaks targeted here
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
    if (list != null) {
      Instruction bodyInstruction = myBuilder.startNode(list);
      final PyExpression target = forPart.getTarget();
      if (target != null) {
        target.accept(this);
      }

      list.accept(this);

      if (myBuilder.prevInstruction != null) {
        myBuilder.addEdge(myBuilder.prevInstruction, bodyInstruction);  //loop
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction); // exit
      }
      final Ref<Instruction> bodyInstRef = new Ref<Instruction>(bodyInstruction);
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(list, pendingScope, false)) {
            myBuilder.addEdge(instruction, bodyInstRef.get());  //loop
            myBuilder.addPendingEdge(node, instruction); // exit
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
    }
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
        myBuilder.addPendingEdge(null, instruction);
      }
    }
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyRaiseStatement(final PyRaiseStatement node) {
    myBuilder.startNode(node);
    final PyExpression[] expressions = node.getExpressions();
    if (expressions != null){
      for (PyExpression expression : expressions) {
        expression.accept(this);
      }

      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          final PsiElement pendingElement = instruction.getElement();
          if (pendingElement != null && PsiTreeUtil.isAncestor(node, pendingElement, false)) {
            myBuilder.addEdge(null, instruction);
          } else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
    }
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
// Here we process pending instructions!!!
    myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
          myBuilder.addPendingEdge(null, instruction);
        } else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }        
      }
    });
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    myBuilder.startNode(node);

// process body
    final PyTryPart tryPart = node.getTryPart();
    myBuilder.startNode(tryPart);
    tryPart.accept(this);
    final Instruction lastBlockInstruction = myBuilder.prevInstruction;

// Goto else block after execution, or exit
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart != null) {
      myBuilder.startNode(elsePart);
      elsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    else {
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }

    final ArrayList<Instruction> exceptInstructions = new ArrayList<Instruction>();
    // Store pending and clear it
    final List<Pair<PsiElement, Instruction>> myPending = myBuilder.pending;
    myBuilder.pending = new ArrayList<Pair<PsiElement, Instruction>>();
    for (PyExceptPart exceptPart : node.getExceptParts()) {
      myBuilder.prevInstruction = lastBlockInstruction;
      final Instruction exceptInstruction = new InstructionImpl(myBuilder, exceptPart);
      myBuilder.addNode(exceptInstruction);
      exceptInstructions.add(exceptInstruction);
      exceptPart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    // Restore pending
    for (Pair<PsiElement, Instruction> pair : myPending) {
      myBuilder.addPendingEdge(pair.first, pair.second);
    }
    // Finally part handling
    final PyFinallyPart finallyPart = node.getFinallyPart();
    Instruction finallyInstruction = null;
    Instruction lastFinallyInstruction = null;
    if (finallyPart != null) {
      myBuilder.flowAbrupted();
      finallyInstruction = myBuilder.startNode(finallyPart);
      finallyPart.accept(this);
      lastFinallyInstruction = myBuilder.prevInstruction;
      myBuilder.addPendingEdge(finallyPart, lastFinallyInstruction);
    }
    final Ref<Instruction> finallyRef = new Ref<Instruction>(finallyInstruction);
    final Ref<Instruction> lastFinallyRef = new Ref<Instruction>(lastFinallyInstruction);
    myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        final PsiElement pendingElement = instruction.getElement();
        if (pendingElement == null){
          myBuilder.addPendingEdge(pendingScope, instruction);
          return;
        }

        // Process raise statements
        if (PsiTreeUtil.isAncestor(tryPart, pendingElement, false) &&
            PsiTreeUtil.getParentOfType(pendingElement, PyRaiseStatement.class) != null){
          for (Instruction rescueInstruction : exceptInstructions) {
            myBuilder.addEdge(instruction, rescueInstruction);
          }
          myBuilder.addPendingEdge(pendingScope, instruction);
          return;
        }

        // Add except statements
        if (PsiTreeUtil.isAncestor(tryPart, pendingElement, false)){
          for (Instruction rescueInstruction : exceptInstructions) {
            myBuilder.addEdge(instruction, rescueInstruction);
          }
        }

        // Process finally
        if (!finallyRef.isNull()){
          myBuilder.addEdge(instruction, finallyRef.get());
          if (!lastFinallyRef.isNull() &&
              PsiTreeUtil.getParentOfType(pendingElement, PyReturnStatement.class, false) != null){
            myBuilder.addPendingEdge(null, lastFinallyRef.get());
          }
          return;
        }

        myBuilder.addPendingEdge(pendingScope, instruction);

      }
    });
  }

  @Override
  public void visitPyListCompExpression(final PyListCompExpression node) {
    visitPyGeneratorExpression(node);
  }

  @Override
  public void visitPyGeneratorExpression(final PyGeneratorExpression node) {
    myBuilder.startNode(node);
    PyExpression prevCondition = null;
    for (ComprhIfComponent component : node.getIfComponents()) {
      final PyExpression condition = component.getTest();
      if (condition != null){
        condition.accept(this);
        final Instruction head = myBuilder.prevInstruction;
        final Instruction prevInstruction =
          prevCondition != null ? myBuilder.startConditionalNode(condition, prevCondition, true) : myBuilder.startNode(condition);
        prevCondition = condition;
        // restore head
        myBuilder.prevInstruction = head;
        myBuilder.addPendingEdge(node, head); // false condition
        myBuilder.prevInstruction = prevInstruction;
      }
    }

    for (ComprhForComponent forComponent : node.getForComponents()) {
      final PyExpression iteratedList = forComponent.getIteratedList();
      if (prevCondition != null) {
        myBuilder.startConditionalNode(iteratedList, prevCondition, true);
        prevCondition = null;
      }
      iteratedList.accept(this);
      forComponent.getIteratorVariable().accept(this);
    }

    final PyExpression result = node.getResultExpression();
    if (result != null) {
      result.accept(this);
    }
  }
}