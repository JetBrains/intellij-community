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
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.*;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Contains list of well-behaved decorators from Pythons standard library, that don't change
 * signature of underlying function/class or use it implicitly somewhere (e.g. register as a callback).
 *
 * @author Mikhail Golubev
 */
public class PyKnownDecoratorUtil {

  private PyKnownDecoratorUtil() {
  }

  // TODO provide more information about these decorators: attributes (e.g. lru_cache(f).cache_info), side-effects etc.
  @SuppressWarnings("SpellCheckingInspection")
  public enum KnownDecorator {

    STATICMETHOD(PyNames.STATICMETHOD),
    CLASSMETHOD(PyNames.CLASSMETHOD),
    PROPERTY(PyNames.PROPERTY),

    CONTEXTLIB_CONTEXTMANAGER("contextlib.contextmanager"),

    FUNCTOOLS_LRU_CACHE("functools.lru_cache"),
    FUNCTOOLS_WRAPS("functools.wraps"),
    FUNCTOOLS_TOTAL_ORDERING("functools.total_ordering"),
    FUNCTOOLS_SINGLEDISPATCH("functools.singledispatch"),

    ABC_ABSTRACTMETHOD("abc.abstractmethod"),
    ABC_ABSTRACTCLASSMETHOD("abc.abstractclassmethod"),
    ABC_ABSTRACTSTATICMETHOD("abc.abstractstaticmethod"),
    ABC_ABSTRACTPROPERTY("abc.abstractproperty"),

    //ATEXIT_REGISTER("atexit.register", true),
    //ATEXIT_UNREGISTER("atexit.unregister", false),

    ASYNCIO_TASKS_COROUTINE("asyncio.tasks.coroutine"),
    ASYNCIO_COROUTINES_COROUTINE("asyncio.coroutines.coroutine"),
    TYPES_COROUTINE("types.coroutine"),

    UNITTEST_SKIP("unittest.case.skip"),
    UNITTEST_SKIP_IF("unittest.case.skipIf"),
    UNITTEST_SKIP_UNLESS("unittest.case.skipUnless"),
    UNITTEST_EXPECTED_FAILURE("unittest.case.expectedFailure"),
    UNITTEST_MOCK_PATCH("unittest.mock.patch"),

    TYPING_OVERLOAD("typing.overload"),

    REPRLIB_RECURSIVE_REPR("reprlib.recursive_repr"),

    PYRAMID_DECORATOR_REIFY("pyramid.decorator.reify"),
    DJANGO_UTILS_FUNCTIONAL_CACHED_PROPERTY("django.utils.functional.cached_property"),
    KOMBU_UTILS_CACHED_PROPERTY("kombu.utils.cached_property");

    private final QualifiedName myQualifiedName;

    KnownDecorator(@NotNull String qualifiedName) {
      myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    }

    @NotNull
    public QualifiedName getQualifiedName() {
      return myQualifiedName;
    }

    @NotNull
    public String getShortName() {
      //noinspection ConstantConditions
      return myQualifiedName.getLastComponent();
    }
  }

  private static final Set<KnownDecorator> BUILTIN_DECORATORS = EnumSet.of(PROPERTY, CLASSMETHOD, STATICMETHOD, TYPING_OVERLOAD);
  private static final Set<KnownDecorator> ABSTRACT_DECORATORS = EnumSet.of(ABC_ABSTRACTMETHOD,
                                                                            ABC_ABSTRACTPROPERTY,
                                                                            ABC_ABSTRACTSTATICMETHOD,
                                                                            ABC_ABSTRACTCLASSMETHOD);

  private static final Set<KnownDecorator> PROPERTY_DECORATORS = EnumSet.of(PROPERTY,
                                                                            ABC_ABSTRACTPROPERTY,
                                                                            PYRAMID_DECORATOR_REIFY,
                                                                            DJANGO_UTILS_FUNCTIONAL_CACHED_PROPERTY,
                                                                            KOMBU_UTILS_CACHED_PROPERTY);

  private static final Set<KnownDecorator> GENERATOR_BASED_COROUTINE_DECORATORS = EnumSet.of(ASYNCIO_TASKS_COROUTINE,
                                                                                             ASYNCIO_COROUTINES_COROUTINE,
                                                                                             TYPES_COROUTINE);

  private static final Map<String, List<KnownDecorator>> BY_SHORT_NAME = StreamEx.of(values()).groupingBy(KnownDecorator::getShortName);

  /**
   * Map decorators of element to {@link PyKnownDecoratorUtil.KnownDecorator}.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @return list of known decorators in declaration order with duplicates (with any)
   */
  @NotNull
  public static List<KnownDecorator> getKnownDecorators(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    final PyDecoratorList decoratorList = element.getDecoratorList();
    if (decoratorList == null) {
      return Collections.emptyList();
    }

    return StreamEx
      .of(decoratorList.getDecorators())
      .flatMap(decorator -> asKnownDecorators(decorator, context).stream())
      .nonNull()
      .toList();
  }

  @NotNull
  public static List<KnownDecorator> asKnownDecorators(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    final QualifiedName qualifiedName = decorator.getQualifiedName();
    if (qualifiedName == null) {
      return Collections.emptyList();
    }

    if (context.maySwitchToAST(decorator)) {
      PyQualifiedNameOwner resolved = as(resolveDecorator(decorator), PyQualifiedNameOwner.class);
      if (resolved instanceof PyFunction && PyNames.INIT.equals(resolved.getName())) {
        resolved = ((PyFunction)resolved).getContainingClass();
      }

      if (resolved != null && resolved.getQualifiedName() != null) {
        final QualifiedName resolvedName = QualifiedName.fromDottedString(resolved.getQualifiedName());
        final List<KnownDecorator> knownDecorators = BY_SHORT_NAME.getOrDefault(resolvedName.getLastComponent(), Collections.emptyList());

        return ContainerUtil.filter(knownDecorators, knownDecorator -> resolvedName.equals(knownDecorator.getQualifiedName()));
      }
    }
    else {
      return BY_SHORT_NAME.getOrDefault(qualifiedName.getLastComponent(), Collections.emptyList());
    }

    return Collections.emptyList();
  }

  @Nullable
  private static PsiElement resolveDecorator(@NotNull PyDecorator decorator) {
    final PyExpression callee = decorator.getCallee();
    if (callee == null) {
      return null;
    }
    final PsiReference reference = callee.getReference();
    if (reference == null) {
      return null;
    }
    return reference.resolve();
  }

  /**
   * Check that given element has any non-standard (read "unreliable") decorators.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @see PyKnownDecoratorUtil.KnownDecorator
   */
  public static boolean hasUnknownDecorator(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    return !allDecoratorsAreKnown(element, getKnownDecorators(element, context));
  }

  /**
   * Checks that given element has any non-builtin decorators.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @see PyKnownDecoratorUtil.KnownDecorator
   */
  public static boolean hasNonBuiltinDecorator(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    final List<KnownDecorator> knownDecorators = getKnownDecorators(element, context);
    if (!allDecoratorsAreKnown(element, knownDecorators)) {
      return true;
    }
    knownDecorators.removeAll(BUILTIN_DECORATORS);
    return !knownDecorators.isEmpty();
  }

  /**
   * Checks that given function has any decorators from {@code abc} module.
   *
   * @param element Python function to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   *                of theirs qualified names.
   * @see PyKnownDecoratorUtil.KnownDecorator
   */
  public static boolean hasAbstractDecorator(@NotNull PyDecoratable element, @NotNull TypeEvalContext context) {
    final List<KnownDecorator> knownDecorators = getKnownDecorators(element, context);
    if (knownDecorators.isEmpty()) {
      return false;
    }
    knownDecorators.retainAll(ABSTRACT_DECORATORS);
    return !knownDecorators.isEmpty();
  }

  public static boolean isPropertyDecorator(@NotNull PyDecorator decorator, @NotNull TypeEvalContext context) {
    return ContainerUtil.exists(asKnownDecorators(decorator, context), PROPERTY_DECORATORS::contains);
  }

  public static boolean hasGeneratorBasedCoroutineDecorator(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return ContainerUtil.exists(getKnownDecorators(function, context), GENERATOR_BASED_COROUTINE_DECORATORS::contains);
  }

  public static boolean hasRedeclarationDecorator(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return getKnownDecorators(function, context).contains(TYPING_OVERLOAD);
  }

  private static boolean allDecoratorsAreKnown(@NotNull PyDecoratable element, @NotNull List<KnownDecorator> decorators) {
    final PyDecoratorList decoratorList = element.getDecoratorList();
    return decoratorList == null ? decorators.isEmpty() : decoratorList.getDecorators().length == decorators.size();
  }
}
