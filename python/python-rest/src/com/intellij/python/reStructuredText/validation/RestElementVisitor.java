// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.validation;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.python.reStructuredText.psi.*;

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
