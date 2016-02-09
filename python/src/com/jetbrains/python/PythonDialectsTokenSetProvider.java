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
package com.jetbrains.python;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.tree.TokenSet;

/**
 * Provides element types of various kinds for known Python dialects.
 *
 * @author vlan
 */
public class PythonDialectsTokenSetProvider {
  public static PythonDialectsTokenSetProvider INSTANCE = new PythonDialectsTokenSetProvider();

  private final TokenSet myStatementTokens;
  private final TokenSet myExpressionTokens;
  private final TokenSet myKeywordTokens;
  private final TokenSet myParameterTokens;
  private final TokenSet myFunctionDeclarationTokens;
  private final TokenSet myUnbalancedBracesRecoveryTokens;
  private final TokenSet myReferenceExpressionTokens;

  private PythonDialectsTokenSetProvider() {
    TokenSet stmts = TokenSet.EMPTY;
    TokenSet exprs = TokenSet.EMPTY;
    TokenSet keywords = TokenSet.EMPTY;
    TokenSet parameters = TokenSet.EMPTY;
    TokenSet functionDeclarations = TokenSet.EMPTY;
    TokenSet recoveryTokens = TokenSet.EMPTY;
    TokenSet referenceExpressions = TokenSet.EMPTY;
    for(PythonDialectsTokenSetContributor contributor: Extensions.getExtensions(PythonDialectsTokenSetContributor.EP_NAME)) {
      stmts = TokenSet.orSet(stmts, contributor.getStatementTokens());
      exprs = TokenSet.orSet(exprs, contributor.getExpressionTokens());
      keywords = TokenSet.orSet(keywords, contributor.getKeywordTokens());
      parameters = TokenSet.orSet(parameters, contributor.getParameterTokens());
      functionDeclarations = TokenSet.orSet(functionDeclarations, contributor.getFunctionDeclarationTokens());
      recoveryTokens = TokenSet.orSet(recoveryTokens, contributor.getUnbalancedBracesRecoveryTokens());
      referenceExpressions = TokenSet.orSet(referenceExpressions, contributor.getReferenceExpressionTokens());
    }
    myStatementTokens = stmts;
    myExpressionTokens = exprs;
    myKeywordTokens = keywords;
    myParameterTokens = parameters;
    myFunctionDeclarationTokens = functionDeclarations;
    myUnbalancedBracesRecoveryTokens = recoveryTokens;
    myReferenceExpressionTokens = referenceExpressions;
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyStatement}.
   */
  public TokenSet getStatementTokens() {
    return myStatementTokens;
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyExpression}.
   */
  public TokenSet getExpressionTokens() {
    return myExpressionTokens;
  }

  /**
   * Returns all element types of Python dialects that are language keywords.
   */
  public TokenSet getKeywordTokens() {
    return myKeywordTokens;
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyParameter}.
   */
  public TokenSet getParameterTokens() {
    return myParameterTokens;
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyFunction}.
   */
  public TokenSet getFunctionDeclarationTokens() {
    return myFunctionDeclarationTokens;
  }

  /**
   * Returns all element types of Python dialects that can be used as unbalanced braces recovery tokens in the lexer.
   */
  public TokenSet getUnbalancedBracesRecoveryTokens() {
    return myUnbalancedBracesRecoveryTokens;
  }

  /**
   * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyReferenceExpression}.
   */
  public TokenSet getReferenceExpressionTokens() {
    return myReferenceExpressionTokens;
  }

  public static void reset() {
    INSTANCE = new PythonDialectsTokenSetProvider();
  }
}
