package com.intellij.psi.controlFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ControlFlowAnalyzer extends PsiElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ControlFlowAnalyzer");

  private final PsiElement myCodeFragment;
  private final ControlFlowPolicy myPolicy;

  private ControlFlowImpl myCurrentFlow;
  private final ControlFlowStack myStack = new ControlFlowStack();
  private final List<PsiParameter> myCatchParameters = new ArrayList<PsiParameter>();  // stack of PsiParameter for catch
  private final List<PsiElement> myCatchBlocks = new ArrayList<PsiElement>();

  private final List<PsiElement> myFinallyBlocks = new ArrayList<PsiElement>();
  private final List<PsiElement> myUnhandledExceptionCatchBlocks = new ArrayList<PsiElement>();

  // element to jump to from inner (sub)expression in "jump to begin" situation.
  // E.g. we should jump to "then" branch if condition expression evaluated to true inside if statement
  private final StatementStack myStartStatementStack = new StatementStack();
  // element to jump to from inner (sub)expression in "jump to end" situation.
  // E.g. we should jump to "else" branch if condition expression evaluated to false inside if statement
  private final StatementStack myEndStatementStack = new StatementStack();

  private final IntArrayList myStartJumpRoles = new IntArrayList();
  private final IntArrayList myEndJumpRoles = new IntArrayList();

  // true if generate direct jumps for short-circuited operations,
  // e.g. jump to else branch of if statement after each calculation of '&&' operand in condition
  private final boolean myEnabledShortCircuit;
  // true if evaluate constant expression inside 'if' statement condition and alter control flow accordingly
  // in case of unreachable statement analysis must be false
  private final boolean myEvaluateConstantIfConfition;
  private final boolean myAssignmentTargetsAreElements;

  private final List<TIntArrayList> intArrayPool = new ArrayList<TIntArrayList>();
  // map: PsiElement element -> TIntArrayList instructionOffsetsToPatch with getStartoffset(element)
  private final Map<PsiElement,TIntArrayList> offsetsAddElementStart = new THashMap<PsiElement, TIntArrayList>();
  // map: PsiElement element -> TIntArrayList instructionOffsetsToPatch with getEndOffset(element)
  private final Map<PsiElement,TIntArrayList> offsetsAddElementEnd = new THashMap<PsiElement, TIntArrayList>();
  private final ControlFlowFactory myControlFlowFactory;
  private final Map<PsiElement, ControlFlowSubRange> mySubRanges = new THashMap<PsiElement, ControlFlowSubRange>();

  ControlFlowAnalyzer(@NotNull PsiElement codeFragment,
                      @NotNull ControlFlowPolicy policy,
                      boolean enabledShortCircuit,
                      boolean evaluateConstantIfConfition) {
    this(codeFragment, policy, enabledShortCircuit, evaluateConstantIfConfition, false);
  }

  private ControlFlowAnalyzer(@NotNull PsiElement codeFragment,
                              @NotNull ControlFlowPolicy policy,
                              boolean enabledShortCircuit,
                              boolean evaluateConstantIfConfition,
                              boolean assignmentTargetsAreElements) {
    myCodeFragment = codeFragment;
    myPolicy = policy;
    myEnabledShortCircuit = enabledShortCircuit;
    myEvaluateConstantIfConfition = evaluateConstantIfConfition;
    myAssignmentTargetsAreElements = assignmentTargetsAreElements;
    myControlFlowFactory = ControlFlowFactory.getInstance(codeFragment.getProject());
  }

  @NotNull
  ControlFlow buildControlFlow() throws AnalysisCanceledException {
    // push guard outer statement offsets in case when nested expression is incorrect
    myStartJumpRoles.add(ControlFlow.JUMP_ROLE_GOTO_END);
    myEndJumpRoles.add(ControlFlow.JUMP_ROLE_GOTO_END);

    myCurrentFlow = new ControlFlowImpl();

    // guard elements
    myStartStatementStack.pushStatement(myCodeFragment, false);
    myEndStatementStack.pushStatement(myCodeFragment, false);

    try {
      myCodeFragment.accept(this);
      cleanup();
    }
    catch (AnalysisCanceledSoftException e) {
      throw new AnalysisCanceledException(e.getErrorElement());
    }

    return myCurrentFlow;
  }

  private static class StatementStack {
    private List<PsiElement> myStatements = new ArrayList<PsiElement>();
    private TIntArrayList myAtStart = new TIntArrayList();

    void popStatement() {
      myAtStart.remove(myAtStart.size() - 1);
      myStatements.remove(myStatements.size() - 1);
    }

    PsiElement peekElement() {
      return myStatements.get(myStatements.size() - 1);
    }

    boolean peekAtStart() {
      return myAtStart.get(myAtStart.size() - 1) == 1;
    }

    void pushStatement(PsiElement statement, boolean atStart) {
      myStatements.add(statement);
      myAtStart.add(atStart ? 1 : 0);
    }
  }

  private TIntArrayList getEmptyIntArray() {
    final int size = intArrayPool.size();
    if (size == 0) {
      return new TIntArrayList(1);
    }
    TIntArrayList list = intArrayPool.get(size - 1);
    intArrayPool.remove(size - 1);
    list.clear();
    return list;
  }

  private void poolIntArray(TIntArrayList list) {
    intArrayPool.add(list);
  }

  // patch instruction currently added to control flow so that its jump offset corrected on getStartOffset(element) or getEndOffset(element)
  //  when corresponding element offset become available
  private void addElementOffsetLater(PsiElement element, boolean atStart) {
    Map<PsiElement,TIntArrayList> offsetsAddElement = atStart ? offsetsAddElementStart : offsetsAddElementEnd;
    TIntArrayList offsets = offsetsAddElement.get(element);
    if (offsets == null) {
      offsets = getEmptyIntArray();
      offsetsAddElement.put(element, offsets);
    }
    int offset = myCurrentFlow.getSize() - 1;
    offsets.add(offset);
    if (myCurrentFlow.getEndOffset(element) != -1) {
      patchInstructionOffsets(element);
    }
  }


  private void patchInstructionOffsets(PsiElement element) {
    patchInstructionOffsets(offsetsAddElementStart.get(element), myCurrentFlow.getStartOffset(element));
    offsetsAddElementStart.put(element, null);
    patchInstructionOffsets(offsetsAddElementEnd.get(element), myCurrentFlow.getEndOffset(element));
    offsetsAddElementEnd.put(element, null);
  }

  private void patchInstructionOffsets(TIntArrayList offsets, int add) {
    if (offsets == null) return;
    for (int i = 0; i < offsets.size(); i++) {
      int offset = offsets.get(i);
      BranchingInstruction instruction = (BranchingInstruction)myCurrentFlow.getInstructions().get(offset);
      instruction.offset += add;
      LOG.assertTrue(instruction.offset >= 0);
//      if (instruction instanceof ReturnInstruction) {
//        final ReturnInstruction returnInstruction = ((ReturnInstruction) instruction);
//        returnInstruction.procBegin += add;
//        returnInstruction.procEnd += add;
//        LOG.assertTrue(returnInstruction.procBegin > 0);
//        LOG.assertTrue(returnInstruction.procEnd > returnInstruction.procBegin);
//      }
    }
    poolIntArray(offsets);
  }

  private void cleanup() {
    // make all non patched goto instructions jump to the end of control flow
    for (TIntArrayList offsets : offsetsAddElementStart.values()) {
      patchInstructionOffsets(offsets, myCurrentFlow.getEndOffset(myCodeFragment));
    }
    for (TIntArrayList offsets : offsetsAddElementEnd.values()) {
      patchInstructionOffsets(offsets, myCurrentFlow.getEndOffset(myCodeFragment));
    }

    // register all sub ranges
    for (PsiElement element : mySubRanges.keySet()) {
      ControlFlowSubRange subRange = mySubRanges.get(element);
      myControlFlowFactory.registerSubRange(element, subRange, myEvaluateConstantIfConfition, myPolicy);
    }
  }

  private void startElement(PsiElement element) {
    if (PsiUtil.hasErrorElementChild(element)) {
      // do not perform control flow analysis for incomplete code
      throw new AnalysisCanceledSoftException(element);
    }
    ProgressManager.getInstance().checkCanceled();
    myCurrentFlow.startElement(element);

    generateUncheckedExceptionJumpsIfNeeded(element, true);
  }

  private void generateUncheckedExceptionJumpsIfNeeded(PsiElement element, boolean atStart) {
    // optimization: reduce number of instructions
    boolean isGeneratingStatement = element instanceof PsiStatement && !(element instanceof PsiSwitchLabelStatement);
    boolean isGeneratingCodeBlock = element instanceof PsiCodeBlock && !(element.getParent() instanceof PsiSwitchStatement);
    if (isGeneratingStatement || isGeneratingCodeBlock) {
      generateUncheckedExceptionJumps(element, atStart);
    }
  }

  private void finishElement(PsiElement element) {
    generateUncheckedExceptionJumpsIfNeeded(element, false);

    myCurrentFlow.finishElement(element);
    patchInstructionOffsets(element);
  }


  private void generateUncheckedExceptionJumps(PsiElement element, boolean atStart) {
    // optimization: if we just generated all necessary jumps, do not generate it once again
    if (atStart
        && element instanceof PsiStatement
        && element.getParent() instanceof PsiCodeBlock && element.getPrevSibling() != null) {
      return;
    }

    for (int i = myUnhandledExceptionCatchBlocks.size() - 1; i >= 0; i--) {
      PsiElement block = myUnhandledExceptionCatchBlocks.get(i);
      // cannot jump to outer catch blocks (belonging to outer try stmt) if current try{} has finally block
      if (block == null) {
        if (!myFinallyBlocks.isEmpty()) {
          break;
        }
        else {
          continue;
        }
      }
      ConditionalThrowToInstruction throwToInstruction = new ConditionalThrowToInstruction(-1); // -1 for init parameter
      myCurrentFlow.addInstruction(throwToInstruction);
      if (!patchUncheckedThrowInstructionIfInsideFinally(throwToInstruction, element, block)) {
        addElementOffsetLater(block, true);
      }
    }


    // generate jump to the top finally block
    if (!myFinallyBlocks.isEmpty()) {
      final PsiElement finallyBlock = myFinallyBlocks.get(myFinallyBlocks.size() - 1);
      ConditionalThrowToInstruction throwToInstruction = new ConditionalThrowToInstruction(-2);
      myCurrentFlow.addInstruction(throwToInstruction);
      if (!patchUncheckedThrowInstructionIfInsideFinally(throwToInstruction, element, finallyBlock)) {
        addElementOffsetLater(finallyBlock, true);
      }
    }

  }

  private void generateCheckedExceptionJumps(PsiElement element) {
    //generate jumps to all handled exception handlers
    //if (myCatchBlocks.size() != 0) {
    //}
    final PsiClassType[] unhandledExceptions = ExceptionUtil.collectUnhandledExceptions(element, element.getParent());
    for (PsiClassType unhandledException : unhandledExceptions) {
      generateThrow(unhandledException, element);
    }
  }

  private void generateThrow(PsiClassType unhandledException, PsiElement throwingElement) {
    final List<PsiElement> catchBlocks = findThrowToBlocks(unhandledException);
    for (PsiElement block : catchBlocks) {
      ConditionalThrowToInstruction instruction = new ConditionalThrowToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      if (!patchCheckedThrowInstructionIfInsideFinally(instruction, throwingElement, block)) {
        if (block == null) {
          addElementOffsetLater(myCodeFragment, false);
        }
        else {
          instruction.offset--; // -1 for catch block param init
          addElementOffsetLater(block, true);
        }
      }
    }
  }

  private Map<PsiElement, List<PsiElement>> finallyBlockToUnhandledExceptions = new HashMap<PsiElement, List<PsiElement>>();

  private boolean patchCheckedThrowInstructionIfInsideFinally(ConditionalThrowToInstruction instruction,
                                                              PsiElement throwingElement,
                                                              PsiElement elementToJumpTo) {
    LOG.assertTrue(instruction != null);

    final PsiElement finallyBlock = findEnclosingFinallyBlockElement(throwingElement, elementToJumpTo);
    if (finallyBlock == null) return false;

    List<PsiElement> unhandledExceptionCatchBlocks = finallyBlockToUnhandledExceptions.get(finallyBlock);
    if (unhandledExceptionCatchBlocks == null) {
      unhandledExceptionCatchBlocks = new ArrayList<PsiElement>();
      finallyBlockToUnhandledExceptions.put(finallyBlock, unhandledExceptionCatchBlocks);
    }
    int index = unhandledExceptionCatchBlocks.indexOf(elementToJumpTo);
    if (index == -1) {
      index = unhandledExceptionCatchBlocks.size();
      unhandledExceptionCatchBlocks.add(elementToJumpTo);
    }
    // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.
    instruction.offset = 3 + index;
    addElementOffsetLater(finallyBlock, false);

    return true;
  }

  private boolean patchUncheckedThrowInstructionIfInsideFinally(ConditionalThrowToInstruction instruction,
                                                                PsiElement throwingElement,
                                                                PsiElement elementToJumpTo) {
    LOG.assertTrue(instruction != null);

    final PsiElement finallyBlock = findEnclosingFinallyBlockElement(throwingElement, elementToJumpTo);
    if (finallyBlock == null) return false;

    // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.
    instruction.offset = 2;
    addElementOffsetLater(finallyBlock, false);

    return true;
  }

  public void visitCodeFragment(PsiCodeFragment codeFragment) {
    startElement(codeFragment);
    int prevOffset = myCurrentFlow.getSize();
    PsiElement[] children = codeFragment.getChildren();
    for (PsiElement aChildren : children) {
      aChildren.accept(this);
    }

    finishElement(codeFragment);
    registerSubRange(codeFragment, prevOffset);
  }

  private void registerSubRange(final PsiElement codeFragment, final int startOffset) {
    // cache child code block in hope it will be needed
    ControlFlowSubRange flow = new ControlFlowSubRange(myCurrentFlow, startOffset, myCurrentFlow.getSize());
    // register it later since offset may not have been patched yet
    mySubRanges.put(codeFragment, flow);
  }

  public void visitCodeBlock(PsiCodeBlock block) {
    startElement(block);
    int prevOffset = myCurrentFlow.getSize();
    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      statement.accept(this);
    }

    //each statement should contain at least one instruction in order to getElement(offset) work
    int nextOffset = myCurrentFlow.getSize();
    if (!(block.getParent() instanceof PsiSwitchStatement) && prevOffset == nextOffset) {
      emitEmptyInstruction();
    }

    finishElement(block);
    registerSubRange(block, prevOffset);
  }

  private void emitEmptyInstruction() {
    myCurrentFlow.addInstruction(EmptyInstruction.INSTANCE);
  }

  public void visitJspFile(JspFile file) {
    visitChildren(file);
  }

  public void visitBlockStatement(PsiBlockStatement statement) {
    startElement(statement);
    final PsiCodeBlock codeBlock = statement.getCodeBlock();
    codeBlock.accept(this);
    finishElement(statement);
  }

  public void visitBreakStatement(PsiBreakStatement statement) {
    startElement(statement);
    PsiStatement exitedStatement = statement.findExitedStatement();
    if (exitedStatement != null) {
      final Instruction instruction;
      final PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, exitedStatement);
      final int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
      if (finallyBlock != null && finallyStartOffset != -1) {
        // go out of finally, use return
        CallInstruction callInstruction = (CallInstruction)myCurrentFlow.getInstructions().get(finallyStartOffset - 2);
        instruction = new ReturnInstruction(0, myStack, callInstruction);
      }
      else {
        instruction = new GoToInstruction(0);
      }
      myCurrentFlow.addInstruction(instruction);
      // exited statement might be out of control flow analyzed
      addElementOffsetLater(exitedStatement, false);
    }
    finishElement(statement);
  }

  private PsiElement findEnclosingFinallyBlockElement(PsiElement sourceElement, PsiElement jumpElement) {
    PsiElement element = sourceElement;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiCodeBlock
          && element.getParent() instanceof PsiTryStatement
          && ((PsiTryStatement)element.getParent()).getFinallyBlock() == element) {
        // element maybe out of scope to be analyzed
        if (myCurrentFlow.getStartOffset(element.getParent()) == -1) return null;
        if (jumpElement == null || !PsiTreeUtil.isAncestor(element, jumpElement, false)) return element;
      }
      element = element.getParent();
    }
    return null;
  }

  public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement != null) {
      PsiElement body = null;
      if (continuedStatement instanceof PsiForStatement) {
        body = ((PsiForStatement)continuedStatement).getBody();
      }
      else if (continuedStatement instanceof PsiWhileStatement) {
        body = ((PsiWhileStatement)continuedStatement).getBody();
      }
      else if (continuedStatement instanceof PsiDoWhileStatement) {
        body = ((PsiDoWhileStatement)continuedStatement).getBody();
      }
      else if (continuedStatement instanceof PsiForeachStatement) {
        body = ((PsiForeachStatement)continuedStatement).getBody();
      }
      if (body == null) {
        body = myCodeFragment;
      }
      final Instruction instruction;
      final PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, continuedStatement);
      final int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
      if (finallyBlock != null && finallyStartOffset != -1) {
        // go out of finally, use return
        CallInstruction callInstruction = (CallInstruction)myCurrentFlow.getInstructions().get(finallyStartOffset - 2);
        instruction = new ReturnInstruction(0, myStack, callInstruction);
      }
      else {
        instruction = new GoToInstruction(0);
      }
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(body, false);
    }
    finishElement(statement);
  }

  public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    startElement(statement);
    int pc = myCurrentFlow.getSize();
    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        element.accept(this);
      }
      else if (element instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable)element).getInitializer();
        if (initializer != null) {
          myStartStatementStack.pushStatement(initializer, false);
          myEndStatementStack.pushStatement(initializer, false);
          initializer.accept(this);
          myStartStatementStack.popStatement();
          myEndStatementStack.popStatement();
        }
        if (element instanceof PsiLocalVariable && initializer != null
            || element instanceof PsiField) {
          if (element instanceof PsiLocalVariable && !myPolicy.isLocalVariableAccepted((PsiLocalVariable)element)) continue;

          if (myAssignmentTargetsAreElements) {
            startElement(element);
          }

          generateWriteInstruction((PsiVariable)element);

          if (myAssignmentTargetsAreElements) {
            finishElement(element);
          }
        }
      }
    }
    if (pc == myCurrentFlow.getSize()) {
      // generate at least one instruction for declaration
      emitEmptyInstruction();
    }
    finishElement(statement);
  }

  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    startElement(statement);
    myStartStatementStack.pushStatement(statement.getBody() == null ? statement : statement.getBody(), true);
    myEndStatementStack.pushStatement(statement, false);

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }

    int offset = myCurrentFlow.getStartOffset(statement);

    Object loopCondition = statement.getManager().getConstantEvaluationHelper().computeConstantExpression(statement.getCondition());
    if (loopCondition instanceof Boolean) {
      if (((Boolean)loopCondition).booleanValue()) {
        myCurrentFlow.addInstruction(new GoToInstruction(offset));
      }
      else {
        emitEmptyInstruction();
      }

    }
    else {
      Instruction instruction = new ConditionalGoToInstruction(offset);
      myCurrentFlow.addInstruction(instruction);
    }

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  public void visitEmptyStatement(PsiEmptyStatement statement) {
    startElement(statement);
    emitEmptyInstruction();

    finishElement(statement);
  }

  public void visitExpressionStatement(PsiExpressionStatement statement) {
    startElement(statement);
    final PsiExpression expression = statement.getExpression();
    expression.accept(this);

    for (PsiParameter catchParameter : myCatchParameters) {
      PsiType type = catchParameter.getType();
      if (type instanceof PsiClassType) {
        generateThrow((PsiClassType)type, statement);
      }
    }
    finishElement(statement);
  }

  public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    startElement(statement);
    PsiExpression[] expressions = statement.getExpressionList().getExpressions();
    for (PsiExpression expr : expressions) {
      expr.accept(this);
    }
    finishElement(statement);
  }

  public void visitField(PsiField field) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      startElement(field);
      initializer.accept(this);
      finishElement(field);
    }
  }

  public void visitForStatement(PsiForStatement statement) {
    startElement(statement);
    myStartStatementStack.pushStatement(statement.getBody() == null ? statement : statement.getBody(), false);
    myEndStatementStack.pushStatement(statement, false);

    PsiStatement initialization = statement.getInitialization();
    if (initialization != null) {
      initialization.accept(this);
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }


    Object loopCondition = statement.getManager().getConstantEvaluationHelper().computeConstantExpression(condition);
    if (loopCondition instanceof Boolean || condition == null) {
      boolean value = condition == null || ((Boolean)loopCondition).booleanValue();
      if (value) {
        emitEmptyInstruction();
      }
      else {
        myCurrentFlow.addInstruction(new GoToInstruction(0));
        addElementOffsetLater(statement, false);
      }
    }
    else {
      Instruction instruction = new ConditionalGoToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(statement, false);
    }

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    PsiStatement update = statement.getUpdate();
    if (update != null) {
      update.accept(this);
    }

    int offset = initialization != null
                 ? myCurrentFlow.getEndOffset(initialization)
                 : myCurrentFlow.getStartOffset(statement);
    Instruction instruction = new GoToInstruction(offset);
    myCurrentFlow.addInstruction(instruction);

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    startElement(statement);
    final PsiStatement body = statement.getBody();
    myStartStatementStack.pushStatement(body == null ? statement : body, false);
    myEndStatementStack.pushStatement(statement, false);
    final PsiExpression iteratedValue = statement.getIteratedValue();
    if (iteratedValue != null) {
      iteratedValue.accept(this);
    }

    final int gotoTarget = myCurrentFlow.getSize();
    Instruction instruction = new ConditionalGoToInstruction(0);
    myCurrentFlow.addInstruction(instruction);
    addElementOffsetLater(statement, false);

    final PsiParameter iterationParameter = statement.getIterationParameter();
    if (myPolicy.isParameterAccepted(iterationParameter)) {
      generateWriteInstruction(iterationParameter);
    }
    if (body != null) {
      body.accept(this);
    }

    final GoToInstruction gotoInstruction = new GoToInstruction(gotoTarget);
    myCurrentFlow.addInstruction(gotoInstruction);
    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    final PsiStatement elseBranch = statement.getElseBranch();
    final PsiStatement thenBranch = statement.getThenBranch();
    PsiExpression conditionExpression = statement.getCondition();

    generateConditionalStatementInstructions(statement, conditionExpression, thenBranch, elseBranch);

    finishElement(statement);
  }

  private void generateConditionalStatementInstructions(PsiElement statement,
                                                        PsiExpression conditionExpression,
                                                        final PsiElement thenBranch,
                                                        final PsiElement elseBranch) {
    if (thenBranch == null) {
      myStartStatementStack.pushStatement(statement, false);
    }
    else {
      myStartStatementStack.pushStatement(thenBranch, true);
    }
    if (elseBranch == null) {
      myEndStatementStack.pushStatement(statement, false);
    }
    else {
      myEndStatementStack.pushStatement(elseBranch, true);
    }

    myEndJumpRoles.add(elseBranch == null ? ControlFlow.JUMP_ROLE_GOTO_END : ControlFlow.JUMP_ROLE_GOTO_ELSE);
    myStartJumpRoles.add(thenBranch == null ? ControlFlow.JUMP_ROLE_GOTO_END : ControlFlow.JUMP_ROLE_GOTO_THEN);

    if (conditionExpression != null) {
      conditionExpression.accept(this);
    }

    boolean generateElseFlow = true;
    boolean generateThenFlow = true;
    boolean generateConditionalJump = true;
    /**
     * if() statement generated instructions outline:
     *  'if (C) { A } [ else { B } ]' :
     *     generate (C)
     *     cond_goto else
     *     generate (A)
     *     [ goto end ]
     * :else
     *     [ generate (B) ]
     * :end
     */
    if (myEvaluateConstantIfConfition) {
      final Object value = statement.getManager().getConstantEvaluationHelper().computeConstantExpression(conditionExpression);
      if (value instanceof Boolean) {
        boolean condition = ((Boolean)value).booleanValue();
        generateThenFlow = condition;
        generateElseFlow = !condition;
        generateConditionalJump = false;
        myCurrentFlow.setConstantConditionOccurred(true);
      }
    }
    if (generateConditionalJump) {
      Instruction instruction = new ConditionalGoToInstruction(0,
                                                               elseBranch == null
                                                               ? ControlFlow.JUMP_ROLE_GOTO_END
                                                               : ControlFlow.JUMP_ROLE_GOTO_ELSE);
      myCurrentFlow.addInstruction(instruction);
      if (elseBranch != null) {
        addElementOffsetLater(elseBranch, true);
      }
      else {
        addElementOffsetLater(statement, false);
      }
    }
    if (thenBranch != null && generateThenFlow) {
      thenBranch.accept(this);
    }
    if (elseBranch != null && generateElseFlow) {
      if (generateThenFlow) {
        // make jump to end after then branch (only if it has been generated)
        Instruction instruction = new GoToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(statement, false);
      }
      elseBranch.accept(this);
    }

    myStartJumpRoles.remove(myStartJumpRoles.size() - 1);
    myEndJumpRoles.remove(myEndJumpRoles.size() - 1);

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
  }

  public void visitLabeledStatement(PsiLabeledStatement statement) {
    startElement(statement);
    final PsiStatement innerStatement = statement.getStatement();
    if (innerStatement != null) {
      innerStatement.accept(this);
    }
    finishElement(statement);
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);
    PsiExpression returnValue = statement.getReturnValue();

    myStartStatementStack.pushStatement(returnValue, false);
    myEndStatementStack.pushStatement(returnValue, false);

    if (returnValue != null) {
      returnValue.accept(this);
    }
    addReturnInstruction(statement);
    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();

    finishElement(statement);
  }

  private void addReturnInstruction(PsiElement statement) {
    BranchingInstruction instruction;
    final PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, null);
    final int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
    if (finallyBlock != null && finallyStartOffset != -1) {
      // go out of finally, go to 2nd return after finally block
      // second return is for return statement called completion
      instruction = new GoToInstruction(2, ControlFlow.JUMP_ROLE_GOTO_END, true);
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(finallyBlock, false);
    }
    else {
      instruction = new GoToInstruction(0, ControlFlow.JUMP_ROLE_GOTO_END, true);
      myCurrentFlow.addInstruction(instruction);
      if (myFinallyBlocks.isEmpty()) {
        addElementOffsetLater(myCodeFragment, false);
      }
      else {
        instruction.offset = -4; // -4 for return
        addElementOffsetLater(myFinallyBlocks.get(myFinallyBlocks.size() - 1), true);
      }
    }
  }

  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    PsiExpression caseValue = statement.getCaseValue();

    myStartStatementStack.pushStatement(caseValue, false);
    myEndStatementStack.pushStatement(caseValue, false);

    if (caseValue != null) caseValue.accept(this);

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();

    finishElement(statement);
  }

  public void visitSwitchStatement(PsiSwitchStatement statement) {
    startElement(statement);

    PsiExpression expr = statement.getExpression();
    if (expr != null) {
      expr.accept(this);
    }

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      PsiSwitchLabelStatement defaultLabel = null;
      for (PsiStatement aStatement : statements) {
        if (aStatement instanceof PsiSwitchLabelStatement) {
          if (((PsiSwitchLabelStatement)aStatement).isDefaultCase()) {
            defaultLabel = (PsiSwitchLabelStatement)aStatement;
          }
          Instruction instruction = new ConditionalGoToInstruction(0);
          myCurrentFlow.addInstruction(instruction);
          addElementOffsetLater(aStatement, true);
        }
      }
      if (defaultLabel == null) {
        Instruction instruction = new GoToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(body, false);
      }

      body.accept(this);
    }

    finishElement(statement);
  }

  public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
    }

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    finishElement(statement);
  }

  public void visitThrowStatement(PsiThrowStatement statement) {
    startElement(statement);

    PsiExpression exception = statement.getException();
    if (exception != null) {
      exception.accept(this);
    }
    final List<PsiElement> blocks = findThrowToBlocks(statement);
    PsiElement element;
    if (blocks.isEmpty() || blocks.get(0) == null) {
      ThrowToInstruction instruction = new ThrowToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      if (myFinallyBlocks.isEmpty()) {
        element = myCodeFragment;
        addElementOffsetLater(element, false);
      }
      else {
        instruction.offset = -2; // -2 to rethrow exception
        element = myFinallyBlocks.get(myFinallyBlocks.size() - 1);
        addElementOffsetLater(element, true);
      }
    }
    else {
      for (int i = 0; i < blocks.size(); i++) {
        element = blocks.get(i);
        BranchingInstruction instruction = i == blocks.size() - 1
                                           ? new ThrowToInstruction(0)
                                           : new ConditionalThrowToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        instruction.offset = -1; // -1 to init catch param
        addElementOffsetLater(element, true);
      }
    }


    finishElement(statement);
  }

  /**
   * find offsets of catch(es) corresponding to this throw statement
   * mycatchParameters and mycatchpoints arrays should be sorted in ascending scope order (from outermost to innermost)
   *
   * @return offset or -1 if not found
   */
  private List<PsiElement> findThrowToBlocks(PsiThrowStatement statement) {
    final PsiExpression exceptionExpr = statement.getException();
    if (exceptionExpr == null) return Collections.emptyList();
    final PsiType throwType = exceptionExpr.getType();
    if (!(throwType instanceof PsiClassType)) return Collections.emptyList();
    return findThrowToBlocks((PsiClassType)throwType);
  }

  private List<PsiElement> findThrowToBlocks(PsiClassType throwType) {
    List<PsiElement> blocks = new ArrayList<PsiElement>();
    for (int i = myCatchParameters.size() - 1; i >= 0; i--) {
      PsiParameter parameter = myCatchParameters.get(i);
      final PsiType type = parameter.getType();
      PsiClass catchedClass = PsiUtil.resolveClassInType(type);
      if (catchedClass == null) continue;
      if (type.isAssignableFrom(throwType)) {
        blocks.add(myCatchBlocks.get(i));
      }
      else if (throwType.isAssignableFrom(type)) {
        blocks.add(myCatchBlocks.get(i));
      }
    }
    if (blocks.isEmpty()) {
      // consider it as throw at the end of the control flow
      blocks.add(null);
    }
    return blocks;
  }

  public void visitAssertStatement(PsiAssertStatement statement) {
    startElement(statement);

    // should not try to compute constant expression within assert
    // since assertions can be disabled/enabled at any moment via JVM flags

    final PsiExpression condition = statement.getAssertCondition();
    if (condition != null) {
      myStartStatementStack.pushStatement(statement, false);
      myEndStatementStack.pushStatement(statement, false);

      myEndJumpRoles.add(ControlFlow.JUMP_ROLE_GOTO_END);
      myStartJumpRoles.add(ControlFlow.JUMP_ROLE_GOTO_END);

      condition.accept(this);

      myStartJumpRoles.remove(myStartJumpRoles.size() - 1);
      myEndJumpRoles.remove(myEndJumpRoles.size() - 1);

      myStartStatementStack.popStatement();
      myEndStatementStack.popStatement();
    }
    PsiExpression description = statement.getAssertDescription();
    if (description != null) {
      description.accept(this);
    }

    Instruction instruction = new ConditionalThrowToInstruction(0);
    myCurrentFlow.addInstruction(instruction);
    addElementOffsetLater(myCodeFragment, false);

    finishElement(statement);
  }

  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
    PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
    int catchNum = Math.min(catchBlocks.length, catchBlockParameters.length);
    myUnhandledExceptionCatchBlocks.add(null);
    for (int i = catchNum - 1; i >= 0; i--) {
      myCatchParameters.add(catchBlockParameters[i]);
      myCatchBlocks.add(catchBlocks[i]);

      final PsiType type = catchBlockParameters[i].getType();
      // todo cast param
      if (type instanceof PsiClassType &&
          ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)type)) {
        myUnhandledExceptionCatchBlocks.add(catchBlocks[i]);
      }
    }

    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    if (finallyBlock != null) {
      myFinallyBlocks.add(finallyBlock);
    }

    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      // javac works as if all checked exceptions can occur at the top of the block
      generateCheckedExceptionJumps(tryBlock);
      tryBlock.accept(this);
    }

    while (myUnhandledExceptionCatchBlocks.remove(myUnhandledExceptionCatchBlocks.size() - 1) != null) ;

    myCurrentFlow.addInstruction(new GoToInstruction(finallyBlock == null ? 0 : -6));
    if (finallyBlock == null) {
      addElementOffsetLater(statement, false);
    }
    else {
      addElementOffsetLater(finallyBlock, true);
    }

    for (int i = 0; i < catchNum; i++) {
      myCatchParameters.remove(myCatchParameters.size() - 1);
      myCatchBlocks.remove(myCatchBlocks.size() - 1);
    }

    for (int i = catchNum - 1; i >= 0; i--) {
      if (myPolicy.isParameterAccepted(catchBlockParameters[i])) {
        generateWriteInstruction(catchBlockParameters[i]);
      }
      PsiCodeBlock catchBlock = catchBlocks[i];
      assert catchBlock != null : statement.getText();
      catchBlock.accept(this);

      myCurrentFlow.addInstruction(new GoToInstruction(finallyBlock == null ? 0 : -6));
      if (finallyBlock == null) {
        addElementOffsetLater(statement, false);
      }
      else {
        addElementOffsetLater(finallyBlock, true);
      }
    }

    if (finallyBlock != null) {
      myFinallyBlocks.remove(myFinallyBlocks.size() - 1);
    }

    if (finallyBlock != null) {
      // normal completion, call finally block and proceed
      myCurrentFlow.addInstruction(new CallInstruction(0, 0, myStack));
      addElementOffsetLater(finallyBlock, true);
      myCurrentFlow.addInstruction(new GoToInstruction(0));
      addElementOffsetLater(statement, false);
      // return completion, call finally block and return
      myCurrentFlow.addInstruction(new CallInstruction(0, 0, myStack));
      addElementOffsetLater(finallyBlock, true);
      addReturnInstruction(statement);
      // throw exception completion, call finally block and rethrow
      myCurrentFlow.addInstruction(new CallInstruction(0, 0, myStack));
      addElementOffsetLater(finallyBlock, true);
      final GoToInstruction gotoUncheckedRethrow = new GoToInstruction(0);
      myCurrentFlow.addInstruction(gotoUncheckedRethrow);
      addElementOffsetLater(finallyBlock, false);

      finallyBlock.accept(this);
      final int procStart = myCurrentFlow.getStartOffset(finallyBlock);
      final int procEnd = myCurrentFlow.getEndOffset(finallyBlock);
      int offset = procStart - 6;
      final List<Instruction> instructions = myCurrentFlow.getInstructions();
      CallInstruction callInstruction = (CallInstruction)instructions.get(offset);
      callInstruction.procBegin = procStart;
      callInstruction.procEnd = procEnd;
      offset += 2;
      callInstruction = (CallInstruction)instructions.get(offset);
      callInstruction.procBegin = procStart;
      callInstruction.procEnd = procEnd;
      offset += 2;
      callInstruction = (CallInstruction)instructions.get(offset);
      callInstruction.procBegin = procStart;
      callInstruction.procEnd = procEnd;

      // generate return instructions
      // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.

      // normal completion
      myCurrentFlow.addInstruction(new ReturnInstruction(0, myStack, callInstruction));

      // return statement call completion
      myCurrentFlow.addInstruction(new ReturnInstruction(procStart - 3, myStack, callInstruction));

      // unchecked exception throwing completion
      myCurrentFlow.addInstruction(new ReturnInstruction(procStart - 1, myStack, callInstruction));

      // checked exception throwing completion. need to dispatch to the correct catch clause
      final List<PsiElement> unhandledExceptionCatchBlocks = finallyBlockToUnhandledExceptions.remove(finallyBlock);
      for (int i = 0; unhandledExceptionCatchBlocks != null && i < unhandledExceptionCatchBlocks.size(); i++) {
        PsiElement catchBlock = unhandledExceptionCatchBlocks.get(i);

        final ReturnInstruction returnInstruction = new ReturnInstruction(0, myStack, callInstruction);
        myCurrentFlow.addInstruction(returnInstruction);
        if (catchBlock == null) {
          // dispatch to rethrowing exception code
          returnInstruction.offset = procStart - 1;
        }
        else {
          // dispatch to catch clause
          returnInstruction.offset--; // -1 for catch block init parameter instruction
          addElementOffsetLater(catchBlock, true);
        }
      }

      // here generated rethrowing code for unchecked exceptions
      gotoUncheckedRethrow.offset = myCurrentFlow.getSize();
      generateUncheckedExceptionJumps(statement, false);
      // just in case
      myCurrentFlow.addInstruction(new ThrowToInstruction(0));
      addElementOffsetLater(myCodeFragment, false);
    }

    finishElement(statement);
  }


  public void visitWhileStatement(PsiWhileStatement statement) {
    startElement(statement);
    if (statement.getBody() == null) {
      myStartStatementStack.pushStatement(statement, false);
    }
    else {
      myStartStatementStack.pushStatement(statement.getBody(), true);
    }
    myEndStatementStack.pushStatement(statement, false);

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }


    Object loopCondition = statement.getManager().getConstantEvaluationHelper().computeConstantExpression(statement.getCondition());
    if (loopCondition instanceof Boolean) {
      boolean value = ((Boolean)loopCondition).booleanValue();
      if (value) {
        emitEmptyInstruction();
      }
      else {
        myCurrentFlow.addInstruction(new GoToInstruction(0));
        addElementOffsetLater(statement, false);
      }
    }
    else {
      Instruction instruction = new ConditionalGoToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(statement, false);
    }

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }
    int offset = myCurrentFlow.getStartOffset(statement);
    Instruction instruction = new GoToInstruction(offset);
    myCurrentFlow.addInstruction(instruction);

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  public void visitExpressionList(PsiExpressionList list) {
    PsiExpression[] expressions = list.getExpressions();
    for (final PsiExpression expression : expressions) {
      myStartStatementStack.pushStatement(expression, false);
      myEndStatementStack.pushStatement(expression, false);

      expression.accept(this);
      myStartStatementStack.popStatement();
      myEndStatementStack.popStatement();
    }
  }

  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    startElement(expression);

    expression.getArrayExpression().accept(this);
    final PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
    }

    finishElement(expression);
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    startElement(expression);

    PsiExpression[] initializers = expression.getInitializers();
    for (PsiExpression initializer : initializers) {
      initializer.accept(this);
    }

    finishElement(expression);
  }

  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    startElement(expression);

    myStartStatementStack.pushStatement(expression.getRExpression() == null ? expression : expression.getRExpression(), false);
    myEndStatementStack.pushStatement(expression.getRExpression() == null ? expression : expression.getRExpression(), false);

    PsiExpression rExpr = expression.getRExpression();
    if (rExpr != null) {
      rExpr.accept(this);
    }

    PsiExpression lExpr = expression.getLExpression();
    if (lExpr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lExpr;
      if (!referenceExpression.isQualified()
          || referenceExpression.getQualifierExpression() instanceof PsiThisExpression) {

        PsiVariable variable = getUsedVariable(referenceExpression);
        if (variable != null) {
          if (myAssignmentTargetsAreElements)
            startElement(lExpr);

          if (expression.getOperationSign().getTokenType() != JavaTokenType.EQ) {
            generateReadInstruction(variable);
          }
          generateWriteInstruction(variable);

          if (myAssignmentTargetsAreElements) finishElement(lExpr);
        }

      }
      else {
        lExpr.accept(this); //?
      }
    }
    else {
      lExpr.accept(this);
    }

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();

    finishElement(expression);
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    startElement(expression);

    final PsiExpression lOperand = expression.getLOperand();
    lOperand.accept(this);

    PsiConstantEvaluationHelper evalHelper = expression.getManager().getConstantEvaluationHelper();
    IElementType signTokenType = expression.getOperationSign().getTokenType();
    if (signTokenType == JavaTokenType.ANDAND) {
      if (myEnabledShortCircuit) {
        final Object exprValue = evalHelper.computeConstantExpression(lOperand);
        if (exprValue instanceof Boolean) {
          myCurrentFlow.setConstantConditionOccurred(true);
        }
        if (calculateConstantExpression(expression) && exprValue instanceof Boolean) {
          if (!((Boolean)exprValue).booleanValue()) {
            myCurrentFlow.addInstruction(new GoToInstruction(0, myEndJumpRoles.get(myEndJumpRoles.size() - 1)));
            addElementOffsetLater(myEndStatementStack.peekElement(), myEndStatementStack.peekAtStart());
          }
        }
        else {
          myCurrentFlow.addInstruction(new ConditionalGoToInstruction(0, myEndJumpRoles.get(myEndJumpRoles.size() - 1)));
          addElementOffsetLater(myEndStatementStack.peekElement(), myEndStatementStack.peekAtStart());
        }
      }
      else {
        Instruction instruction = new ConditionalGoToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(expression, false);
      }
    }
    else if (signTokenType == JavaTokenType.OROR) {
      if (myEnabledShortCircuit) {
        final Object exprValue = evalHelper.computeConstantExpression(lOperand);
        if (exprValue instanceof Boolean) {
          myCurrentFlow.setConstantConditionOccurred(true);
        }
        if (calculateConstantExpression(expression) && exprValue instanceof Boolean) {
          if (((Boolean)exprValue).booleanValue()) {
            myCurrentFlow.addInstruction(new GoToInstruction(0, myStartJumpRoles.get(myStartJumpRoles.size() - 1)));
            addElementOffsetLater(myStartStatementStack.peekElement(), myStartStatementStack.peekAtStart());
          }
        }
        else {
          myCurrentFlow.addInstruction(new ConditionalGoToInstruction(0, myStartJumpRoles.get(myStartJumpRoles.size() - 1)));
          addElementOffsetLater(myStartStatementStack.peekElement(), myStartStatementStack.peekAtStart());
        }
      }
      else {
        Instruction instruction = new ConditionalGoToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(expression, false);
      }
    }

    final PsiExpression rOperand = expression.getROperand();
    if (rOperand != null) {
      rOperand.accept(this);
    }

    finishElement(expression);
  }

  private static boolean isInsideIfCondition(PsiExpression expression) {
    PsiElement element = expression;
    while (element instanceof PsiExpression) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiIfStatement && element == ((PsiIfStatement)parent).getCondition()) return true;
      element = parent;
    }
    return false;
  }

  private boolean calculateConstantExpression(PsiExpression expression) {
    return myEvaluateConstantIfConfition || !isInsideIfCondition(expression);
  }


  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    visitChildren(expression);

  }

  private void visitChildren(PsiElement element) {
    startElement(element);

    PsiElement[] children = element.getChildren();
    for (PsiElement aChildren : children) {
      aChildren.accept(this);
    }

    finishElement(element);
  }

  public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    final PsiExpression condition = expression.getCondition();
    final PsiExpression thenExpression = expression.getThenExpression();
    final PsiExpression elseExpression = expression.getElseExpression();
    generateConditionalStatementInstructions(expression, condition, thenExpression, elseExpression);

    finishElement(expression);
  }

  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    startElement(expression);

    final PsiExpression operand = expression.getOperand();
    operand.accept(this);

    finishElement(expression);
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);
    finishElement(expression);
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    startElement(expression);

    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    methodExpression.accept(this);
    final PsiExpressionList argumentList = expression.getArgumentList();
    argumentList.accept(this);
    // just to increase counter - there is some executable code here
    emitEmptyInstruction();

    generateCheckedExceptionJumps(expression);

    finishElement(expression);
  }

  public void visitNewExpression(PsiNewExpression expression) {
    startElement(expression);

    int pc = myCurrentFlow.getSize();
    PsiElement[] children = expression.getChildren();
    for (PsiElement aChildren : children) {
      aChildren.accept(this);
    }
    generateCheckedExceptionJumps(expression);

    if (pc == myCurrentFlow.getSize()) {
      // generate at least one instruction for constructor call
      emitEmptyInstruction();
    }

    finishElement(expression);
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    visitChildren(expression);
  }

  public void visitPostfixExpression(PsiPostfixExpression expression) {
    startElement(expression);

    String op = expression.getOperationSign().getText();
    PsiExpression operand = expression.getOperand();
    operand.accept(this);
    if (op.equals("++") || op.equals("--")) {
      if (operand instanceof PsiReferenceExpression) {
        PsiVariable variable = getUsedVariable((PsiReferenceExpression)operand);
        if (variable != null) {
          generateWriteInstruction(variable);
        }
      }
    }

    finishElement(expression);
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    PsiExpression operand = expression.getOperand();
    if (operand != null) {
      IElementType operationSign = expression.getOperationSign().getTokenType();
      if (operationSign == JavaTokenType.EXCL) {
        // negation inverts jump targets
        PsiElement topStartStatement = myStartStatementStack.peekElement();
        boolean topAtStart = myStartStatementStack.peekAtStart();
        myStartStatementStack.pushStatement(myEndStatementStack.peekElement(), myEndStatementStack.peekAtStart());
        myEndStatementStack.pushStatement(topStartStatement, topAtStart);
      }

      operand.accept(this);

      if (operationSign == JavaTokenType.EXCL) {
        // negation inverts jump targets
        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
      }

      if (operand instanceof PsiReferenceExpression &&
          (operationSign == JavaTokenType.PLUSPLUS || operationSign == JavaTokenType.MINUSMINUS)) {
        PsiVariable variable = getUsedVariable((PsiReferenceExpression)operand);
        if (variable != null) {
          generateWriteInstruction(variable);
        }
      }
    }

    finishElement(expression);
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(this);
    }

    PsiVariable variable = getUsedVariable(expression);
    if (variable != null) {
      generateReadInstruction(variable);
    }

    finishElement(expression);
  }


  public void visitSuperExpression(PsiSuperExpression expression) {
    startElement(expression);
    finishElement(expression);
  }

  public void visitThisExpression(PsiThisExpression expression) {
    startElement(expression);
    finishElement(expression);
  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    startElement(expression);
    PsiExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);

    }
    finishElement(expression);
  }

  public void visitClass(PsiClass aClass) {
    startElement(aClass);
    // anonymous or local class
    if (aClass instanceof PsiAnonymousClass) {
      final PsiElement arguments = PsiTreeUtil.getChildOfType(aClass, PsiExpressionList.class);
      if (arguments != null) arguments.accept(this);
    }
    List<PsiVariable> array = new ArrayList<PsiVariable>();
    addUsedVariables(array, aClass);
    for (PsiVariable var : array) {
      generateReadInstruction(var);
    }
    finishElement(aClass);
  }

  private void addUsedVariables(List<PsiVariable> array, PsiElement scope) {
    if (scope instanceof PsiReferenceExpression) {
      PsiVariable variable = getUsedVariable((PsiReferenceExpression)scope);
      if (variable != null) {
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement aChildren : children) {
      addUsedVariables(array, aChildren);
    }
  }

  private void generateReadInstruction(PsiVariable variable) {
    Instruction instruction = new ReadVariableInstruction(variable);
    myCurrentFlow.addInstruction(instruction);
  }

  private void generateWriteInstruction(PsiVariable variable) {
    Instruction instruction = new WriteVariableInstruction(variable);
    myCurrentFlow.addInstruction(instruction);
  }

  private PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
    if (refExpr.getParent() instanceof PsiMethodCallExpression) return null;
    return myPolicy.getUsedVariable(refExpr);
  }
}
