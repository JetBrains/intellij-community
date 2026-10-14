// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The known decorators of a decoratable element, one {@link PyKnownDecorators} per decorator in declaration order.
 *
 * <p>The two axes stay apart: which decorator, and which known decorator each of them can resolve to. A predicate
 * holds for the element when it holds for any of its decorators, while {@link #allKnown()} asks about all of them.
 *
 * @see PyKnownDecoratorUtil#knownDecoratorsOf(PyDecoratable, com.jetbrains.python.psi.types.TypeEvalContext)
 */
@ApiStatus.NonExtendable
public final class PyElementKnownDecorators {
  public static final PyElementKnownDecorators NONE = new PyElementKnownDecorators(List.of());

  private final List<PyKnownDecorators> myDecorators;

  private PyElementKnownDecorators(@NotNull List<PyKnownDecorators> decorators) {
    myDecorators = decorators;
  }

  @ApiStatus.Internal
  public static @NotNull PyElementKnownDecorators of(@NotNull List<PyKnownDecorators> decorators) {
    return decorators.isEmpty() ? NONE : new PyElementKnownDecorators(List.copyOf(decorators));
  }

  /** Whether the element carries no decorator at all. */
  public boolean isEmpty() {
    return myDecorators.isEmpty();
  }

  /** Whether every decorator of the element resolves to a known decorator. True for an element without decorators. */
  public boolean allKnown() {
    return ContainerUtil.and(myDecorators, PyKnownDecorators::isKnown);
  }

  public boolean contains(@NotNull PyKnownDecorator known) {
    return ContainerUtil.exists(myDecorators, decorator -> decorator.contains(known));
  }

  public boolean containsAny(PyKnownDecorator @NotNull ... known) {
    return ContainerUtil.exists(myDecorators, decorator -> decorator.containsAny(known));
  }

  public boolean hasTrait(@NotNull Predicate<PyKnownDecorator> trait) {
    return ContainerUtil.exists(myDecorators, decorator -> decorator.hasTrait(trait));
  }

  /**
   * Returns the known decorator with [trait] that is nearest the decorated element, or null when none carries it.
   * The decorator nearest the element is applied first and the others wrap it, so it decides.
   */
  public @Nullable PyKnownDecorator findLastWithTrait(@NotNull Predicate<PyKnownDecorator> trait) {
    for (int i = myDecorators.size() - 1; i >= 0; i--) {
      PyKnownDecorator found = myDecorators.get(i).findWithTrait(trait);
      if (found != null) return found;
    }
    return null;
  }

  @ApiStatus.Internal
  public @NotNull List<PyKnownDecorator> asList() {
    return ContainerUtil.flatMap(myDecorators, PyKnownDecorators::asList);
  }
}
