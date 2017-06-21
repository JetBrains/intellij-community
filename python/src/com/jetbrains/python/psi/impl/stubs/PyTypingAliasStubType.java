/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyTypingAliasStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyTypingAliasStubType extends CustomTargetExpressionStubType<PyTypingAliasStub> {
  private static final int STRING_LITERAL_LENGTH_THRESHOLD = 120;

  private static final Pattern TYPE_ANNOTATION_LIKE = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*" +
                                                                      "(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*" +
                                                                      "(\\[.*])?$");

  private static final TokenSet VALID_TYPE_ANNOTATION_ELEMENTS = TokenSet.create(PyElementTypes.REFERENCE_EXPRESSION,
                                                                                 PyElementTypes.SUBSCRIPTION_EXPRESSION,
                                                                                 PyElementTypes.TUPLE_EXPRESSION, 
                                                                                 // List of types is allowed only inside Callable[...]
                                                                                 PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                                                 PyElementTypes.STRING_LITERAL_EXPRESSION);

  @Nullable
  @Override
  public PyTypingAliasStub createStub(PyTargetExpression psi) {
    if (!PyUtil.isTopLevel(psi) || !looksLikeTypeAliasTarget(psi)) {
      return null;
    }
    final PyExpression value = psi.findAssignedValue();
    if (value == null || !looksLikeTypeHint(value)) {
      return null;
    }
    return new PyTypingTypeAliasStubImpl(value.getText());
  }

  private static boolean looksLikeTypeAliasTarget(@NotNull PyTargetExpression target) {
    if (target.isQualified()) {
      return false;
    }
    final String name = target.getName();
    if (name == null || PyUtil.isSpecialName(name)) {
      return false;
    }
    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
    if (assignment == null) {
      return false;
    }
    final PyExpression[] targets = assignment.getRawTargets();
    return targets.length == 1 && targets[0] == target;
  }

  private static boolean looksLikeTypeHint(@NotNull PyExpression expression) {
    final PyCallExpression call = as(expression, PyCallExpression.class);
    if (call != null) {
      final PyReferenceExpression callee = as(call.getCallee(), PyReferenceExpression.class);
      return callee != null && "TypeVar".equals(callee.getReferencedName());
    }

    final PyStringLiteralExpression pyString = as(expression, PyStringLiteralExpression.class);
    if (pyString != null) {
      if (pyString.getStringNodes().size() != 1 && pyString.getTextLength() > STRING_LITERAL_LENGTH_THRESHOLD) {
        return false;
      }
      final String content = pyString.getStringValue();
      return TYPE_ANNOTATION_LIKE.matcher(content).matches();
    }

    if (expression instanceof PyReferenceExpression || expression instanceof PySubscriptionExpression) {
      return isSyntacticallyValidAnnotation(expression);
    }
    
    return false;
  }

  private static boolean isSyntacticallyValidAnnotation(@NotNull PyExpression expression) {
    return PsiTreeUtil.processElements(expression, element -> {
      // Check only composite elements
      if (element instanceof ASTDelegatePsiElement) {
        if (!VALID_TYPE_ANNOTATION_ELEMENTS.contains(element.getNode().getElementType())) {
          return false;
        }
        if (element instanceof PyReferenceExpression) {
          // too complex reference expression, e.g. foo[bar].baz
          return PyTypingTypeProvider.turnPlainReferenceExpressionIntoQualifiedName((PyReferenceExpression)element) != null;
        }
      }
      return true;
    });
  }

  @Nullable
  @Override
  public PyTypingAliasStub deserializeStub(StubInputStream stream) throws IOException {
    final StringRef ref = stream.readName();
    return ref != null ? new PyTypingTypeAliasStubImpl(ref.getString()) : null;
  }
}
