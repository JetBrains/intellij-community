// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Represents a type of an expression.
 */
public interface PyType {

  /**
   * Returns the declaration element that can be used to refer to this type inside type hints. Normally, it's a symbol
   * that can be imported to mentioned the type in type annotations and comments anywhere else.
   * <p>
   * Typical examples are target expressions in LHS of assignments in {@code TypeVar} and named tuple definitions, as well as
   * class definitions themselves for plain class and generic types.
   */
  default @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return null;
  }

  /**
   * Resolves an attribute of type.
   *
   * @param name     attribute name
   * @param location the expression of type qualifierType on which the member is being resolved (optional)
   * @return null if name definitely cannot be found (e.g. in a qualified reference),
   * or an empty list if name is not found but other contexts are worth looking at,
   * or a list of elements that define the name, a la multiResolve().
   */
  @Nullable
  List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                   final @Nullable PyExpression location,
                                                   final @NotNull AccessDirection direction,
                                                   final @NotNull PyResolveContext resolveContext);

  @ApiStatus.Experimental
  @Nullable
  default List<@NotNull PyTypedResolveResult> getMemberTypes(@NotNull String name,
                                                             final @Nullable PyExpression location,
                                                             final @NotNull AccessDirection direction,
                                                             final @NotNull PyResolveContext context) {
    for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
      List<PyTypedResolveResult> types = typeProvider.getMemberTypes(this, name, location, direction, context);
      if (types != null) {
        return types;
      }
    }

    List<? extends RatedResolveResult> results = resolveMember(name, location, direction, context);
    if (results == null) {
      return null;
    }

    return ContainerUtil.map(results, result -> {
      PsiElement element = result.getElement();
      if (element instanceof PyTypedElement typedElement) {
        return new PyTypedResolveResult(typedElement,
                                        context.getTypeEvalContext().getType(typedElement));
      }
      else {
        return new PyTypedResolveResult(element, null);
      }
    });
  }

  /**
   * Proposes completion variants from type's attributes.
   *
   * @param location the reference on which the completion was invoked
   * @param context  to share state between nested invocations
   * @return completion variants good for {@link com.intellij.psi.PsiReference#getVariants} return value.
   */
  Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context);

  /**
   * Context key for access to a set of names already found by variant search.
   */
  Key<Set<String>> CTX_NAMES = new Key<>("Completion variants names");

  /**
   * TODO rename it to something like getPresentableName(), because it's not clear that these names are actually visible to end-user
   *
   * @return name of the type
   */
  @Nullable
  @NlsSafe
  String getName();

  /**
   * @return true if the type is a known built-in type.
   */
  boolean isBuiltin();

  void assertValid(String message);

  /**
   * For nullable {@code PyType} instance use {@link PyTypeVisitor#visit(PyType, PyTypeVisitor)}
   * to visit {@code null} values with {@link PyTypeVisitor#visitUnknownType()}.
   */
  @ApiStatus.Experimental
  default <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyType(this);
  }
}
