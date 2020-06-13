// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.model.psi.UrlReferenceHost;

public class ShVisitor extends PsiElementVisitor {

  public void visitAddExpression(@NotNull ShAddExpression o) {
    visitBinaryExpression(o);
  }

  public void visitArithmeticExpansion(@NotNull ShArithmeticExpansion o) {
    visitCompositeElement(o);
  }

  public void visitArrayAssignment(@NotNull ShArrayAssignment o) {
    visitCompositeElement(o);
  }

  public void visitArrayExpression(@NotNull ShArrayExpression o) {
    visitExpression(o);
  }

  public void visitAssignmentCommand(@NotNull ShAssignmentCommand o) {
    visitCommand(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitAssignmentCondition(@NotNull ShAssignmentCondition o) {
    visitCondition(o);
  }

  public void visitAssignmentExpression(@NotNull ShAssignmentExpression o) {
    visitBinaryExpression(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitAssignmentList(@NotNull ShAssignmentList o) {
    visitCompositeElement(o);
  }

  public void visitBinaryExpression(@NotNull ShBinaryExpression o) {
    visitExpression(o);
  }

  public void visitBitwiseAndExpression(@NotNull ShBitwiseAndExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBitwiseExclusiveOrExpression(@NotNull ShBitwiseExclusiveOrExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBitwiseOrExpression(@NotNull ShBitwiseOrExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBitwiseShiftExpression(@NotNull ShBitwiseShiftExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBlock(@NotNull ShBlock o) {
    visitLazyBlock(o);
  }

  public void visitBraceExpansion(@NotNull ShBraceExpansion o) {
    visitCompositeElement(o);
  }

  public void visitCaseClause(@NotNull ShCaseClause o) {
    visitCompositeElement(o);
  }

  public void visitCaseCommand(@NotNull ShCaseCommand o) {
    visitCommand(o);
  }

  public void visitCommaExpression(@NotNull ShCommaExpression o) {
    visitBinaryExpression(o);
  }

  public void visitCommand(@NotNull ShCommand o) {
    visitCompositeElement(o);
  }

  public void visitCommandSubstitutionCommand(@NotNull ShCommandSubstitutionCommand o) {
    visitCommand(o);
  }

  public void visitCommandsList(@NotNull ShCommandsList o) {
    visitCompositeElement(o);
  }

  public void visitComparisonCondition(@NotNull ShComparisonCondition o) {
    visitCondition(o);
  }

  public void visitComparisonExpression(@NotNull ShComparisonExpression o) {
    visitBinaryExpression(o);
  }

  public void visitCompoundList(@NotNull ShCompoundList o) {
    visitCompositeElement(o);
  }

  public void visitCondition(@NotNull ShCondition o) {
    visitCompositeElement(o);
  }

  public void visitConditionalCommand(@NotNull ShConditionalCommand o) {
    visitCommand(o);
  }

  public void visitConditionalExpression(@NotNull ShConditionalExpression o) {
    visitBinaryExpression(o);
  }

  public void visitDoBlock(@NotNull ShDoBlock o) {
    visitLazyDoBlock(o);
  }

  public void visitElifClause(@NotNull ShElifClause o) {
    visitCompositeElement(o);
  }

  public void visitElseClause(@NotNull ShElseClause o) {
    visitCompositeElement(o);
  }

  public void visitEqualityCondition(@NotNull ShEqualityCondition o) {
    visitCondition(o);
  }

  public void visitEqualityExpression(@NotNull ShEqualityExpression o) {
    visitBinaryExpression(o);
  }

  public void visitEvalCommand(@NotNull ShEvalCommand o) {
    visitCommand(o);
  }

  public void visitExpExpression(@NotNull ShExpExpression o) {
    visitBinaryExpression(o);
  }

  public void visitExpression(@NotNull ShExpression o) {
    visitCompositeElement(o);
  }

  public void visitForClause(@NotNull ShForClause o) {
    visitCompositeElement(o);
  }

  public void visitForCommand(@NotNull ShForCommand o) {
    visitCommand(o);
  }

  public void visitFunctionDefinition(@NotNull ShFunctionDefinition o) {
    visitCommand(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitGenericCommandDirective(@NotNull ShGenericCommandDirective o) {
    visitSimpleCommand(o);
  }

  public void visitHeredoc(@NotNull ShHeredoc o) {
    visitCompositeElement(o);
  }

  public void visitIfCommand(@NotNull ShIfCommand o) {
    visitCommand(o);
  }

  public void visitIncludeCommand(@NotNull ShIncludeCommand o) {
    visitCommand(o);
  }

  public void visitIncludeDirective(@NotNull ShIncludeDirective o) {
    visitGenericCommandDirective(o);
  }

  public void visitIndexExpression(@NotNull ShIndexExpression o) {
    visitExpression(o);
  }

  public void visitLetCommand(@NotNull ShLetCommand o) {
    visitCommand(o);
  }

  public void visitListTerminator(@NotNull ShListTerminator o) {
    visitCompositeElement(o);
  }

  public void visitLiteral(@NotNull ShLiteral o) {
    visitSimpleCommandElement(o);
    // visitUrlReferenceHost(o);
    // visitPsiNameIdentifierOwner(o);
  }

  public void visitLiteralCondition(@NotNull ShLiteralCondition o) {
    visitCondition(o);
  }

  public void visitLiteralExpression(@NotNull ShLiteralExpression o) {
    visitExpression(o);
  }

  public void visitLogicalAndCondition(@NotNull ShLogicalAndCondition o) {
    visitCondition(o);
  }

  public void visitLogicalAndExpression(@NotNull ShLogicalAndExpression o) {
    visitBinaryExpression(o);
  }

  public void visitLogicalBitwiseCondition(@NotNull ShLogicalBitwiseCondition o) {
    visitCondition(o);
  }

  public void visitLogicalBitwiseNegationExpression(@NotNull ShLogicalBitwiseNegationExpression o) {
    visitExpression(o);
  }

  public void visitLogicalOrCondition(@NotNull ShLogicalOrCondition o) {
    visitCondition(o);
  }

  public void visitLogicalOrExpression(@NotNull ShLogicalOrExpression o) {
    visitBinaryExpression(o);
  }

  public void visitMulExpression(@NotNull ShMulExpression o) {
    visitBinaryExpression(o);
  }

  public void visitNumber(@NotNull ShNumber o) {
    visitLiteral(o);
  }

  public void visitOldArithmeticExpansion(@NotNull ShOldArithmeticExpansion o) {
    visitArithmeticExpansion(o);
  }

  public void visitParenthesesCondition(@NotNull ShParenthesesCondition o) {
    visitCondition(o);
  }

  public void visitParenthesesExpression(@NotNull ShParenthesesExpression o) {
    visitExpression(o);
  }

  public void visitPattern(@NotNull ShPattern o) {
    visitCompositeElement(o);
  }

  public void visitPipelineCommand(@NotNull ShPipelineCommand o) {
    visitCommand(o);
  }

  public void visitPostExpression(@NotNull ShPostExpression o) {
    visitExpression(o);
  }

  public void visitPreExpression(@NotNull ShPreExpression o) {
    visitExpression(o);
  }

  public void visitProcessSubstitution(@NotNull ShProcessSubstitution o) {
    visitCompositeElement(o);
  }

  public void visitRedirection(@NotNull ShRedirection o) {
    visitCompositeElement(o);
  }

  public void visitRegexCondition(@NotNull ShRegexCondition o) {
    visitCondition(o);
  }

  public void visitRegexPattern(@NotNull ShRegexPattern o) {
    visitCompositeElement(o);
  }

  public void visitSelectCommand(@NotNull ShSelectCommand o) {
    visitCommand(o);
  }

  public void visitShellCommand(@NotNull ShShellCommand o) {
    visitCommand(o);
  }

  public void visitShellParameterExpansion(@NotNull ShShellParameterExpansion o) {
    visitCompositeElement(o);
  }

  public void visitSimpleCommand(@NotNull ShSimpleCommand o) {
    visitCommand(o);
  }

  public void visitSimpleCommandElement(@NotNull ShSimpleCommandElement o) {
    visitCompositeElement(o);
  }

  public void visitString(@NotNull ShString o) {
    visitLiteral(o);
  }

  public void visitSubshellCommand(@NotNull ShSubshellCommand o) {
    visitCommand(o);
  }

  public void visitTestCommand(@NotNull ShTestCommand o) {
    visitCommand(o);
  }

  public void visitThenClause(@NotNull ShThenClause o) {
    visitCompositeElement(o);
  }

  public void visitUnaryExpression(@NotNull ShUnaryExpression o) {
    visitExpression(o);
  }

  public void visitUntilCommand(@NotNull ShUntilCommand o) {
    visitCommand(o);
  }

  public void visitVariable(@NotNull ShVariable o) {
    visitLiteral(o);
  }

  public void visitWhileCommand(@NotNull ShWhileCommand o) {
    visitCommand(o);
  }

  public void visitLazyBlock(@NotNull ShLazyBlock o) {
    visitCompositeElement(o);
  }

  public void visitLazyDoBlock(@NotNull ShLazyDoBlock o) {
    visitCompositeElement(o);
  }

  public void visitCompositeElement(@NotNull ShCompositeElement o) {
    visitElement(o);
  }

}
