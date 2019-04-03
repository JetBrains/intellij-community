// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;

public class BashVisitor extends PsiElementVisitor {

  public void visitAddExpression(@NotNull BashAddExpression o) {
    visitBinaryExpression(o);
  }

  public void visitArithmeticExpansion(@NotNull BashArithmeticExpansion o) {
    visitCompositeElement(o);
  }

  public void visitArrayAssignment(@NotNull BashArrayAssignment o) {
    visitCompositeElement(o);
  }

  public void visitArrayExpression(@NotNull BashArrayExpression o) {
    visitExpression(o);
  }

  public void visitAssignmentCommand(@NotNull BashAssignmentCommand o) {
    visitCommand(o);
  }

  public void visitAssignmentExpression(@NotNull BashAssignmentExpression o) {
    visitBinaryExpression(o);
  }

  public void visitAssignmentList(@NotNull BashAssignmentList o) {
    visitCompositeElement(o);
  }

  public void visitBashExpansion(@NotNull BashBashExpansion o) {
    visitCompositeElement(o);
  }

  public void visitBinaryExpression(@NotNull BashBinaryExpression o) {
    visitExpression(o);
  }

  public void visitBitwiseAndExpression(@NotNull BashBitwiseAndExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBitwiseExclusiveOrExpression(@NotNull BashBitwiseExclusiveOrExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBitwiseOrExpression(@NotNull BashBitwiseOrExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBitwiseShiftExpression(@NotNull BashBitwiseShiftExpression o) {
    visitBinaryExpression(o);
  }

  public void visitBlock(@NotNull BashBlock o) {
    visitCommand(o);
  }

  public void visitCaseClause(@NotNull BashCaseClause o) {
    visitCompositeElement(o);
  }

  public void visitCaseCommand(@NotNull BashCaseCommand o) {
    visitCommand(o);
  }

  public void visitCommaExpression(@NotNull BashCommaExpression o) {
    visitBinaryExpression(o);
  }

  public void visitCommand(@NotNull BashCommand o) {
    visitCompositeElement(o);
  }

  public void visitCommandSubstitutionCommand(@NotNull BashCommandSubstitutionCommand o) {
    visitCommand(o);
  }

  public void visitCommandsList(@NotNull BashCommandsList o) {
    visitCompositeElement(o);
  }

  public void visitComparisonExpression(@NotNull BashComparisonExpression o) {
    visitBinaryExpression(o);
  }

  public void visitCompoundList(@NotNull BashCompoundList o) {
    visitCompositeElement(o);
  }

  public void visitConditionalCommand(@NotNull BashConditionalCommand o) {
    visitCommand(o);
  }

  public void visitConditionalExpression(@NotNull BashConditionalExpression o) {
    visitBinaryExpression(o);
  }

  public void visitDoBlock(@NotNull BashDoBlock o) {
    visitBlock(o);
  }

  public void visitElifClause(@NotNull BashElifClause o) {
    visitCompositeElement(o);
  }

  public void visitElseClause(@NotNull BashElseClause o) {
    visitCompositeElement(o);
  }

  public void visitEqualityExpression(@NotNull BashEqualityExpression o) {
    visitBinaryExpression(o);
  }

  public void visitExpExpression(@NotNull BashExpExpression o) {
    visitBinaryExpression(o);
  }

  public void visitExpression(@NotNull BashExpression o) {
    visitCompositeElement(o);
  }

  public void visitForClause(@NotNull BashForClause o) {
    visitCompositeElement(o);
  }

  public void visitForCommand(@NotNull BashForCommand o) {
    visitCommand(o);
  }

  public void visitFunctionDef(@NotNull BashFunctionDef o) {
    visitCommand(o);
  }

  public void visitGenericCommandDirective(@NotNull BashGenericCommandDirective o) {
    visitSimpleCommand(o);
  }

  public void visitHeredoc(@NotNull BashHeredoc o) {
    visitCompositeElement(o);
  }

  public void visitIfCommand(@NotNull BashIfCommand o) {
    visitCommand(o);
  }

  public void visitIncludeCommand(@NotNull BashIncludeCommand o) {
    visitCommand(o);
  }

  public void visitIncludeDirective(@NotNull BashIncludeDirective o) {
    visitGenericCommandDirective(o);
  }

  public void visitIndexExpression(@NotNull BashIndexExpression o) {
    visitExpression(o);
  }

  public void visitLetCommand(@NotNull BashLetCommand o) {
    visitCommand(o);
  }

  public void visitListTerminator(@NotNull BashListTerminator o) {
    visitCompositeElement(o);
  }

  public void visitLiteralExpression(@NotNull BashLiteralExpression o) {
    visitExpression(o);
  }

  public void visitLogicalAndExpression(@NotNull BashLogicalAndExpression o) {
    visitBinaryExpression(o);
  }

  public void visitLogicalBitwiseNegationExpression(@NotNull BashLogicalBitwiseNegationExpression o) {
    visitExpression(o);
  }

  public void visitLogicalOrExpression(@NotNull BashLogicalOrExpression o) {
    visitBinaryExpression(o);
  }

  public void visitMulExpression(@NotNull BashMulExpression o) {
    visitBinaryExpression(o);
  }

  public void visitOldArithmeticExpansion(@NotNull BashOldArithmeticExpansion o) {
    visitArithmeticExpansion(o);
  }

  public void visitParenthesesExpression(@NotNull BashParenthesesExpression o) {
    visitExpression(o);
  }

  public void visitPattern(@NotNull BashPattern o) {
    visitCompositeElement(o);
  }

  public void visitPipeline(@NotNull BashPipeline o) {
    visitCompositeElement(o);
  }

  public void visitPipelineCommand(@NotNull BashPipelineCommand o) {
    visitCommand(o);
  }

  public void visitPostExpression(@NotNull BashPostExpression o) {
    visitExpression(o);
  }

  public void visitPreExpression(@NotNull BashPreExpression o) {
    visitExpression(o);
  }

  public void visitProcessSubstitution(@NotNull BashProcessSubstitution o) {
    visitCompositeElement(o);
  }

  public void visitRedirection(@NotNull BashRedirection o) {
    visitCompositeElement(o);
  }

  public void visitRedirectionList(@NotNull BashRedirectionList o) {
    visitCompositeElement(o);
  }

  public void visitSelectCommand(@NotNull BashSelectCommand o) {
    visitCommand(o);
  }

  public void visitShellCommand(@NotNull BashShellCommand o) {
    visitCommand(o);
  }

  public void visitShellParameterExpansion(@NotNull BashShellParameterExpansion o) {
    visitCompositeElement(o);
  }

  public void visitSimpleCommand(@NotNull BashSimpleCommand o) {
    visitCommand(o);
  }

  public void visitSimpleCommandElement(@NotNull BashSimpleCommandElement o) {
    visitCompositeElement(o);
  }

  public void visitString(@NotNull BashString o) {
    visitCompositeElement(o);
  }

  public void visitSubshellCommand(@NotNull BashSubshellCommand o) {
    visitCommand(o);
  }

  public void visitThenClause(@NotNull BashThenClause o) {
    visitCompositeElement(o);
  }

  public void visitTimeOpt(@NotNull BashTimeOpt o) {
    visitCompositeElement(o);
  }

  public void visitTimespec(@NotNull BashTimespec o) {
    visitCompositeElement(o);
  }

  public void visitTrapCommand(@NotNull BashTrapCommand o) {
    visitCommand(o);
  }

  public void visitUnaryExpression(@NotNull BashUnaryExpression o) {
    visitExpression(o);
  }

  public void visitUntilCommand(@NotNull BashUntilCommand o) {
    visitCommand(o);
  }

  public void visitVariable(@NotNull BashVariable o) {
    visitCompositeElement(o);
  }

  public void visitWhileCommand(@NotNull BashWhileCommand o) {
    visitCommand(o);
  }

  public void visitCompositeElement(@NotNull BashCompositeElement o) {
    visitElement(o);
  }

}
