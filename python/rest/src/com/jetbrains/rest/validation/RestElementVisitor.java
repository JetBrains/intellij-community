package com.jetbrains.rest.validation;

import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.rest.psi.*;

/**
 * User : catherine
 * Visitor for rest-specific nodes.
 */
public class RestElementVisitor extends PsiElementVisitor {
  public void visitRestElement(final RestElement node) {
    visitElement(node);
  }

  public void visitReference(final RestReference node) {
    visitRestElement(node);
  }

  public void visitReferenceTarget(final RestReferenceTarget node) {
    visitRestElement(node);
  }

  public void visitRole(final RestRole node) {
    visitRestElement(node);
  }

  public void visitTitle(final RestTitle node) {
    visitRestElement(node);
  }

  public void visitDirectiveBlock(final RestDirectiveBlock node) {
    visitRestElement(node);
  }

  public void visitInlineBlock(final RestInlineBlock node) {
    visitRestElement(node);
  }
}
