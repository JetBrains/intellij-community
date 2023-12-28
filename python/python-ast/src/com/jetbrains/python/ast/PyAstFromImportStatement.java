/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByType;

/**
 * Describes "from ... import" statements.
 */
@ApiStatus.Experimental
public interface PyAstFromImportStatement extends PyAstImportStatementBase,
                                                  PyAstImplicitImportNameDefiner {

  default boolean isStarImport() {
    return getStarImportElement() != null;
  }

  /**
   * Returns a reference the module from which import is required.
   * @return reference to module. If the 'from' reference is relative and consists entirely of dots, null is returned.
   */
  @Nullable
  default PyAstReferenceExpression getImportSource() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens(), 0);
  }

  @Nullable
  default QualifiedName getImportSourceQName() {
    final PyAstReferenceExpression importSource = getImportSource();
    if (importSource == null) {
      return null;
    }
    return importSource.asQualifiedName();
  }

  /**
   * @return the star in "from ... import *"
   */
  default @Nullable PyAstStarImportElement getStarImportElement() {
    //noinspection unchecked
    return this.<PyAstStarImportElement>getStubOrPsiChild(PyElementTypes.STAR_IMPORT_ELEMENT);
  }

  /**
   * @return number of dots in relative "from" clause, or 0 in absolute import.
   */
  default int getRelativeLevel() {
    int result = 0;
    ASTNode seeker = getNode().getFirstChildNode();
    while (seeker != null && (seeker.getElementType() == PyTokenTypes.FROM_KEYWORD || seeker.getElementType() == TokenType.WHITE_SPACE)) {
      seeker = seeker.getTreeNext();
    }
    while (seeker != null && seeker.getElementType() == PyTokenTypes.DOT) {
      result++;
      seeker = seeker.getTreeNext();
    }
    return result;
  }

  /**
   * @return true iff the statement is an import from __future__.
   */
  default boolean isFromFuture() {
    final QualifiedName qName = getImportSourceQName();
    return qName != null && qName.matches(PyNames.FUTURE_MODULE);
  }

  /**
   * If the from ... import statement uses an import list in parentheses, returns the opening parenthesis.
   *
   * @return opening parenthesis token or null
   */
  @Nullable
  default PsiElement getLeftParen()  {
    return findChildByType(this, PyTokenTypes.LPAR);
  }

  /**
   * If the from ... import statement uses an import list in parentheses, returns the closing parenthesis.
   *
   * @return closing parenthesis token or null
   */
  @Nullable
  default PsiElement getRightParen() {
    return findChildByType(this, PyTokenTypes.RPAR);
  }

  @NotNull
  @Override
  default List<String> getFullyQualifiedObjectNames() {
    final QualifiedName source = getImportSourceQName();

    final String prefix = (source != null) ? (source.join(".") + '.') : "";

    final List<String> unqualifiedNames = PyAstImportStatement.getImportElementNames(getImportElements());

    final List<String> result = new ArrayList<>(unqualifiedNames.size());

    for (final String unqualifiedName : unqualifiedNames) {
      result.add(prefix + unqualifiedName);
    }
    return result;
  }
}
