package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.codeInsight.controlflow.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class PyControlFlowBuilder extends PyRecursiveElementVisitor {
  // Here we store all the instructions
  private List<Instruction> myInstructions;

  private Instruction myPrevInstruction;

  // Here we store all the pending instructions with their scope
  private List<Pair<PsiElement, Instruction>> myPending;

  private int myInstructionNumber;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//// Control flow builder staff
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public ControlFlow buildControlFlow(@NotNull final ControlFlowOwner owner) {
    myInstructions = new ArrayList<Instruction>();
    myPending = new ArrayList<Pair<PsiElement, Instruction>>();
    myInstructionNumber = 0;

    // create start pseudo node
    startNode(null);

    owner.acceptChildren(this);

    // create end pseudo node and close all pending edges
    checkPending(startNode(null));

    return new ControlFlowImpl(myInstructions.toArray(new Instruction[myInstructions.size()]));
  }

  @Nullable
  private Instruction findInstructionByElement(final PsiElement element) {
    for (int i = myInstructions.size() - 1; i >= 0; i--) {
      final Instruction instruction = myInstructions.get(i);
      if (element.equals(instruction.getElement())) {
        return instruction;
      }
    }
    return null;
  }

  /**
   * Adds edge between 2 edges
   *
   * @param beginInstruction Begin of new edge
   * @param endInstruction   End of new edge
   */
  private static void addEdge(final Instruction beginInstruction, final Instruction endInstruction) {
    if (beginInstruction == null || endInstruction == null) {
      return;
    }
    if (!beginInstruction.allSucc().contains(endInstruction)) {
      beginInstruction.allSucc().add(endInstruction);
    }

    if (!endInstruction.allPred().contains(beginInstruction)) {
      endInstruction.allPred().add(beginInstruction);
    }
  }

  /**
   * Add new node and set prev instruction pointing to this instruction
   *
   * @param instruction new instruction
   */
  private void addNode(final Instruction instruction) {
    myInstructions.add(instruction);
    if (myPrevInstruction != null) {
      addEdge(myPrevInstruction, instruction);
    }
    myPrevInstruction = instruction;
  }

  /**
   * Stops control flow, used for break, next, redo
   */
  private void flowAbrupted() {
    myPrevInstruction = null;
  }

  /**
   * Adds pending edge in pendingScope
   *
   * @param pendingScope Scope for instruction
   * @param instruction  "Last" pending instruction
   */
  private void addPendingEdge(final PsiElement pendingScope, final Instruction instruction) {
    if (instruction == null) {
      return;
    }

    int i = 0;
    // another optimization! Place pending before first scope, not contained in pendingScope
    // the same logic is used in checkPending
    if (pendingScope != null) {
      for (; i < myPending.size(); i++) {
        final Pair<PsiElement, Instruction> pair = myPending.get(i);
        final PsiElement scope = pair.getFirst();
        if (scope == null) {
          continue;
        }
        if (!PsiTreeUtil.isAncestor(scope, pendingScope, true)) {
          break;
        }
      }
    }
    myPending.add(i, Pair.create(pendingScope, instruction));
  }

  private void checkPending(@NotNull final Instruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) {
      // if element is null (fake element, we just process all pending)
      for (Pair<PsiElement, Instruction> pair : myPending) {
        addEdge(pair.getSecond(), instruction);
      }
      myPending.clear();
    }
    else {
      // else we just all the pending with scope containing in element
      // reverse order is just an optimization
      for (int i = myPending.size() - 1; i >= 0; i--) {
        final Pair<PsiElement, Instruction> pair = myPending.get(i);
        final PsiElement scopeWhenToAdd = pair.getFirst();
        if (scopeWhenToAdd == null) {
          continue;
        }
        if (!PsiTreeUtil.isAncestor(scopeWhenToAdd, element, false)) {
          addEdge(pair.getSecond(), instruction);
          myPending.remove(i);
        }
        else {
          break;
        }
      }
    }
  }

  /**
   * Creates instruction for given element, and adds it to myInstructionsStack
   * Warning! Always call finishNode after startNode
   *
   * @param element Element to create instruction for
   * @return new instruction
   */
  private Instruction startNode(final PyElement element) {
    final Instruction instruction = new InstructionImpl(element, myInstructionNumber++);
    addNode(instruction);
    checkPending(instruction);
    return instruction;
  }

  /**
   * Creates conditional instruction for given element, and adds it to myInstructionsStack
   * Warning! Always call finishNode after startNode
   *
   * @param element Element to create instruction for
   * @return new instruction
   */
  private Instruction startConditionalNode(final PyElement element, final PyElement condition, final boolean result) {
    final ConditionInstruction instruction = new ConditionInstructionImpl(element, myInstructionNumber++, condition, result);
    addNode(instruction);
    checkPending(instruction);
    return instruction;
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
    startNode(node);
    super.visitPyStatement(node);
  }

  @Override
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    startNode(node);
    final PyExpression value = node.getAssignedValue();
    if (value != null) {
      value.accept(this);
    }
    for (PyExpression expression : node.getTargets()) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyTargetExpression(final PyTargetExpression node) {
    final WriteInstruction instruction = new WriteInstructionImpl(node, node.getName(), myInstructionNumber++);
    addNode(instruction);
    checkPending(instruction);
  }

  @Override
  public void visitPyNamedParameter(final PyNamedParameter node) {
    final WriteInstruction instruction = new WriteInstructionImpl(node, node.getName(), myInstructionNumber++);
    addNode(instruction);
    checkPending(instruction);
  }

  private Instruction getPrevInstruction(final PyElement condition) {
    final Ref<Instruction> head = new Ref<Instruction>(myPrevInstruction);
    processPending(new PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        if (pendingScope != null && PsiTreeUtil.isAncestor(condition, pendingScope, false)) {
          head.set(instruction);
        }
        else {
          addPendingEdge(pendingScope, instruction);
        }
      }
    });
    return head.get();
  }

  @Override
  public void visitPyIfStatement(final PyIfStatement node) {
    startNode(node);
    final PyIfPart ifPart = node.getIfPart();
    PyExpression condition = ifPart.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    // Set the head as the last instruction of condition
    Instruction head = getPrevInstruction(condition);
    myPrevInstruction = head;
    final PyStatementList thenStatements = ifPart.getStatementList();
    if (thenStatements != null) {
      startConditionalNode(thenStatements, condition, true);
      thenStatements.accept(this);
      processPending(new PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(thenStatements, pendingScope, false)) {
            addPendingEdge(node, instruction);
          }
          else {
            addPendingEdge(pendingScope, instruction);
          }
        }
      });
      addPendingEdge(node, myPrevInstruction);
    }
    for (PyIfPart part : node.getElifParts()) {
      // restore head
      myPrevInstruction = head;
      condition = part.getCondition();
      if (condition != null) {
        condition.accept(this);
      }
      // Set the head as the last instruction of condition
      head = getPrevInstruction(condition);
      myPrevInstruction = head;
      startConditionalNode(ifPart, condition, true);
      final PyStatementList statementList = part.getStatementList();
      if (statementList != null) {
        statementList.accept(this);
      }
      processPending(new PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(ifPart, pendingScope, false)) {
            addPendingEdge(node, instruction);
          }
          else {
            addPendingEdge(pendingScope, instruction);
          }
        }
      });
      addPendingEdge(node, myPrevInstruction);
    }
    // restore head
    myPrevInstruction = head;
    final PyElsePart elseBranch = node.getElsePart();
    if (elseBranch != null) {
      startConditionalNode(elseBranch, condition, false);
      elseBranch.accept(this);
      addPendingEdge(node, myPrevInstruction);
    }

  }

  @Override
  public void visitPyWhileStatement(final PyWhileStatement node) {
    final Instruction instruction = startNode(node);
    final PyWhilePart whilePart = node.getWhilePart();
    final PyExpression condition = whilePart.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    final Instruction head = getPrevInstruction(condition);
    myPrevInstruction = head;

    // if condition was false
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null) {
      addPendingEdge(node, myPrevInstruction);
    }

    final PyStatementList statementList = whilePart.getStatementList();
    if (statementList != null) {
      startConditionalNode(statementList, condition, true);
      statementList.accept(this);
    }
    if (myPrevInstruction != null) {
      addEdge(myPrevInstruction, instruction); //loop
    }
    // else part
    if (elsePart != null) {
      startConditionalNode(statementList, condition, false);
      elsePart.accept(this);
      addPendingEdge(node, myPrevInstruction);
    }
    flowAbrupted();
    checkPending(instruction); //check for breaks targeted here
  }

  @Override
  public void visitPyForStatement(final PyForStatement node) {
    startNode(node);
    final PyForPart forPart = node.getForPart();
    final PyExpression source = forPart.getSource();
    if (source != null) {
      source.accept(this);
    }
    final Instruction head = myPrevInstruction;
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null) {
      addPendingEdge(node, myPrevInstruction);
    }

    final PyStatementList list = forPart.getStatementList();
    if (list != null) {
      Instruction bodyInstruction = startNode(list);
      final PyExpression target = forPart.getTarget();
      if (target != null) {
        target.accept(this);
      }

      list.accept(this);

      if (myPrevInstruction != null) {
        addEdge(myPrevInstruction, bodyInstruction);  //loop
        addPendingEdge(node, myPrevInstruction); // exit
      }
    }
    myPrevInstruction = head;
    if (elsePart != null) {
      elsePart.accept(this);
      addPendingEdge(node, myPrevInstruction); // exit
    }
    flowAbrupted();
  }

  @Override
  public void visitPyBreakStatement(final PyBreakStatement node) {
    final Instruction breakInstruction = new InstructionImpl(node, myInstructionNumber++);
    addNode(breakInstruction);
    checkPending(breakInstruction);
    final PyLoopStatement loop = node.getLoopStatement();
    if (loop != null) {
      addPendingEdge(loop, myPrevInstruction);
      flowAbrupted();
    }
  }

  @Override
  public void visitPyContinueStatement(final PyContinueStatement node) {
    final Instruction nextInstruction = new InstructionImpl(node, myInstructionNumber++);
    addNode(nextInstruction);
    checkPending(nextInstruction);
    final PyLoopStatement loop = node.getLoop();
    if (loop != null) {
      final Instruction instruction = findInstructionByElement(loop);
      if (instruction != null) {
        addEdge(myPrevInstruction, instruction);
        flowAbrupted();
      }
    }
  }

  @Override
  public void visitPyReturnStatement(final PyReturnStatement node) {
    final Instruction instruction = new InstructionImpl(node, myInstructionNumber++);
    addNode(instruction);
    checkPending(instruction);
    final PyExpression expression = node.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
// Here we process pending instructions!!!
    final List<Pair<PsiElement, Instruction>> pending = myPending;
    myPending = new ArrayList<Pair<PsiElement, Instruction>>();

    for (Pair<PsiElement, Instruction> pair : pending) {
      final PsiElement pendingScope = pair.getFirst();
      if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
        final Instruction pendingInstruction = pair.getSecond();
        addPendingEdge(null, pendingInstruction);
      }
      else {
        myPending.add(pair);
      }
    }

    addPendingEdge(null, myPrevInstruction);
    flowAbrupted();
  }

  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
   startNode(node);

// process body
    final PyTryPart tryPart = node.getTryPart();
    startNode(tryPart);
    tryPart.accept(this);
    final Instruction lastBlockInstruction = myPrevInstruction;

// Goto else block after execution, or exit
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart != null) {
      startNode(elsePart);
      elsePart.accept(this);
      addPendingEdge(node, myPrevInstruction);
    } else {
      addPendingEdge(node, myPrevInstruction);
    }

    final ArrayList<Instruction> rescueInstructions = new ArrayList<Instruction>();
    for (PyExceptPart exceptPart : node.getExceptParts()) {
      myPrevInstruction = lastBlockInstruction;
      final Instruction rescueInstruction = startNode(exceptPart);
      rescueInstructions.add(rescueInstruction);
      exceptPart.accept(this);
      addPendingEdge(node, myPrevInstruction);
    }

    final PyFinallyPart finallyPart = node.getFinallyPart();
    Instruction finallyInstruction = null;
    Instruction lastFinallyInstruction = null;
    if (finallyPart != null) {
      flowAbrupted();
      finallyInstruction = startNode(finallyPart);
      finallyPart.accept(this);
      lastFinallyInstruction = myPrevInstruction;
      addPendingEdge(finallyPart, lastFinallyInstruction);
    }
    final Ref<Instruction> finallyRef = new Ref<Instruction>(finallyInstruction);
    final Ref<Instruction> lastFinallyRef = new Ref<Instruction>(lastFinallyInstruction);
    processPending(new PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        final PyElement pendingElement = instruction.getElement();

        // handle raise instructions inside compound statement
        if (pendingElement instanceof PyRaiseStatement &&
            PsiTreeUtil.isAncestor(tryPart, pendingElement, false)){
          for (Instruction rescueInstruction : rescueInstructions) {
            addEdge(instruction, rescueInstruction);
          }
          return;
        }
        // handle return pending instructions inside body if ensure block exists
        if (pendingElement instanceof PyReturnStatement && !finallyRef.isNull() &&
            PsiTreeUtil.isAncestor(node, pendingElement, false)) {
          addEdge(instruction, finallyRef.get());
          addPendingEdge(null, lastFinallyRef.get());
          return;
        }

        // Handle pending instructions inside body with ensure block
        if (pendingElement != null && finallyPart!=null && pendingScope !=finallyPart &&
            PsiTreeUtil.isAncestor(node, pendingElement, false)) {
          addEdge(instruction, finallyRef.get());
          return;
        }
        addPendingEdge(pendingScope, instruction);
      }
    });
  }

  @Override
  public void visitPyListCompExpression(final PyListCompExpression node) {
    super.visitPyListCompExpression(node);
  }


  private static interface PendingProcessor {
    void process(PsiElement pendingScope, Instruction instruction);
  }

  private void processPending(final PendingProcessor processor) {
    final List<Pair<PsiElement, Instruction>> pending = myPending;
    myPending = new ArrayList<Pair<PsiElement, Instruction>>();
    for (Pair<PsiElement, Instruction> pair : pending) {
      processor.process(pair.getFirst(), pair.getSecond());
    }
  }
}
