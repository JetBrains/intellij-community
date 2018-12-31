// This is a generated file. Not intended for manual editing.
package com.intellij.bash.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;

public class BashVisitor extends PsiElementVisitor {

  public void visitAssignmentWordRule(@NotNull BashAssignmentWordRule o) {
    visitCompositeElement(o);
  }

  public void visitCaseClause(@NotNull BashCaseClause o) {
    visitCompositeElement(o);
  }

  public void visitCaseCommand(@NotNull BashCaseCommand o) {
    visitCommand(o);
  }

  public void visitCommand(@NotNull BashCommand o) {
    visitCompositeElement(o);
  }

  public void visitCommandsList(@NotNull BashCommandsList o) {
    visitCompositeElement(o);
  }

  public void visitCompoundList(@NotNull BashCompoundList o) {
    visitCompositeElement(o);
  }

  public void visitElifClause(@NotNull BashElifClause o) {
    visitCompositeElement(o);
  }

  public void visitForCommand(@NotNull BashForCommand o) {
    visitCommand(o);
  }

  public void visitFunctionDef(@NotNull BashFunctionDef o) {
    visitCompositeElement(o);
  }

  public void visitGroupCommand(@NotNull BashGroupCommand o) {
    visitCommand(o);
  }

  public void visitIfCommand(@NotNull BashIfCommand o) {
    visitCommand(o);
  }

  public void visitList(@NotNull BashList o) {
    visitCompositeElement(o);
  }

  public void visitListTerminator(@NotNull BashListTerminator o) {
    visitCompositeElement(o);
  }

  public void visitPattern(@NotNull BashPattern o) {
    visitCompositeElement(o);
  }

  public void visitPatternList(@NotNull BashPatternList o) {
    visitCompositeElement(o);
  }

  public void visitPipeline(@NotNull BashPipeline o) {
    visitCompositeElement(o);
  }

  public void visitPipelineCommand(@NotNull BashPipelineCommand o) {
    visitCommand(o);
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

  public void visitSimpleCommand(@NotNull BashSimpleCommand o) {
    visitCommand(o);
  }

  public void visitSimpleCommandElement(@NotNull BashSimpleCommandElement o) {
    visitCompositeElement(o);
  }

  public void visitString(@NotNull BashString o) {
    visitCompositeElement(o);
  }

  public void visitSubshell(@NotNull BashSubshell o) {
    visitCompositeElement(o);
  }

  public void visitTimeOpt(@NotNull BashTimeOpt o) {
    visitCompositeElement(o);
  }

  public void visitTimespec(@NotNull BashTimespec o) {
    visitCompositeElement(o);
  }

  public void visitUntilCommand(@NotNull BashUntilCommand o) {
    visitCommand(o);
  }

  public void visitWhileCommand(@NotNull BashWhileCommand o) {
    visitCommand(o);
  }

  public void visitCompositeElement(@NotNull BashCompositeElement o) {
    visitElement(o);
  }

}
