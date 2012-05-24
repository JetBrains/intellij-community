// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;

public class Visitor extends PsiElementVisitor {

  public void visitAssignment(@NotNull Assignment o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitCodeBlock(@NotNull CodeBlock o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitConditionElement(@NotNull ConditionElement o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitCopying(@NotNull Copying o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitIncludeStatementElement(@NotNull IncludeStatementElement o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitMultilineValueAssignment(@NotNull MultilineValueAssignment o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitObjectPath(@NotNull ObjectPath o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitUnsetting(@NotNull Unsetting o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitValueModification(@NotNull ValueModification o) {
    visitTypoScriptCompositeElement(o);
  }

  public void visitTypoScriptCompositeElement(@NotNull TypoScriptCompositeElement o) {
    visitElement(o);
  }

}
