// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.FunctionParameter;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyKnownDecorator.*;

/**
 * Contains list of well-behaved decorators from Pythons standard library, that don't change
 * signature of underlying function/class or use it implicitly somewhere (e.g. register as a callback).
 *
 * @author Mikhail Golubev
 */
public final class PyKnownDecoratorUtil {

  private PyKnownDecoratorUtil() {
  }

  /**
   * Map decorators of element to {@link PyKnownDecorator}.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @return list of known decorators in declaration order with duplicates (with any)
   */
  public static @NotNull List<PyKnownDecorator> getKnownDecorators(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    final PyDecoratorList decoratorList = element.getDecoratorList();
    if (decoratorList == null) {
      return Collections.emptyList();
    }

    return StreamEx.of(decoratorList.getDecorators())
      .flatMap(decorator -> asKnownDecorators(decorator, context).stream())
      .toImmutableList();
  }

  public static @NotNull List<PyKnownDecorator> asKnownDecorators(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    final QualifiedName qualifiedName = decorator.getQualifiedName();
    if (qualifiedName == null) {
      return Collections.emptyList();
    }
    if (context.maySwitchToAST(decorator)) {
      PsiFile containingFile = decorator.getContainingFile();
      List<PsiElement> resolved;
      if (containingFile instanceof PyiFile) {
        // In .pyi files it's safe to resolve decorators such as "@overload" flow-insensitively.
        resolved = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, (ScopeOwner)containingFile, context);
      }
      else {
        resolved = PyUtil.multiResolveTopPriority(Objects.requireNonNull(decorator.getCallee()), PyResolveContext.defaultContext(context));
      }
      return StreamEx.of(resolved)
        .select(PyQualifiedNameOwner.class)
        .map(PyQualifiedNameOwner::getQualifiedName)
        .nonNull()
        .map(QualifiedName::fromDottedString)
        .map(PyKnownDecoratorUtil::findByQualifiedName)
        .nonNull()
        .toImmutableList();
    }
    else {
      return asKnownDecorators(qualifiedName);
    }
  }

  @ApiStatus.Internal
  public static @NotNull List<PyKnownDecorator> asKnownDecorators(@NotNull QualifiedName qualifiedName) {
    // The method might have been called during building of PSI stub indexes. Thus, we can't leave this file's boundaries.
    // TODO Use proper local resolve to imported names here
    String lastComponent = qualifiedName.getLastComponent();
    if (lastComponent == null) {
      return Collections.emptyList();
    }
    return findByShortName(lastComponent);
  }

  /**
   * Check that given element has any non-standard (read "unreliable") decorators.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @see PyKnownDecorator
   */
  public static boolean hasUnknownDecorator(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    return !allDecoratorsAreKnown(element, getKnownDecorators(element, context));
  }

  /**
   * Checks that given function has any decorators from {@code abc} module.
   *
   * @param element Python function to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @see PyKnownDecorator
   */
  public static boolean hasAbstractDecorator(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    return ContainerUtil.exists(getKnownDecorators(element, context), knownDecorator -> knownDecorator.isAbstract());
  }

  public static boolean hasGeneratorBasedCoroutineDecorator(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return ContainerUtil.exists(getKnownDecorators(function, context), knownDecorator -> knownDecorator.isGeneratorBasedCoroutine());
  }

  public static boolean isResolvedToGeneratorBasedCoroutine(@NotNull PyCallExpression receiver,
                                                            @NotNull PyResolveContext resolveContext,
                                                            @NotNull TypeEvalContext typeEvalContext) {
    return StreamEx
      .of((receiver).multiResolveCalleeFunction(resolveContext))
      .select(PyFunction.class)
      .anyMatch(function -> hasGeneratorBasedCoroutineDecorator(function, typeEvalContext));
  }

  public static boolean hasRedeclarationDecorator(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return getKnownDecorators(function, context).contains(TYPING_OVERLOAD);
  }

  public static boolean hasUnknownOrChangingSignatureDecorator(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    final List<PyKnownDecorator> decorators = getKnownDecorators(decoratable, context);
    return !allDecoratorsAreKnown(decoratable, decorators) || decorators.contains(UNITTEST_MOCK_PATCH);
  }

  public static boolean hasUnknownOrChangingReturnTypeDecorator(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    final List<PyKnownDecorator> decorators = getKnownDecorators(decoratable, context);

    if (!allDecoratorsAreKnown(decoratable, decorators)) {
      return true;
    }

    return ContainerUtil.exists(decorators, d -> d == UNITTEST_MOCK_PATCH);
  }

  public static boolean hasChangingReturnTypeDecorator(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    final List<PyKnownDecorator> decorators = getKnownDecorators(decoratable, context);
    return ContainerUtil.exists(decorators, d -> d == UNITTEST_MOCK_PATCH);
  }

  public static boolean hasUnknownOrUpdatingAttributesDecorator(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    final List<PyKnownDecorator> decorators = getKnownDecorators(decoratable, context);

    if (!allDecoratorsAreKnown(decoratable, decorators)) {
      return true;
    }

    return ContainerUtil.exists(
      decorators,
      d ->
        d == FUNCTOOLS_LRU_CACHE || // cache_clear, cache_info
        d == FUNCTOOLS_SINGLEDISPATCH // dispatch, register, registry
    );
  }

  private static boolean allDecoratorsAreKnown(@NotNull PyDecoratable element, @NotNull List<PyKnownDecorator> decorators) {
    final PyDecoratorList decoratorList = element.getDecoratorList();
    return decoratorList == null
           ? decorators.isEmpty()
           : decoratorList.getDecorators().length == StreamEx.of(decorators).groupingBy(PyKnownDecorator::getShortName).size();
  }

  private static List<PyKnownDecorator> findByShortName(@NotNull String shortName) {
    return PyKnownDecoratorProvider.EP_NAME.getExtensionList().stream()
      .flatMap(knownDecoratorProvider -> {
        Collection<PyKnownDecorator> decorators = knownDecoratorProvider.getKnownDecorators();
        if (!decorators.isEmpty()) {
          return decorators.stream();
        }
        // Fallback to the old implementation that will be removed in the future release
        String knownDecorator = knownDecoratorProvider.toKnownDecorator(shortName);
        if (knownDecorator != null && !knownDecorator.isEmpty() && !knownDecorator.equals(shortName)) {
          return StreamEx.of(findByShortName(knownDecorator));
        }
        return StreamEx.empty();
      })
      .filter(knownDecorator -> knownDecorator.getShortName().equals(shortName))
      .toList();
  }

  private static @Nullable PyKnownDecorator findByQualifiedName(@NotNull QualifiedName qualifiedName) {
    return PyKnownDecoratorProvider.EP_NAME.getExtensionList().stream()
      .flatMap(knownDecoratorProvider -> knownDecoratorProvider.getKnownDecorators().stream())
      .filter(knownDecorator -> knownDecorator.getQualifiedName().equals(qualifiedName))
      .findFirst()
      .orElse(null);
  }

  public enum FunctoolsWrapsParameters implements FunctionParameter {
    WRAPPED(0, "wrapped");

    private final int myPosition;
    private final String myName;

    FunctoolsWrapsParameters(int position, @NotNull String name) {
      myPosition = position;
      myName = name;
    }

    @Override
    public int getPosition() {
      return myPosition;
    }

    @Override
    public @NotNull String getName() {
      return myName;
    }
  }
}
