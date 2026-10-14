// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The known decorators that one decorator can resolve to.
 *
 * <p>Several candidates mean an ambiguous resolve, not several decorators: without the AST the short name of a
 * decorator is all its file gives, and every registered decorator with that short name becomes a candidate.
 * A predicate therefore holds for the decorator when it holds for any one of its candidates.
 *
 * @see PyKnownDecoratorUtil#knownDecoratorsOf(PyDecorator, com.jetbrains.python.psi.types.TypeEvalContext)
 */
public final class PyKnownDecorators {
  public static final PyKnownDecorators NONE = new PyKnownDecorators(List.of());

  private final List<PyKnownDecorator> myCandidates;

  private PyKnownDecorators(@NotNull List<PyKnownDecorator> candidates) {
    myCandidates = candidates;
  }

  @ApiStatus.Internal
  public static @NotNull PyKnownDecorators of(@NotNull List<PyKnownDecorator> candidates) {
    return candidates.isEmpty() ? NONE : new PyKnownDecorators(List.copyOf(candidates));
  }

  /** Whether the decorator resolves to a known decorator at all. */
  public boolean isKnown() {
    return !myCandidates.isEmpty();
  }

  public boolean contains(@NotNull PyKnownDecorator known) {
    return myCandidates.contains(known);
  }

  public boolean containsAny(PyKnownDecorator @NotNull ... known) {
    for (PyKnownDecorator candidate : known) {
      if (myCandidates.contains(candidate)) return true;
    }
    return false;
  }

  public boolean hasTrait(@NotNull Predicate<PyKnownDecorator> trait) {
    return ContainerUtil.exists(myCandidates, trait::test);
  }

  /** Only {@link PyElementKnownDecorators#findLastWithTrait} needs a candidate rather than an answer about one. */
  @ApiStatus.Internal
  public @Nullable PyKnownDecorator findWithTrait(@NotNull Predicate<PyKnownDecorator> trait) {
    return ContainerUtil.find(myCandidates, trait::test);
  }

  @ApiStatus.Internal
  public @NotNull List<PyKnownDecorator> asList() {
    return myCandidates;
  }
}
