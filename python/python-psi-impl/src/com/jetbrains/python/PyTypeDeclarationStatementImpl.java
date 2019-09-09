/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.impl.PyElementImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyTypeDeclarationStatementImpl extends PyElementImpl implements PyTypeDeclarationStatement {
  public PyTypeDeclarationStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  @Override
  public PyExpression getTarget() {
    return findNotNullChildByClass(PyExpression.class);
  }

  @Nullable
  @Override
  public PyAnnotation getAnnotation() {
    return findChildByClass(PyAnnotation.class);
  }

  @Nullable
  @Override
  public String getAnnotationValue() {
    return getAnnotationContentFromPsi(this);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeDeclarationStatement(this);
  }
}
