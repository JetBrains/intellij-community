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
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Function declaration in source (the <code>def</code> and everything within).
 *
 * @author yole
 */
public interface PyFunction extends PsiNamedElement, StubBasedPsiElement<PyFunctionStub>, PsiNameIdentifierOwner, PyStatement, PyCallable,
                                    PyDocStringOwner, ScopeOwner, PyDecoratable, PyTypedElement, PyStatementListContainer,
                                    PyPossibleClassMember {

  PyFunction[] EMPTY_ARRAY = new PyFunction[0];
  ArrayFactory<PyFunction> ARRAY_FACTORY = new ArrayFactory<PyFunction>() {
    @NotNull
    @Override
    public PyFunction[] create(int count) {
      return new PyFunction[count];
    }
  };

  /**
   * Returns the AST node for the function name identifier.
   *
   * @return the node, or null if the function is incomplete (only the "def"
   *         keyword was typed)
   */
  @Nullable
  ASTNode getNameNode();

  @Nullable
  PyType getReturnTypeFromDocString();

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning..
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  @Nullable
  String getDeprecationMessage();

  /**
   * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
   *
   * @return a flag describing what was detected.
   */
  @Nullable
  Modifier getModifier();

  boolean isAsync();

  /**
   * Flags that mark common alterations of a function: decoration by and wrapping in classmethod() and staticmethod().
   */
  enum Modifier {
    /**
     * Function is decorated with @classmethod, its first param is the class.
     */
    CLASSMETHOD,
    /**
     * Function is decorated with {@code @staticmethod}, its first param is as in a regular function.
     */
    STATICMETHOD,
  }

  /**
   * Returns a property for which this function is a getter, setter or deleter.
   *
   * @return the corresponding property, or null if there isn't any.
   */
  @Nullable
  Property getProperty();

  @Nullable
  PyAnnotation getAnnotation();

  /**
   * Returns a comment that immediately follows function header and precedes any child statement (including docstring).
   * It must start with conventional "type:" prefix and contain function type in PEP 484 compatible format
   * (https://www.python.org/dev/peps/pep-0484/#suggested-syntax-for-python-2-7-and-straddling-code).
   * <p/>
   * Use {@link #getTypeCommentAnnotation()} to get its content with the prefix stripped accessing either stubs or AST.
   *
   * @see #getTypeCommentAnnotation()
   */
  @Nullable
  PsiComment getTypeComment();

  /**
   * Returns type annotation after the "type:" prefix extracted from the commentary returned by {@link #getTypeComment()}.
   *
   * @see #getTypeComment()
   */
  @Nullable
  String getTypeCommentAnnotation();

  /**
   * Searches for function attributes.
   * See <a href="http://legacy.python.org/dev/peps/pep-0232/">PEP-0232</a>
   * @return assignment statements for function attributes
   */
  @NotNull
  List<PyAssignmentStatement> findAttributes();

  /**
   * @return function protection level (underscore based)
   */
  @NotNull
  ProtectionLevel getProtectionLevel();

  enum ProtectionLevel {
    /**
     * public members
     */
    PUBLIC(0),
    /**
     * _protected_memebers
     */
    PROTECTED(1),
    /**
     * __private_memebrs
     */
    PRIVATE(2);
    private final int myUnderscoreLevel;

    ProtectionLevel(final int underscoreLevel) {
      myUnderscoreLevel = underscoreLevel;
    }

    /**
     * @return number of underscores
     */
    public int getUnderscoreLevel() {
      return myUnderscoreLevel;
    }
  }
}
