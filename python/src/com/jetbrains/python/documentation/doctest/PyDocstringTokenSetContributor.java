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
package com.jetbrains.python.documentation.doctest;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyDocstringTokenSetContributor extends PythonDialectsTokenSetContributorBase {
  public static final TokenSet DOCSTRING_REFERENCE_EXPRESSIONS = TokenSet.create(PyDocstringTokenTypes.DOC_REFERENCE);

  @NotNull
  @Override
  public TokenSet getExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }

  @NotNull
  @Override
  public TokenSet getReferenceExpressionTokens() {
    return DOCSTRING_REFERENCE_EXPRESSIONS;
  }
}
