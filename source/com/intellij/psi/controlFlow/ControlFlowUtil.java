package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;

import java.util.*;

public class ControlFlowUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ControlFlowUtil");

  private static class SSAInstructionState implements Cloneable {
    private int myWriteCount;
    private int myInstructionIdx;

    public SSAInstructionState(int writeCount, int instructionIdx) {
      myWriteCount = writeCount;
      myInstructionIdx = instructionIdx;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SSAInstructionState)) return false;

      final SSAInstructionState ssaInstructionState = (SSAInstructionState)o;

      if (myInstructionIdx != ssaInstructionState.myInstructionIdx) return false;
      if (Math.min(2, myWriteCount) != Math.min(2, ssaInstructionState.myWriteCount)) return false;

      return true;
    }

    public int hashCode() {
      int result = Math.min(2, myWriteCount);
      result = 29 * result + myInstructionIdx;
      return result;
    }

    public int getWriteCount() {
      return myWriteCount;
    }

    public int getInstructionIdx() {
      return myInstructionIdx;
    }
  }

  public static PsiVariable[] getSSAVariables(ControlFlow flow, boolean reportVarsIfNonInitializingPathExists) {
    return getSSAVariables(flow, 0, flow.getSize(), reportVarsIfNonInitializingPathExists);
  }

  public static PsiVariable[] getSSAVariables(ControlFlow flow, int from, int to,
                                              boolean reportVarsIfNonInitializingPathExists) {
    List<Instruction> instructions = flow.getInstructions();
    PsiVariable[] writtenVariables = getWrittenVariables(flow, from, to, false);
    ArrayList<PsiVariable> result = new ArrayList<PsiVariable>(1);

    variables:
    for (PsiVariable psiVariable : writtenVariables) {

      final List<SSAInstructionState> queue = new ArrayList<SSAInstructionState>();
      queue.add(new SSAInstructionState(0, from));
      Set<SSAInstructionState> processedStates = new THashSet<SSAInstructionState>();

      while (queue.size() > 0) {
        final SSAInstructionState state = queue.remove(0);
        if (state.getWriteCount() > 1) continue variables;
        if (!processedStates.contains(state)) {
          processedStates.add(state);
          int i = state.getInstructionIdx();
          if (i < to) {
            Instruction instruction = instructions.get(i);

            if (instruction instanceof ReturnInstruction) {
              int[] offsets = ((ReturnInstruction)instruction).getPossibleReturnOffsets();
              for (int offset : offsets) {
                queue.add(new SSAInstructionState(state.getWriteCount(), Math.min(offset, to)));
              }
            }
            else if (instruction instanceof GoToInstruction) {
              int nextOffset = ((GoToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
            }
            else if (instruction instanceof ThrowToInstruction) {
              int nextOffset = ((ThrowToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
            }
            else if (instruction instanceof ConditionalGoToInstruction) {
              int nextOffset = ((ConditionalGoToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else if (instruction instanceof ConditionalThrowToInstruction) {
              int nextOffset = ((ConditionalThrowToInstruction)instruction).offset;
              nextOffset = Math.min(nextOffset, to);
              queue.add(new SSAInstructionState(state.getWriteCount(), nextOffset));
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else if (instruction instanceof WriteVariableInstruction) {
              WriteVariableInstruction write = (WriteVariableInstruction)instruction;
              queue.add(new SSAInstructionState(state.getWriteCount() + (write.variable == psiVariable ? 1 : 0), i + 1));
            }
            else if (instruction instanceof ReadVariableInstruction) {
              ReadVariableInstruction read = (ReadVariableInstruction)instruction;
              if (read.variable == psiVariable && state.getWriteCount() == 0) continue variables;
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
            else {
              queue.add(new SSAInstructionState(state.getWriteCount(), i + 1));
            }
          }
          else if (!reportVarsIfNonInitializingPathExists && state.getWriteCount() == 0) continue variables;
        }
      }

      result.add(psiVariable);
    }

    return result.toArray(new PsiVariable[result.size()]);
  }

  public static boolean needVariableValueAt(final PsiVariable variable, final ControlFlow flow, final int offset) {
    InstructionClientVisitor<Boolean> visitor = new InstructionClientVisitor<Boolean>() {
      boolean[] neededBelow = new boolean[flow.getSize()+1];

      public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        if (instruction.variable.equals(variable)) {
           needed = true;
        }
        neededBelow[offset] |= needed;
      }

      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        if (instruction.variable.equals(variable)) {
          needed = false;
        }
        neededBelow[offset] = needed;
      }

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean needed = neededBelow[nextOffset];
        neededBelow[offset] |= needed;
      }

      public Boolean getResult() {
        return neededBelow[offset];
      }
    };
    depthFirstSearch(flow, visitor, offset, flow.getSize());
    return visitor.getResult().booleanValue();
  }

  public static PsiVariable[] getReadVariables(ControlFlow flow, int start, int end) {
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        PsiVariable variable = ((ReadVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
    }
    return array.toArray(new PsiVariable[array.size()]);
  }

  public static PsiVariable[] getWrittenVariables(ControlFlow flow, int start, int end, final boolean ignoreNotReachingWrites) {
    Set<PsiVariable> set = new HashSet<PsiVariable>();
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction && (!ignoreNotReachingWrites || isInstructionReachable(flow, end, i))) {
        set.add(((WriteVariableInstruction)instruction).variable);
      }
    }
    return set.toArray(new PsiVariable[set.size()]);
  }

  public static PsiVariable[] getUsedVariables(ControlFlow flow, int start, int end) {
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    List<Instruction> instructions = flow.getInstructions();
    for (int i = start; i < end; i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof ReadVariableInstruction) {
        PsiVariable variable = ((ReadVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
      else if (instruction instanceof WriteVariableInstruction) {
        PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
    }
    return array.toArray(new PsiVariable[array.size()]);
  }

  public static PsiVariable[] getInputVariables(ControlFlow flow, int start, int end) {
    PsiVariable[] usedVariables = getUsedVariables(flow, start, end);
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    for (PsiVariable variable : usedVariables) {
      if (needVariableValueAt(variable, flow, start)) {
        array.add(variable);
      }
    }
    PsiVariable[] inputVariables = array.toArray(new PsiVariable[array.size()]);
    if (LOG.isDebugEnabled()) {
      LOG.debug("input variables:");
      for (PsiVariable variable : inputVariables) {
        LOG.debug("  " + variable.toString());
      }
    }
    return inputVariables;
  }

  public static PsiVariable[] getOutputVariables(ControlFlow flow, int start, int end, int[] exitPoints) {
    PsiVariable[] writtenVariables = getWrittenVariables(flow, start, end, true);
    ArrayList<PsiVariable> array = new ArrayList<PsiVariable>();
    for (PsiVariable variable : writtenVariables) {
      for (int exitPoint : exitPoints) {
        if (needVariableValueAt(variable, flow, exitPoint)) {
          array.add(variable);
        }
      }
    }
    PsiVariable[] outputVariables = array.toArray(new PsiVariable[array.size()]);
    if (LOG.isDebugEnabled()) {
      LOG.debug("output variables:");
      for (PsiVariable variable : outputVariables) {
        LOG.debug("  " + variable.toString());
      }
    }
    return outputVariables;
  }

  public static void findExitPointsAndStatements(final ControlFlow flow, final int start, final int end,
                                                 final IntArrayList exitPoints, final List<PsiStatement> exitStatements, final Class[] classesFilter) {
    if (end == start) {
      exitPoints.add(end);
      return;
    }
    InstructionClientVisitor visitor = new InstructionClientVisitor() {
      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        //[ven]This is a hack since Extract Method doesn't want to see throw's exit points
        processGotoStatement(flow, offset, classesFilter, exitStatements);
      }

      public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
        processGoto(flow, start, end, exitPoints, exitStatements, offset, instruction.offset, null, classesFilter);
      }

      // call/return do not incur exit points
      public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
      }
      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
      }

      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        visitInstruction(instruction, offset, nextOffset);
      }

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (offset >= end - 1) {
          int exitOffset = end;
          exitOffset = promoteThroughGotoChain(flow, exitOffset);
          if (!exitPoints.contains(exitOffset)) {
            exitPoints.add(exitOffset);
          }
        }
      }

      public Object getResult() {
        return null;
      }
    };
    depthFirstSearch(flow, visitor, start, end);
  }

  private static void processGoto(ControlFlow flow, int start, int end,
                                  IntArrayList exitPoints,
                                  List<PsiStatement> exitStatements,
                                  int offset,
                                  int gotoOffset,
                                  IntArrayList stack,
                                  Class[] classesFilter) {
    if (start <= gotoOffset && gotoOffset < end) {
      if (stack != null) {
        stack.add(gotoOffset);
      }
    }
    else {
      // process chain of goto's
      gotoOffset = promoteThroughGotoChain(flow, gotoOffset);

      if (!exitPoints.contains(gotoOffset) && (gotoOffset >= end || gotoOffset < start)) {
        exitPoints.add(gotoOffset);
      }
      processGotoStatement(flow, offset, classesFilter, exitStatements);
    }
  }

  private static void processGotoStatement(ControlFlow flow, int offset, Class[] classesFilter, List<PsiStatement> exitStatements) {
    PsiStatement statement = findStatement(flow, offset);
    if (statement != null) {
      if (classesFilter == null) {
        exitStatements.add(statement);
      } else {
        for (Class aClassesFilter : classesFilter) {
          if (aClassesFilter.isAssignableFrom(statement.getClass())) {
            exitStatements.add(statement);
            break;
          }
        }
      }
    }
  }

  private static int promoteThroughGotoChain(ControlFlow flow, int offset) {
    List<Instruction> instructions = flow.getInstructions();
    while (true) {
      if (offset >= instructions.size()) break;
      Instruction instruction = instructions.get(offset);
      if (!(instruction instanceof GoToInstruction) || ((GoToInstruction)instruction).isReturn) break;
      offset = ((GoToInstruction)instruction).offset;
    }
    return offset;
  }

  public static final Class[] DEFAULT_EXIT_STATEMENTS_CLASSES = new Class[] {PsiReturnStatement.class, PsiBreakStatement.class, PsiContinueStatement.class};

  private static PsiStatement findStatement(ControlFlow flow, int offset) {
    PsiElement element = flow.getElement(offset);
    while (!(element instanceof PsiStatement) && element != null) {
      element = element.getParent();
    }
    return (PsiStatement)element;
  }

  public static PsiElement findCodeFragment(PsiElement element) {
    PsiElement codeFragment = element;
    PsiElement parent = codeFragment.getParent();
    while (parent != null) {
      if (parent instanceof PsiDirectory
          || parent instanceof PsiMethod
          || parent instanceof PsiField || parent instanceof PsiClassInitializer) {
        break;
      }
      codeFragment = parent;
      parent = parent.getParent();
    }
    return codeFragment;
  }

  private static boolean checkReferenceExpressionScope (final PsiReferenceExpression ref, final PsiElement targetClassMember) {
    final JavaResolveResult resolveResult = ref.advancedResolve(false);
    final PsiElement def = resolveResult.getElement();
    if (def != null) {
      PsiElement parent = def.getParent ();
      PsiElement commonParent = PsiTreeUtil.findCommonParent(parent, targetClassMember);
      if (commonParent == null) {
        parent = resolveResult.getCurrentFileResolveScope();
      }
      if (parent instanceof PsiClass) {
        final PsiClass clss = (PsiClass) parent;
        if (PsiTreeUtil.isAncestor(targetClassMember, clss, false))
          return false;
        }
    }

    return true;
  }

  /**
   * Checks possibility of extracting code fragment outside containing anonymous (local) class.
   * Also collects variables to be passed as additional parameters.
   * @return true if code fragement can be extracted outside
   * @param array Vector to collect variables to be passed as additional parameters
   * @param scope scope to be scanned (part of code fragement to be extracted)
   * @param member member containing the code to be extracted
   * @param targetClassMember member in target class containing code fragement
   */
  public static boolean collectOuterLocals(List<PsiVariable> array, PsiElement scope, PsiElement member,
                                           PsiElement targetClassMember) {
    if (scope instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)scope;
      if (!checkReferenceExpressionScope (call.getMethodExpression(), targetClassMember)) {
        return false;
      }
    }
    else if (scope instanceof PsiReferenceExpression) {
      if (!checkReferenceExpressionScope ((PsiReferenceExpression)scope, targetClassMember)) {
        return false;
      }
    }

    if (scope instanceof PsiJavaCodeReferenceElement) {

      final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)scope;
      final JavaResolveResult result = ref.advancedResolve(false);
      final PsiElement refElement = result.getElement();

      if (refElement != null) {

        PsiElement parent = PsiTreeUtil.findCommonParent(refElement.getParent(), member);
        if (parent == null) {
          parent = result.getCurrentFileResolveScope();
        }

        if (parent != null && !member.equals(parent)) { // not local in member
          parent = PsiTreeUtil.findCommonParent(parent, targetClassMember);
          if (targetClassMember.equals(parent)) { //something in anonymous class
            if (refElement instanceof PsiVariable) {
              if (scope instanceof PsiReferenceExpression &&
                  PsiUtil.isAccessedForWriting((PsiReferenceExpression)scope)) {
                return false;
              }
              PsiVariable variable = (PsiVariable)refElement;
              if (!array.contains(variable)) {
                array.add(variable);
              }
            }
            else {
              return false;
            }
          }
        }
      }
    }
    else if (scope instanceof PsiThisExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression)scope).getQualifier();
      if (qualifier == null) {
        return false;
      }
    }
    else if (scope instanceof PsiSuperExpression) {
      if (((PsiSuperExpression)scope).getQualifier() == null) {
        return false;
      }
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!collectOuterLocals(array, child, member, targetClassMember)) return false;
    }
    return true;
  }


  /**
   * @return true if each control flow path results in return statement or exception thrown
   */
  public static boolean returnPresent(final ControlFlow flow) {
    InstructionClientVisitor<Boolean> visitor = new ReturnPresentClientVisitor(flow);

    depthFirstSearch(flow, visitor);
    return visitor.getResult().booleanValue();
  }
  private static class ReturnPresentClientVisitor extends InstructionClientVisitor<Boolean> {
    // false if control flow at this offset terminates either by return called or exception thrown
    private final boolean[] isNormalCompletion;
    private final ControlFlow myFlow;

    public ReturnPresentClientVisitor(ControlFlow flow) {
      myFlow = flow;
      isNormalCompletion = new boolean[myFlow.getSize() + 1];
      isNormalCompletion[myFlow.getSize()] = true;
    }

    public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !instruction.isReturn && isNormalCompletion[nextOffset];
    }

    public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      boolean isNormal = instruction.offset == nextOffset && nextOffset != offset + 1 ?
                  !isLeaf(nextOffset) && isNormalCompletion[nextOffset] :
                  isLeaf(nextOffset) || isNormalCompletion[nextOffset];

      isNormalCompletion[offset] |= isNormal;
    }

    public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
    }

    public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      isNormalCompletion[offset] |= !instruction.isReturn && isNormalCompletion[nextOffset];
    }

    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();

      boolean isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
      isNormalCompletion[offset] |= isNormal;
    }

    public Boolean getResult() {
      return !isNormalCompletion[0];
    }
  }

  public static boolean returnPresentBetween(final ControlFlow flow, final int startOffset, final int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // false if control flow at this offset terminates either by return called or exception thrown
      boolean[] isNormalCompletion = new boolean[flow.getSize() + 1];

      public MyVisitor() {
        int i;
        final int length = flow.getSize();
        for (i = 0; i < startOffset; i++) {
          isNormalCompletion[i] = true;
        }
        for (i = endOffset; i <= length; i++) {
          isNormalCompletion[i] = true;
        }
      }

      public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        boolean isNormal = !instruction.isReturn && isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal;
        if (throwToOffset == nextOffset) {
          if (throwToOffset <= endOffset) {
            isNormal = !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
          }
          else {
            return;
          }
        }
        else {
          isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
        }
        isNormalCompletion[offset] |= isNormal;
      }

      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset <= endOffset) {
          boolean isNormal = !isLeaf(nextOffset) && isNormalCompletion[nextOffset];
          isNormalCompletion[offset] |= isNormal;
        }
      }

      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset > endOffset && nextOffset != offset + 1) {
          return;
        }
        boolean isNormal = isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        boolean isNormal = !instruction.isReturn && isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        final boolean isNormal = isLeaf(nextOffset) || isNormalCompletion[nextOffset];
        isNormalCompletion[offset] |= isNormal;
      }

      public Boolean getResult() {
        return !isNormalCompletion[startOffset];
      }
    }
    final MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult().booleanValue();
  }

  public static Object[] getAllWorldProblemsAtOnce(final ControlFlow flow) {
    InstructionClientVisitor[] visitors = new InstructionClientVisitor[]{
      new ReturnPresentClientVisitor(flow),
      new UnreachableStatementClientVisitor(flow),
      new ReadBeforeWriteClientVisitor(flow),
      new InitializedTwiceClientVisitor(flow),
    };
    CompositeInstructionClientVisitor visitor = new CompositeInstructionClientVisitor(visitors);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }

  /**
   * returns true iff exists controlflow path completing normally, i.e. not resulting in return,break,continue or exception thrown.
   * In other words, if we add instruction after controlflow specified, it should be reachable
   */
  public static boolean canCompleteNormally(final ControlFlow flow, final int startOffset, final int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // false if control flow at this offset terminates abruptly
      boolean[] canCompleteNormally = new boolean[flow.getSize() + 1];

      public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, instruction.isReturn);
      }
      public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, instruction.isReturn);
      }

      private void checkInstruction(int offset, int nextOffset, boolean isReturn) {
        if (offset > endOffset) return;
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean isNormal = nextOffset <= endOffset && !isReturn && (nextOffset == endOffset || canCompleteNormally[nextOffset]);
        if (isNormal && nextOffset == endOffset) {
          PsiElement element = flow.getElement(offset);
          if (element instanceof PsiBreakStatement || element instanceof PsiContinueStatement) {
            isNormal = false;
          }
        }
        canCompleteNormally[offset] |= isNormal;
      }

      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        int throwToOffset = instruction.offset;
        boolean isNormal;
        if (throwToOffset == nextOffset) {
          isNormal = throwToOffset <= endOffset && !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
        }
        else {
          isNormal = canCompleteNormally[nextOffset];
        }
        canCompleteNormally[offset] |= isNormal;
      }

      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset <= endOffset) {
          boolean isNormal = !isLeaf(nextOffset) && canCompleteNormally[nextOffset];
          canCompleteNormally[offset] |= isNormal;
        }
      }

      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        if (offset > endOffset) return;
        if (nextOffset > endOffset && nextOffset != offset + 1) {
          return;
        }
        boolean isNormal = canCompleteNormally[nextOffset];
        canCompleteNormally[offset] |= isNormal;
      }

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        checkInstruction(offset, nextOffset, false);
      }

      public Boolean getResult() {
        return canCompleteNormally[startOffset];
      }
    }
    final MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, endOffset);
    return visitor.getResult().booleanValue();
  }

  /**
   * @return any unreachable statement or null
   */
  public static PsiElement getUnreachableStatement(final ControlFlow flow) {
    final InstructionClientVisitor<PsiElement> visitor = new UnreachableStatementClientVisitor(flow);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }
  private static class UnreachableStatementClientVisitor extends InstructionClientVisitor<PsiElement> {
    private final ControlFlow myFlow;

    public UnreachableStatementClientVisitor(ControlFlow flow) {
      myFlow = flow;
    }

    public PsiElement getResult() {
      for (int i = 0; i < processedInstructions.length; i++) {
        if (!processedInstructions[i]) {
          PsiElement element = myFlow.getElement(i);
          if (element == null || !PsiUtil.isStatement(element)) continue;
          if (element.getParent() instanceof PsiExpression) continue;

          // ignore for(;;) statement unreachable update
          while (element instanceof PsiExpression) {
            element = element.getParent();
          }
          if (element instanceof PsiStatement
              && element.getParent() instanceof PsiForStatement
              && element == ((PsiForStatement) element.getParent()).getUpdate()) {
            continue;
          }
          //filter out generated stmts
          final int endOffset = myFlow.getEndOffset(element);
          if (endOffset != i + 1) continue;
          final int startOffset = myFlow.getStartOffset(element);
          // this offset actually is a part of reachable statement
          if (0 <= startOffset && startOffset < processedInstructions.length && processedInstructions[startOffset]) continue;
          return element;
        }
      }
      return null;
    }
  }

  private static PsiReferenceExpression getEnclosingReferenceExpression(PsiElement element, PsiVariable variable) {
    final PsiReferenceExpression reference = findReferenceTo(element, variable);
    if (reference != null) return reference;
    while (element != null) {
      if (element instanceof PsiReferenceExpression) {
        return (PsiReferenceExpression)element;
      }
      else if (element instanceof PsiMethod || element instanceof PsiClass) {
        return null;
      }
      element = element.getParent();
    }
    return null;
  }

  private static PsiReferenceExpression findReferenceTo(PsiElement element, PsiVariable variable) {
    if (element instanceof PsiReferenceExpression
        && ((PsiReferenceExpression)element).resolve() == variable) {
      return (PsiReferenceExpression)element;
    }
    final PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      final PsiReferenceExpression reference = findReferenceTo(child, variable);
      if (reference != null) return reference;
    }
    return null;
  }


  public static boolean isVariableDefinitelyAssigned(final PsiVariable variable, final ControlFlow flow) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // true if from this point below there may be branch with no variable assignment
      boolean[] maybeUnassigned = new boolean[flow.getSize() + 1];
      {
        maybeUnassigned[maybeUnassigned.length-1] = true;
      }

      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (instruction.variable == variable) {
          maybeUnassigned[offset] = false;
        }
        else {
          visitInstruction(instruction, offset, nextOffset);
        }
      }

      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean unassigned = offset == flow.getSize() - 1
                             || !isLeaf(nextOffset) && maybeUnassigned[nextOffset];

        maybeUnassigned[offset] |= unassigned;
      }

      public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
        visitInstruction(instruction, offset, nextOffset);
        // clear return statements after procedure as well
        for (int i = instruction.procBegin; i<instruction.procEnd+3;i++) {
          maybeUnassigned[i] = false;
        }
      }

      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean unassigned = !isLeaf(nextOffset) && maybeUnassigned[nextOffset];
        maybeUnassigned[offset] |= unassigned;
      }

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean unassigned = isLeaf(nextOffset) || maybeUnassigned[nextOffset];
        maybeUnassigned[offset] |= unassigned;
      }

      public Boolean getResult() {
        return !maybeUnassigned[0];
      }
    }
    if (flow.getSize() == 0) return false;
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult().booleanValue();
  }

  public static boolean isVariableDefinitelyNotAssigned(final PsiVariable variable, final ControlFlow flow) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      // true if from this point below there may be branch with variable assignment
      boolean[] maybeAssigned = new boolean[flow.getSize() + 1];

      public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean assigned;
        assigned = instruction.variable == variable || maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        boolean assigned = !isLeaf(nextOffset) && maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();
        int throwToOffset = instruction.offset;
        boolean assigned = throwToOffset == nextOffset ? !isLeaf(nextOffset) && maybeAssigned[nextOffset] :
                    maybeAssigned[nextOffset];
        maybeAssigned[offset] |= assigned;
      }

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        boolean assigned = maybeAssigned[nextOffset];

        maybeAssigned[offset] |= assigned;
      }

      public Boolean getResult() {
        return !maybeAssigned[0];
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult().booleanValue();
  }

  /**
   * @return min offset after sourceOffset which is definitely reachable from all references
   */
  public static int getMinDefinitelyReachedOffset(final ControlFlow flow, final int sourceOffset,
                                                  final List references) {
    class MyVisitor extends InstructionClientVisitor<Integer> {
      // set of exit posint reached from this offset
      TIntHashSet[] exitPoints = new TIntHashSet[flow.getSize()];

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset > flow.getSize()) nextOffset = flow.getSize();

        if (exitPoints[offset] == null) {
          exitPoints[offset] = new TIntHashSet();
        }
        if (isLeaf(nextOffset)) {
          exitPoints[offset].add(offset);
        }
        else {
          exitPoints[offset].addAll(exitPoints[nextOffset].toArray());
        }
      }

      public Integer getResult() {
        int minOffset = flow.getSize();
        int maxExitPoints = 0;
        nextOffset:
        for (int i = sourceOffset; i < exitPoints.length; i++) {
          TIntHashSet exitPointSet = exitPoints[i];
          final int size = exitPointSet == null ? 0 : exitPointSet.size();
          if (size > maxExitPoints) {
            // this offset should be reachable from all other references
            for (Object reference : references) {
              PsiElement element = (PsiElement)reference;
              final PsiElement statement = PsiUtil.getEnclosingStatement(element);
              if (statement == null) continue;
              final int endOffset = flow.getEndOffset(statement);
              if (endOffset == -1) continue;
              if (i != endOffset && !isInstructionReachable(flow, i, endOffset)) continue nextOffset;
            }
            minOffset = i;
            maxExitPoints = size;
          }
        }
        return minOffset;
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor);
    return visitor.getResult().intValue();
  }

  private static void depthFirstSearch(ControlFlow flow, InstructionClientVisitor visitor) {
    depthFirstSearch(flow, visitor, 0, flow.getSize());
  }

  private static void depthFirstSearch(ControlFlow flow, InstructionClientVisitor visitor, int startOffset, int endOffset) {
    visitor.processedInstructions = new boolean[endOffset];
    internalDepthFirstSearch(flow.getInstructions(), visitor, startOffset, endOffset);
  }

  private static void internalDepthFirstSearch(final List<Instruction> instructions, final InstructionClientVisitor clientVisitor, int offset, int endOffset) {
    final IntArrayList oldOffsets = new IntArrayList(instructions.size() / 2);
    final IntArrayList newOffsets = new IntArrayList(instructions.size() / 2);

    oldOffsets.add(offset);
    newOffsets.add(-1);

    // we can change instruction internal state here (e.g. CallInstruction.stack)
    synchronized (instructions) {
      final IntArrayList currentProcedureReturnOffsets = new IntArrayList();
      ControlFlowInstructionVisitor getNextOffsetVisitor = new ControlFlowInstructionVisitor() {
        public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
          instruction.execute(offset + 1);
          int newOffset = instruction.offset;
          // 'procedure' pointed by call instruction should be processed regardless of whether it was already visited or not
          // clear procedure text and return instructions aftewards
          for (int i = instruction.procBegin; i < instruction.procEnd || instructions.get(i) instanceof ReturnInstruction; i++) {
            clientVisitor.processedInstructions[i] = false;
          }
          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);

          currentProcedureReturnOffsets.add(offset + 1);
        }

        public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.execute(false);
          if (newOffset != -1) {
            oldOffsets.add(offset);
            newOffsets.add(newOffset);

            oldOffsets.add(newOffset);
            newOffsets.add(-1);
          }
        }

        public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.offset;
          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);
        }

        public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
          int newOffset = instruction.offset;

          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(offset);
          newOffsets.add(offset + 1);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);

          oldOffsets.add(offset + 1);
          newOffsets.add(-1);
        }

        public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
          int newOffset = offset + 1;
          oldOffsets.add(offset);
          newOffsets.add(newOffset);

          oldOffsets.add(newOffset);
          newOffsets.add(-1);
        }
      };
      while (oldOffsets.size() != 0) {
        offset = oldOffsets.remove(oldOffsets.size() - 1);
        int newOffset = newOffsets.remove(newOffsets.size() - 1);

        if (offset >= endOffset) {
          continue;
        }
        Instruction instruction = instructions.get(offset);

        if (clientVisitor.processedInstructions[offset]) {
          if (newOffset != -1) {
            instruction.accept(clientVisitor, offset, newOffset);
          }
          // when traversing call instruction, we have traversed all procedure control flows, so pop return address
          if (currentProcedureReturnOffsets.size() != 0 && currentProcedureReturnOffsets.get(currentProcedureReturnOffsets.size() - 1) - 1 == offset) {
            currentProcedureReturnOffsets.remove(currentProcedureReturnOffsets.size() - 1);
          }
          continue;
        }
        if (currentProcedureReturnOffsets.size() != 0) {
          int returnOffset = currentProcedureReturnOffsets.get(currentProcedureReturnOffsets.size() - 1);
          CallInstruction callInstruction = (CallInstruction) instructions.get(returnOffset - 1);
          // check if we inside procedure but 'return offset' stack is empty, so
          // we should push back to 'return offset' stack
          synchronized (callInstruction.stack) {
            if (callInstruction.procBegin <= offset && offset < callInstruction.procEnd + 2
                && (callInstruction.stack.size() == 0 || callInstruction.stack.peekReturnOffset() != returnOffset)) {
              callInstruction.stack.push(returnOffset, callInstruction);
            }
          }
        }

        clientVisitor.processedInstructions[offset] = true;
        instruction.accept(getNextOffsetVisitor, offset, newOffset);
      }
    }
  }

  private static boolean isInsideReturnStatement(PsiElement element) {
    while (element instanceof PsiExpression) element = element.getParent();
    return element instanceof PsiReturnStatement;
  }

  private static class CopyOnWriteList {
    List<VariableInfo> original;
    List<VariableInfo> list;

    public CopyOnWriteList add(VariableInfo value) {
      CopyOnWriteList newList = new CopyOnWriteList();
      List<VariableInfo> list = getList();
      for (final VariableInfo variableInfo : list) {
        if (!value.equals(variableInfo)) {
          newList.list.add(variableInfo);
        }
      }
      newList.list.add(value);
      return newList;
    }
    public CopyOnWriteList remove(VariableInfo value) {
      CopyOnWriteList newList = new CopyOnWriteList();
      List<VariableInfo> list = getList();
      for (final VariableInfo variableInfo : list) {
        if (!value.equals(variableInfo)) {
          newList.list.add(variableInfo);
        }
      }
      return newList;
    }

    public List<VariableInfo> getList() {
      return original == null ? list : original;
    }

    public CopyOnWriteList(List<VariableInfo> original) {
      this.original = original;
    }
    public CopyOnWriteList() {
      list = new LinkedList<VariableInfo>();
    }
    public CopyOnWriteList addAll(CopyOnWriteList addList) {
      CopyOnWriteList newList = new CopyOnWriteList();
      List<VariableInfo> list = getList();
      for (final VariableInfo variableInfo : list) {
        newList.list.add(variableInfo);
      }
      List<VariableInfo> toAdd = addList.getList();
      for (final VariableInfo variableInfo : toAdd) {
        if (!newList.list.contains(variableInfo)) {
          // no copy
          newList.list.add(variableInfo);
        }
      }
      return newList;
    }
  }
  public static class VariableInfo {
    private final PsiVariable variable;
    public final PsiElement expression;

    public VariableInfo(PsiVariable variable, PsiElement expression) {
      this.variable = variable;
      this.expression = expression;
    }

    public VariableInfo(VariableInfo variableInfo) {
      this(variableInfo.variable, variableInfo.expression);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      return o instanceof VariableInfo && variable.equals(((VariableInfo)o).variable);
    }

    public int hashCode() {
      return variable.hashCode();
    }
  }
  private static void merge(int offset, CopyOnWriteList readVars, CopyOnWriteList[] readVariables) {
    if (readVars != null) {
      CopyOnWriteList existing = readVariables[offset];
      readVariables[offset] = existing == null ? readVars : existing.addAll(readVars);
    }
  }
  /**
   * @return list of PsiReferenceExpression of usages of non-initialized variables
   */
  public static List<PsiReferenceExpression> getReadBeforeWrite(final ControlFlow flow) {
    InstructionClientVisitor<List<PsiReferenceExpression>> visitor = new ReadBeforeWriteClientVisitor(flow);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }
  private static class ReadBeforeWriteClientVisitor extends InstructionClientVisitor<List<PsiReferenceExpression>> {
    // map of variable->PsiReferenceExpressions for all read before written variables for this point and below in control flow
    private final CopyOnWriteList[] readVariables;
    private final ControlFlow myFlow;

    public ReadBeforeWriteClientVisitor(ControlFlow flow) {
      myFlow = flow;
      readVariables = new CopyOnWriteList[myFlow.getSize()+1];
    }

    public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      CopyOnWriteList readVars = readVariables[nextOffset];
      PsiElement element = myFlow.getElement(offset);
      final PsiVariable variable = instruction.variable;
      if (!(variable instanceof PsiParameter) || ((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement) {
        PsiReferenceExpression expression = getEnclosingReferenceExpression(element, variable);
        if (expression != null) {
          VariableInfo variableInfo = new VariableInfo(variable, expression);
          if (readVars == null) {
            readVars = new CopyOnWriteList();
            readVars.list.add(variableInfo);
          }
          else {
            readVars = readVars.add(variableInfo);
          }
        }
      }
      merge(offset, readVars, readVariables);
    }

    public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      CopyOnWriteList readVars = readVariables[nextOffset];
      final PsiVariable variable = instruction.variable;
      if (readVars != null && (!(variable instanceof PsiParameter) || ((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement)) {
        readVars = readVars.remove(new VariableInfo(variable, null));
      }
      merge(offset, readVars, readVariables);
    }

    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();
      CopyOnWriteList readVars = readVariables[nextOffset];
      merge(offset, readVars, readVariables);
    }

    public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
      visitInstruction(instruction, offset, nextOffset);
      for (int i = instruction.procBegin; i <= instruction.procEnd; i++) {
        readVariables[i] = null;
      }
    }

    public List<PsiReferenceExpression> getResult() {
      List<PsiReferenceExpression> problemsFound = new ArrayList<PsiReferenceExpression>();
      CopyOnWriteList topReadVariables = readVariables[0];
      if (topReadVariables != null) {
        List<VariableInfo> list = topReadVariables.getList();
        for (final VariableInfo variableInfo : list) {
          problemsFound.add((PsiReferenceExpression)variableInfo.expression);
        }
      }
      return problemsFound;
    }
  }

  public static final int NORMAL_COMPLETION_REASON = 1;
  public static final int RETURN_COMPLETION_REASON = 2;
  /**
   * return reasons.normalCompletion when  block can complete normally
   *        reasons.returnCalled when  block can complete abruptly because of return statement executed
   */
  public static int getCompletionReasons(final ControlFlow flow, final int offset, final int endOffset) {
    class MyVisitor extends InstructionClientVisitor<Integer> {
      boolean[] normalCompletion = new boolean[endOffset];
      boolean[] returnCalled = new boolean[endOffset];

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        boolean ret = nextOffset < endOffset && returnCalled[nextOffset];
        boolean normal = nextOffset < endOffset && normalCompletion[nextOffset];
        final PsiElement element = flow.getElement(offset);
        boolean goToReturn = instruction instanceof GoToInstruction && ((GoToInstruction)instruction).isReturn;
        boolean condGoToReturn = instruction instanceof ConditionalGoToInstruction && ((ConditionalGoToInstruction)instruction).isReturn;
        if (goToReturn || condGoToReturn || isInsideReturnStatement(element)) {
          ret = true;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          final int throwOffset = ((ConditionalThrowToInstruction)instruction).offset;
          boolean normalWhenThrow = throwOffset < endOffset && normalCompletion[throwOffset];
          boolean normalWhenNotThrow = offset == endOffset - 1 || normalCompletion[offset + 1];
          normal = normalWhenThrow || normalWhenNotThrow;
        }
        else if (!(instruction instanceof ThrowToInstruction) && nextOffset >= endOffset) {
          normal = true;
        }
        returnCalled[offset] |= ret;
        normalCompletion[offset] |= normal;
      }

      public Integer getResult() {
        return (returnCalled[offset] ? RETURN_COMPLETION_REASON : 0) | (normalCompletion[offset] ? NORMAL_COMPLETION_REASON : 0);
      }
    }
    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, offset, endOffset);

    return visitor.getResult().intValue();
  }

  public static Collection<VariableInfo> getInitializedTwice(final ControlFlow flow) {
    InitializedTwiceClientVisitor visitor = new InitializedTwiceClientVisitor(flow);
    depthFirstSearch(flow, visitor);
    return visitor.getResult();
  }
  private static class InitializedTwiceClientVisitor extends InstructionClientVisitor<Collection<VariableInfo>> {
    // map of variable->PsiReferenceExpressions for all read and not written variables for this point and below in control flow
    private final CopyOnWriteList[] writtenVariables;
    private final CopyOnWriteList[] writtenTwiceVariables;
    private final ControlFlow myFlow;

    public InitializedTwiceClientVisitor(ControlFlow flow) {
      myFlow = flow;
      writtenVariables = new CopyOnWriteList[myFlow.getSize() + 1];
      writtenTwiceVariables = new CopyOnWriteList[myFlow.getSize() + 1];
    }

    public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
      if (nextOffset > myFlow.getSize()) nextOffset = myFlow.getSize();

      CopyOnWriteList writeVars = writtenVariables[nextOffset];
      CopyOnWriteList writeTwiceVars = writtenTwiceVariables[nextOffset];
      if (instruction instanceof WriteVariableInstruction) {
        final WriteVariableInstruction writeVariableInstruction = (WriteVariableInstruction)instruction;
        final PsiVariable variable = writeVariableInstruction.variable;
        final PsiElement element = myFlow.getElement(offset);

        PsiElement latestWriteVarExpression = null;
        if (writeVars != null) {
          List<VariableInfo> list = writeVars.getList();
          for (final VariableInfo variableInfo : list) {
            if (variableInfo.variable == variable) {
              latestWriteVarExpression = variableInfo.expression;
              break;
            }
          }
        }
        if (latestWriteVarExpression == null) {
          PsiElement expression = null;
          if (element instanceof PsiAssignmentExpression
              && ((PsiAssignmentExpression)element).getLExpression() instanceof PsiReferenceExpression) {
            expression = ((PsiAssignmentExpression)element).getLExpression();
          }
          else if (element instanceof PsiPostfixExpression) {
            expression = ((PsiPostfixExpression)element).getOperand();
          }
          else if (element instanceof PsiPrefixExpression) {
            expression = ((PsiPrefixExpression)element).getOperand();
          }
          else if (element instanceof PsiDeclarationStatement) {
            //should not happen
            expression = element;
          }
          if (writeVars == null) {
            writeVars = new CopyOnWriteList();
          }
          writeVars = writeVars.add(new VariableInfo(variable, expression));
        }
        else {
          if (writeTwiceVars == null) {
            writeTwiceVars = new CopyOnWriteList();
          }
          writeTwiceVars = writeTwiceVars.add(new VariableInfo(variable, latestWriteVarExpression));
        }
      }
      merge(offset, writeVars, writtenVariables);
      merge(offset, writeTwiceVars, writtenTwiceVariables);
    }

    public Collection<VariableInfo> getResult() {
      CopyOnWriteList writtenTwiceVariable = writtenTwiceVariables[0];
      if (writtenTwiceVariable == null) return Collections.emptyList();
      return writtenTwiceVariable.getList();
    }
  }

  /**
   * @return true if instruction at 'instructionOffset' is reachable from offset 'startOffset'
   */
  public static boolean isInstructionReachable(final ControlFlow flow, final int instructionOffset, final int startOffset) {
    class MyVisitor extends InstructionClientVisitor<Boolean> {
      boolean reachable;

      public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
        if (nextOffset == instructionOffset) reachable = true;
      }

      public Boolean getResult() {
        return reachable;
      }
    }

    MyVisitor visitor = new MyVisitor();
    depthFirstSearch(flow, visitor, startOffset, flow.getSize());

    return visitor.getResult().booleanValue();
  }

  public static boolean isVariableAssignedInLoop(PsiReferenceExpression expression, PsiElement resolved) {
    if (!(expression.getParent() instanceof PsiAssignmentExpression)
        || ((PsiAssignmentExpression)expression.getParent()).getLExpression() != expression) {
      return false;
    }
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) return false;

    if (!(resolved instanceof PsiVariable)) return false;
    PsiVariable variable = (PsiVariable)resolved;

    final PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, expression);
    if (codeBlock == null) return false;
    final ControlFlow flow;
    try {
      flow = ControlFlowFactory.getControlFlow(codeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression.getParent();
    int startOffset = flow.getStartOffset(assignmentExpression);
    return startOffset != -1 && isInstructionReachable(flow, startOffset, startOffset);
  }
}
