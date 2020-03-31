// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyKnownDecoratorUtil.KnownDecorator.*;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.PyUtil.turnConstructorIntoClass;

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

    TYPING_OVERLOAD("typing." + PyNames.OVERLOAD),
    TYPING_RUNTIME("typing.runtime"),
    TYPING_RUNTIME_EXT("typing_extensions.runtime"),
    TYPING_RUNTIME_CHECKABLE("typing.runtime_checkable"),
    TYPING_RUNTIME_CHECKABLE_EXT("typing_extensions.runtime_checkable"),
    TYPING_FINAL("typing.final"),
    TYPING_FINAL_EXT("typing_extensions.final"),

    REPRLIB_RECURSIVE_REPR("reprlib.recursive_repr"),

    PYRAMID_DECORATOR_REIFY("pyramid.decorator.reify"),
    DJANGO_UTILS_FUNCTIONAL_CACHED_PROPERTY("django.utils.functional.cached_property"),
    KOMBU_UTILS_CACHED_PROPERTY("kombu.utils.cached_property"),

    DATACLASSES_DATACLASS("dataclasses.dataclass"),
    ATTR_S("attr.s"),
    ATTR_ATTRS("attr.attrs"),
    ATTR_ATTRIBUTES("attr.attributes"),
    ATTR_DATACLASS("attr.dataclass"),

    PYTEST_FIXTURE("pytest.fixture");

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
      resolved = ObjectUtils.chooseNotNull(turnConstructorIntoClass(as(resolved, PyFunction.class)), resolved);

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
    final List<KnownDecorator> decorators = getKnownDecorators(decoratable, context);
    return !allDecoratorsAreKnown(decoratable, decorators) || decorators.contains(UNITTEST_MOCK_PATCH);
  }

  public static boolean hasUnknownOrChangingReturnTypeDecorator(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    final List<KnownDecorator> decorators = getKnownDecorators(decoratable, context);

    if (!allDecoratorsAreKnown(decoratable, decorators)) {
      return true;
    }

    return ContainerUtil.exists(decorators, d -> d == UNITTEST_MOCK_PATCH || d == CONTEXTLIB_CONTEXTMANAGER);
  }

  public static boolean hasUnknownOrUpdatingAttributesDecorator(@NotNull PyDecoratable decoratable, @NotNull TypeEvalContext context) {
    final List<KnownDecorator> decorators = getKnownDecorators(decoratable, context);

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

  private static boolean allDecoratorsAreKnown(@NotNull PyDecoratable element, @NotNull List<KnownDecorator> decorators) {
    final PyDecoratorList decoratorList = element.getDecoratorList();
    return decoratorList == null
           ? decorators.isEmpty()
           : decoratorList.getDecorators().length == StreamEx.of(decorators).groupingBy(KnownDecorator::getShortName).size();
  }
}
