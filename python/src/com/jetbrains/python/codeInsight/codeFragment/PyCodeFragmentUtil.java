package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.codeInsight.codeFragment.CodeFragmentUtil;
import com.intellij.codeInsight.codeFragment.Position;
import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBinaryExpressionNavigator;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyCodeFragmentUtil {
  private PyCodeFragmentUtil() {
  }

  public static CodeFragment createCodeFragment(@NotNull final ScopeOwner owner,
                                                @NotNull final PsiElement startInScope,
                                                @NotNull final PsiElement endInScope) throws CannotCreateCodeFragmentException {
    final int start = startInScope.getTextOffset();
    final int end = endInScope.getTextOffset() + endInScope.getTextLength();

    // Check for statements inside code fragment
    owner.acceptChildren(new PyRecursiveElementVisitor(){
      @Override
      public void visitPyClass(final PyClass node) {
        if (CodeFragmentUtil.getPosition(node, start, end) == Position.INSIDE){
          throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.class.declaration.inside"));
        }
      }

      @Override
      public void visitPyFunction(final PyFunction node) {
        if (CodeFragmentUtil.getPosition(node, start, end) == Position.INSIDE){
          throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.function.declaration.inside"));
        }
      }

      @Override
      public void visitPyFromImportStatement(PyFromImportStatement node) {
        if (CodeFragmentUtil.getPosition(node, start, end) == Position.INSIDE){
          throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.from.import.inside"));
        }
      }
    });

    // Control flow inspection
    final HashSet<Instruction> outerInstructions = new HashSet<Instruction>();
    boolean returnInstructionInside = false;
    final Instruction[] flow = ControlFlowCache.getControlFlow(owner).getInstructions();
    for (Instruction instruction : flow) {
      final PsiElement element = instruction.getElement();
      if (element!=null && CodeFragmentUtil.elementFit(element, start, end)){
        if (element instanceof PyReturnStatement){
          returnInstructionInside = true;
        }
        if (element instanceof PyBreakStatement && !CodeFragmentUtil.elementFit(((PyBreakStatement) element).getLoopStatement(), start, end)){
          throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.no.corresponding.loop.for.break"));
        }
        if (element instanceof PyContinueStatement && !CodeFragmentUtil.elementFit(((PyContinueStatement) element).getLoopStatement(), start, end)){
          throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.no.corresponding.loop.for.continue"));
        }

        for (Instruction next : instruction.allSucc()) {
          // Ignore conditional instruction
          if (next instanceof ConditionalInstruction){
            continue;
          }
          final PsiElement nextElement = next.getElement();
          // Ignore binary operations control flow
          if (nextElement != null && PyBinaryExpressionNavigator.getBinaryExpressionByOperand(nextElement) != null){
            continue;
          }
          // We ignore except blocks
          if (nextElement instanceof PyExceptPart){
            continue;
          }
          // We allow raise statements in code
          if (nextElement == null && PsiTreeUtil.getParentOfType(element, PyRaiseStatement.class) != null){
            continue;
          }
          if (!CodeFragmentUtil.elementFit(nextElement, start, end)){
            outerInstructions.add(next);
          }
        }
      }
    }

    // If we see more than 1 outer instruction, controlflow is interrupted
    if (outerInstructions.size() > 2){
      throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.execution.flow.is.interrupted"));
    }
    if (outerInstructions.size() == 2){
      boolean errorFound = true;
      for (Instruction outerInstruction : outerInstructions) {
        // Here we check control flow when for statement content is beeing extracted
        final PsiElement element = outerInstruction.getElement();
        if (element != null && (PyForStatementNavigator.getPyForStatementByIterable(element) != null ||
                                PyForStatementNavigator.getPyForStatementByBody(element) != null)) {
          // In case when return instruction is inside
          if (!returnInstructionInside){
            errorFound = false;
            break;
          }
        }
      }
      if (errorFound){
        throw new CannotCreateCodeFragmentException(PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.when.execution.flow.is.interrupted"));
      }
    }

    // Building code fragment
    final PyCodeFragmentBuilder builder = new PyCodeFragmentBuilder(owner, start, end);
    owner.acceptChildren(builder);
    return new CodeFragment(builder.inElements, builder.outElements, returnInstructionInside);
  }
}