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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub.InitializerType;
import com.jetbrains.python.psi.stubs.PyTypingAliasStub;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public final class PyTypingAliasStubType extends CustomTargetExpressionStubType<PyTypingAliasStub> {
  private static final int STRING_LITERAL_LENGTH_THRESHOLD = 100;

  public static final Pattern RE_TYPE_HINT_LIKE_STRING = Pattern.compile(
    """
      (?x)
      \\s*
      \\S+(\\[.*])?   # initial type like: "list[int]"
      (\\s*[|&]\\s*   # union or intersection operators: " | " or " & "
        \\S+(\\[.*])? # operand types
      )*              # repeating
      \\s*
      """,
    Pattern.DOTALL
  );

  private static final TokenSet VALID_TYPE_ANNOTATION_ELEMENTS = TokenSet.create(PyElementTypes.REFERENCE_EXPRESSION,
                                                                                 PyElementTypes.SUBSCRIPTION_EXPRESSION,
                                                                                 PyElementTypes.TUPLE_EXPRESSION,
                                                                                 // List of types is allowed only inside Callable[...]
                                                                                 PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                                                 PyElementTypes.STRING_LITERAL_EXPRESSION,
                                                                                 PyElementTypes.NONE_LITERAL_EXPRESSION,
                                                                                 PyElementTypes.ELLIPSIS_LITERAL_EXPRESSION,
                                                                                 PyElementTypes.STAR_EXPRESSION);

  @Override
  public @Nullable PyTypingAliasStub createStub(@NotNull PyTargetExpression psi) {
    final PyExpression value = getAssignedValueIfTypeAliasLike(psi, true);
    return value != null ? new PyTypingTypeAliasStubImpl(value.getText()) : null;
  }

  private static @Nullable PyExpression getAssignedValueIfTypeAliasLike(@NotNull PyTargetExpression target, boolean forStubCreation) {
    if (!(PyUtil.isTopLevel(target) || ScopeUtil.getScopeOwner(target) instanceof PyClass) || !looksLikeTypeAliasTarget(target)) {
      return null;
    }
    final PyExpression value = target.findAssignedValue();

    // Plain reference expressions are handled by PyTargetExpressionStub.getInitializer()
    // when initializer type is ReferenceExpression. We don't want to override these stubs,
    // so as not to break existing resolve functionality (see PyTargetExpression.getAssignedQName()).
    if (value == null || forStubCreation && value instanceof PyReferenceExpression) {
      return null;
    }

    if (isExplicitTypeAlias(target)) {
      return value;
    }
    // Typing specification doesn't allow fully quoted values of implicit type aliases
    // because they ambiguous with ordinary top-level string variables.
    // For instance:
    //
    // FOO = "int | str"
    //
    // is not considered a valid type alias, even though the string content looks like a type hint.
    // See "BadTypeAlias14" in aliases_implicit.py of the conformance test suite.
    if (target.getAnnotation() == null && !(value instanceof PyStringLiteralExpression) && looksLikeTypeHint(value)) {
      return value;
    }
    return null;
  }

  private static boolean looksLikeTypeAliasTarget(@NotNull PyTargetExpression target) {
    if (target.isQualified()) {
      return false;
    }
    final String name = target.getName();
    if (name == null || PyUtilCore.isSpecialName(name)) {
      return false;
    }
    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
    if (assignment == null) {
      return false;
    }
    final PyExpression[] targets = assignment.getRawTargets();
    return targets.length == 1 && targets[0] == target;
  }

  private static boolean isExplicitTypeAlias(@NotNull PyTargetExpression target) {
    PyAnnotation annotation = target.getAnnotation();
    if (annotation != null) {
      PyExpression value = annotation.getValue();
      if (value instanceof PyReferenceExpression referenceExpression) {
        return StreamEx.of(PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression))
          .map(QualifiedName::toString)
          .anyMatch(name -> name.equals(PyTypingTypeProvider.TYPE_ALIAS) || name.equals(PyTypingTypeProvider.TYPE_ALIAS_EXT));
      }
    }
    else {
      String typeHintText = StringUtil.notNullize(target.getTypeCommentAnnotation());
      return typeHintText.equals("TypeAlias") || typeHintText.endsWith(".TypeAlias");
    }
    return false;
  }

  @ApiStatus.Internal
  public static boolean looksLikeTypeHint(@NotNull PyExpression expression) {
    final PyCallExpression call = as(expression, PyCallExpression.class);
    if (call != null) {
      final PyReferenceExpression callee = as(call.getCallee(), PyReferenceExpression.class);
      return callee != null &&
             ("TypeVar".equals(callee.getReferencedName()) || "TypeVarTuple".equals(callee.getReferencedName()) ||
              "ParamSpec".equals(callee.getReferencedName()));
    }
    return isSyntacticallyValidAnnotation(expression);
  }

  private static boolean isSyntacticallyValidAnnotation(@NotNull PyExpression expression) {
    boolean[] illegal = {false};
    expression.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPySubscriptionExpression(@NotNull PySubscriptionExpression node) {
        if (node.getOperand() instanceof PyReferenceExpression refExpr &&
            "Annotated".equals(refExpr.getName()) &&
            node.getIndexExpression() instanceof PyTupleExpression tupleExpr) {
          refExpr.accept(this);
          tupleExpr.getElements()[0].accept(this);
        }
        else if (node.getOperand() instanceof PyReferenceExpression refExpr &&
                 "Literal".equals(refExpr.getName())) {
          refExpr.accept(this);
        }
        else {
          super.visitPySubscriptionExpression(node);
        }
      }

      @Override
      public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
        if (!(node.getOperator() == PyTokenTypes.OR || node.getOperator() == PyTokenTypes.AND)) {
          illegal[0] = true;
          return;
        }
        node.getLeftExpression().accept(this);
        PyExpression rightExpression = node.getRightExpression();
        if (rightExpression != null) {
          rightExpression.accept(this);
        }
      }

      @Override
      public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression node) {
        boolean nonTrivial = (node.isInterpolated()
                              || node.getStringNodes().size() != 1
                              || node.getTextLength() > STRING_LITERAL_LENGTH_THRESHOLD
                              || !node.getStringElements().getFirst().getPrefix().isEmpty()
                              || !RE_TYPE_HINT_LIKE_STRING.matcher(node.getStringValue()).matches());
        if (nonTrivial) {
          illegal[0] = true;
        }
      }

      @Override
      public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
        if (node.asQualifiedName() == null) {
          illegal[0] = true;
        }
      }

      @Override
      public void visitPyListLiteralExpression(@NotNull PyListLiteralExpression node) {
        if (node == expression) {
          illegal[0] = true;
          return;
        }
        super.visitElement(node);
      }

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof ASTDelegatePsiElement) {
          if (!VALID_TYPE_ANNOTATION_ELEMENTS.contains(element.getNode().getElementType())) {
            illegal[0] = true;
            return;
          }
          super.visitElement(element);
        }
      }
    });
    return !illegal[0];
  }

  @Override
  public @Nullable PyTypingAliasStub deserializeStub(@NotNull StubInputStream stream) throws IOException {
    String ref = stream.readNameString();
    return ref != null ? new PyTypingTypeAliasStubImpl(ref) : null;
  }


  /**
   * If the target expression's stub is present and the right hand value of the assignment is treated as
   * a valid type alias, retrieve its text from the stub, create {@link PyExpressionCodeFragment} from it,
   * and return the contained expression. Otherwise, get the assigned value from AST directly
   * but process it in the same way as if it was going to be saved in the stub.
   *
   * @see PyTypingAliasStub
   */
  public static @Nullable PyExpression getAssignedValueStubLike(@NotNull PyTargetExpression target) {
    return CachedValuesManager.getCachedValue(target, () -> {
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
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
