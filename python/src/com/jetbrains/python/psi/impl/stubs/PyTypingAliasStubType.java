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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub.InitializerType;
import com.jetbrains.python.psi.stubs.PyTypingAliasStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyTypingAliasStubType extends CustomTargetExpressionStubType<PyTypingAliasStub> {
  private static final int STRING_LITERAL_LENGTH_THRESHOLD = 100;

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
    final PyExpression value = getAssignedValueIfTypeAliasLike(psi, true);
    return value != null ? new PyTypingTypeAliasStubImpl(value.getText()) : null;
  }

  @Nullable
  private static PyExpression getAssignedValueIfTypeAliasLike(@NotNull PyTargetExpression target, boolean forStubCreation) {
    if (!PyUtil.isTopLevel(target) || !looksLikeTypeAliasTarget(target)) {
      return null;
    }
    final PyExpression value = target.findAssignedValue();
    if (value == null || !looksLikeTypeHint(value, forStubCreation)) {
      return null;
    }
    return value;
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

  private static boolean looksLikeTypeHint(@NotNull PyExpression expression, boolean forStubCreation) {
    final PyCallExpression call = as(expression, PyCallExpression.class);
    if (call != null) {
      final PyReferenceExpression callee = as(call.getCallee(), PyReferenceExpression.class);
      return callee != null && "TypeVar".equals(callee.getReferencedName());
    }

    final PyStringLiteralExpression pyString = as(expression, PyStringLiteralExpression.class);
    if (pyString != null) {
      if (pyString.getStringNodes().size() != 1 || pyString.getTextLength() > STRING_LITERAL_LENGTH_THRESHOLD) {
        return false;
      }
      final String content = pyString.getStringValue();
      return TYPE_ANNOTATION_LIKE.matcher(content).matches();
    }

    // Plain reference expressions are handled by PyTargetExpressionStub.getInitializer() 
    // when initializer type is ReferenceExpression. We don't want to override these stubs,
    // so as not to break existing resolve functionality (see PyTargetExpression.getAssignedQName()).
    if ((!forStubCreation && expression instanceof PyReferenceExpression) || expression instanceof PySubscriptionExpression) {
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
          return ((PyReferenceExpression)element).asQualifiedName() != null;
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


  /**
   * If the target expression's stub is present and the right hand value of the assignment is treated as
   * a valid type alias, retrieve its text from the stub, create {@link PyExpressionCodeFragment} from it,
   * and return the contained expression. Otherwise, get the assigned value from AST directly
   * but process it in the same way as if it was going to be saved in the stub.
   *
   * @see PyTypingAliasStub
   */
  @Nullable
  public static PyExpression getAssignedValueStubLike(@NotNull PyTargetExpression target) {
    final PyTargetExpressionStub stub = target.getStub();
    PyExpression result = null;
    if (stub != null) {
      final PyTypingAliasStub aliasStub = stub.getCustomStub(PyTypingAliasStub.class);
      String aliasText = null;
      if (aliasStub != null) {
        aliasText = aliasStub.getText();
      }
      else if (stub.getInitializerType() == InitializerType.ReferenceExpression) {
        aliasText = Objects.toString(stub.getInitializer(), null);
      }
      if (aliasText != null) {
        result = PyUtil.createExpressionFromFragment(aliasText, target.getContainingFile());
      }
    }
    else {
      // Use PSI to get the assigned value but only if the same expression would be saved in stubs
      result = getAssignedValueIfTypeAliasLike(target, false);
    }
    return result;
  }
}
