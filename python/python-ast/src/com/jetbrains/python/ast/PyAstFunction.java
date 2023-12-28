// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Function declaration in source (the {@code def} and everything within).
 */
@ApiStatus.Experimental
public interface PyAstFunction extends PsiNameIdentifierOwner, PyAstCompoundStatement,
                                       PyAstDecoratable, PyAstCallable, PyAstStatementListContainer, PyAstPossibleClassMember,
                                       AstScopeOwner, PyAstDocStringOwner, PyAstTypeCommentOwner, PyAstAnnotationOwner, PyAstTypeParameterListOwner {

  PyAstFunction[] EMPTY_ARRAY = new PyAstFunction[0];
  ArrayFactory<PyAstFunction> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PyAstFunction[count];

  @Override
  @Nullable
  default String getName() {
    ASTNode node = getNameNode();
    return node != null ? node.getText() : null;
  }

  @Override
  @Nullable
  default PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  /**
   * Returns the AST node for the function name identifier.
   *
   * @return the node, or null if the function is incomplete (only the "def"
   *         keyword was typed)
   */
  @Nullable
  default ASTNode getNameNode() {
    ASTNode id = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    if (id == null) {
      ASTNode error = getNode().findChildByType(TokenType.ERROR_ELEMENT);
      if (error != null) {
        id = error.findChildByType(PythonDialectsTokenSetProvider.getInstance().getKeywordTokens());
      }
    }
    return id;
  }

  @Override
  @NotNull
  default PyAstStatementList getStatementList() {
    final PyAstStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for function " + getText();
    return statementList;
  }

  @Override
  @Nullable
  default PyAstFunction asMethod() {
    if (getContainingClass() != null) {
      return this;
    }
    else {
      return null;
    }
  }

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning..
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  @Nullable
  default String getDeprecationMessage() {
    return extractDeprecationMessage();
  }

  @Nullable
  default String extractDeprecationMessage() {
    PyAstStatementList statementList = getStatementList();
    return extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
  }

  static @Nullable String extractDeprecationMessage(List<? extends PyAstStatement> statements) {
    for (PyAstStatement statement : statements) {
      if (statement instanceof PyAstExpressionStatement expressionStatement) {
        if (expressionStatement.getExpression() instanceof PyAstCallExpression callExpression) {
          if (callExpression.isCalleeText(PyNames.WARN)) {
            PyAstReferenceExpression warningClass = callExpression.getArgument(1, PyAstReferenceExpression.class);
            if (warningClass != null && (PyNames.DEPRECATION_WARNING.equals(warningClass.getReferencedName()) ||
                                         PyNames.PENDING_DEPRECATION_WARNING.equals(warningClass.getReferencedName()))) {
              return PyPsiUtilsCore.strValue(callExpression.getArguments()[0]);
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  default String getDocStringValue() {
    return DocStringUtilCore.getDocStringValue(this);
  }

  @Override
  default int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : getNode().getStartOffset();
  }

  @Override
  @Nullable
  default PyAstStringLiteralExpression getDocStringExpression() {
    final PyAstStatementList stmtList = getStatementList();
    return DocStringUtilCore.findDocStringExpression(stmtList);
  }

  @Override
  @Nullable
  default PyAstTypeParameterList getTypeParameterList() {
    //noinspection unchecked
    return this.<PyAstTypeParameterList>getStubOrPsiChild(PyElementTypes.TYPE_PARAMETER_LIST);
  }

  /**
   * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
   *
   * @return a flag describing what was detected.
   */
  @Nullable
  Modifier getModifier();

  default boolean isAsync() {
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }

  default boolean isAsyncAllowed() {
    final LanguageLevel languageLevel = LanguageLevel.forElement(this);
    if (languageLevel.isOlderThan(LanguageLevel.PYTHON35)) return false;

    final String functionName = getName();

    if (functionName == null ||
        ArrayUtil.contains(functionName, PyNames.AITER, PyNames.ANEXT, PyNames.AENTER, PyNames.AEXIT, PyNames.CALL)) {
      return true;
    }

    final Map<String, PyNames.BuiltinDescription> builtinMethods =
      asMethod() != null ? PyNames.getBuiltinMethods(languageLevel) : PyNames.getModuleBuiltinMethods(languageLevel);

    return !builtinMethods.containsKey(functionName);
  }

  default boolean onlyRaisesNotImplementedError() {
    final PyAstStatement[] statements = getStatementList().getStatements();
    return statements.length == 1 && isRaiseNotImplementedError(statements[0]) ||
           statements.length == 2 && PyUtilCore.isStringLiteral(statements[0]) && isRaiseNotImplementedError(statements[1]);
  }

  private static boolean isRaiseNotImplementedError(@NotNull PyAstStatement statement) {
    final PyAstExpression raisedExpression = Optional
      .ofNullable(ObjectUtils.tryCast(statement, PyAstRaiseStatement.class))
      .map(PyAstRaiseStatement::getExpressions)
      .filter(expressions -> expressions.length == 1)
      .map(expressions -> expressions[0])
      .orElse(null);

    if (raisedExpression instanceof PyAstCallExpression) {
      final PyAstExpression callee = ((PyAstCallExpression)raisedExpression).getCallee();
      if (callee != null && callee.getText().equals(PyNames.NOT_IMPLEMENTED_ERROR)) {
        return true;
      }
    }
    else if (raisedExpression != null && raisedExpression.getText().equals(PyNames.NOT_IMPLEMENTED_ERROR)) {
      return true;
    }

    return false;
  }

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
   * @return function protection level (underscore based)
   */
  @NotNull
  default ProtectionLevel getProtectionLevel() {
    final int underscoreLevels = PyUtilCore.getInitialUnderscores(getName());
    for (final ProtectionLevel level : ProtectionLevel.values()) {
      if (level.getUnderscoreLevel() == underscoreLevels) {
        return level;
      }
    }
    return ProtectionLevel.PRIVATE;
  }

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

  @Override
  @Nullable
  default PyAstDecoratorList getDecoratorList() {
    //noinspection unchecked
    return this.<PyAstDecoratorList>getStubOrPsiChild(PyElementTypes.DECORATOR_LIST); // PsiTreeUtil.getChildOfType(this, PyDecoratorList.class);
  }

  @Override
  default PyAstAnnotation getAnnotation() {
    //noinspection unchecked
    return this.<PyAstAnnotation>getStubOrPsiChild(PyElementTypes.ANNOTATION);
  }

  @Override
  default @NotNull PyAstParameterList getParameterList() {
    //noinspection unchecked,rawtypes
    return this.<StubElement, PyAstParameterList>getRequiredStubOrPsiChild(PyElementTypes.PARAMETER_LIST);
  }

  @Override
  @Nullable
  default PyAstClass getContainingClass() {
    final PsiElement parent = PsiTreeUtil.getParentOfType(this, StubBasedPsiElement.class);
    if (parent instanceof PyAstClass) {
      return (PyAstClass)parent;
    }
    return null;
  }
}
