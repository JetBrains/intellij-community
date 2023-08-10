package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiFunction;

import static com.jetbrains.python.codeInsight.controlflow.PyControlFlowBuilder.isConjunctionOrDisjunction;

public final class PyMatchStatementControlFlowBuilder {
  private final ControlFlowBuilder myBuilder;
  private final PyElementVisitor myBaseVisitor;

  public PyMatchStatementControlFlowBuilder(@NotNull ControlFlowBuilder builder, @NotNull PyElementVisitor baseVisitor) {
    myBuilder = builder;
    myBaseVisitor = baseVisitor;
  }

  public void build(@NotNull PyMatchStatement matchStatement) {
    myBuilder.startNode(matchStatement);
    PyExpression subject = matchStatement.getSubject();
    if (subject != null) {
      subject.accept(myBaseVisitor);
    }
    for (PyCaseClause caseClause : matchStatement.getCaseClauses()) {
      processCaseClause(caseClause);
    }
  }

  private void processCaseClause(@NotNull PyCaseClause clause) {
    PyPattern pattern = clause.getPattern();
    if (pattern != null) {
      processPattern(pattern);
      retargetOutgoingPatternEdges(pattern, (oldScope, instr) -> {
        return instr.isMatched() ? pattern : clause;
      });
    }
    PyStatementList statementList = clause.getStatementList();
    PyExpression guard = clause.getGuardCondition();
    if (guard != null) {
      guard.accept(myBaseVisitor);
      // Retarget failure edges coming from inner OR and AND expressions
      retargetOutgoingEdges(guard, (pendingScope, instr) -> {
        if (instr instanceof ConditionalInstruction && !((ConditionalInstruction)instr).getResult()) {
          return clause;
        }
        return pendingScope;
      });
      // Top-level OR and AND expressions should have had their own outgoing failure edges
      if (!isConjunctionOrDisjunction(guard)) {
        myBuilder.addPendingEdge(clause, myBuilder.prevInstruction);
      }
      myBuilder.startConditionalNode(statementList, guard, true);
    }
    statementList.accept(myBaseVisitor);
    PyMatchStatement matchStatement = PsiTreeUtil.getParentOfType(clause, PyMatchStatement.class);
    assert matchStatement != null;
    retargetOutgoingEdges(statementList, (pendingScope, instruction) -> matchStatement);
    myBuilder.addPendingEdge(matchStatement, myBuilder.prevInstruction);
    myBuilder.prevInstruction = null;
  }

  private void processPattern(@NotNull PyPattern pattern) {
    boolean isRefutable = !pattern.isIrrefutable();
    if (isRefutable) {
      RefutablePatternInstruction instruction = new RefutablePatternInstruction(myBuilder, pattern, false);
      myBuilder.addNodeAndCheckPending(instruction);
      myBuilder.addPendingEdge(pattern, instruction);
    }

    if (pattern instanceof PyWildcardPattern) {
      myBuilder.startNode(pattern);
    }
    else if (pattern instanceof PyOrPattern) {
      List<PyPattern> alternatives = ((PyOrPattern)pattern).getAlternatives();
      PyPattern lastAlternative = ContainerUtil.getLastItem(alternatives);
      for (PyPattern alternative : alternatives) {
        processPattern(alternative);
        if (alternative != lastAlternative) {
          myBuilder.addPendingEdge(alternative, myBuilder.prevInstruction);
          myBuilder.prevInstruction = null;
        }
        retargetOutgoingEdges(alternative, (pendingScope, instruction) -> {
          if (instruction instanceof RefutablePatternInstruction && !((RefutablePatternInstruction)instruction).isMatched()) {
            // Pattern has failed, jump to the next alternative if any
            return alternative;
          }
          // Pattern succeeded, jump out of OR-pattern. It can be either a refutable pattern or a capture/wildcard node.
          else {
            return pattern;
          }
        });
      }
    }
    else {
      pattern.acceptChildren(new PyElementVisitor() {
        @Override
        public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
          myBaseVisitor.visitPyReferenceExpression(node);
        }

        @Override
        public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
          myBaseVisitor.visitPyTargetExpression(node);
        }

        @Override
        public void visitPyPattern(@NotNull PyPattern node) {
          processPattern(node);
          retargetOutgoingPatternEdges(pattern, (oldScope, instr) -> {
            // Mismatch in a non-OR pattern means mismatch of the containing pattern as well
            return instr.isMatched() ? oldScope : pattern;
          });
        }

        @Override
        public void visitPyPatternArgumentList(@NotNull PyPatternArgumentList node) {
          node.acceptChildren(this);
        }
      });
    }

    if (isRefutable) {
      myBuilder.addNode(new RefutablePatternInstruction(myBuilder, pattern, true));
    }
  }

  private void retargetOutgoingEdges(@NotNull PsiElement scopeAncestor,
                                     @NotNull BiFunction<PsiElement, Instruction, PsiElement> newScopeProvider) {
    myBuilder.processPending((oldScope, instruction) -> {
      if (oldScope != null && PsiTreeUtil.isAncestor(scopeAncestor, oldScope, false)) {
        myBuilder.addPendingEdge(newScopeProvider.apply(oldScope, instruction), instruction);
      }
      else {
        myBuilder.addPendingEdge(oldScope, instruction);
      }
    });
  }

  private void retargetOutgoingPatternEdges(@NotNull PsiElement scopeAncestor,
                                            @NotNull BiFunction<PsiElement, RefutablePatternInstruction, PsiElement> newScopeProvider) {
   retargetOutgoingEdges(scopeAncestor, (pendingScope, instruction) -> {
     if (instruction instanceof RefutablePatternInstruction) {
       return newScopeProvider.apply(pendingScope, (RefutablePatternInstruction)instruction);
     }
     return pendingScope;
   });
  }
}
