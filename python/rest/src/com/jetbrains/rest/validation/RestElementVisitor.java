/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
