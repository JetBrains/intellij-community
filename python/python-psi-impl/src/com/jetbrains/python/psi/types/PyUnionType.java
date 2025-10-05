// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;


public class PyUnionType implements PyType {

  @ApiStatus.Internal
  public static boolean isStrictSemanticsEnabled() {
    return Registry.is("python.typing.strict.unions", true);
  }

  private final @NotNull LinkedHashSet<@Nullable PyType> myMembers;

  PyUnionType(@NotNull LinkedHashSet<@Nullable PyType> members) {
    myMembers = new LinkedHashSet<>(members);
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext) {
    SmartList<RatedResolveResult> ret = new SmartList<>();
    boolean allNulls = true;
    for (PyType member : myMembers) {
      if (member != null) {
        List<? extends RatedResolveResult> result = member.resolveMember(name, location, direction, resolveContext);
        if (result != null) {
          allNulls = false;
          ret.addAll(result);
        }
      }
    }
    return allNulls ? null : ret;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    Set<Object> variants = new HashSet<>();
    for (PyType member : myMembers) {
      if (member != null) {
        Collections.addAll(variants, member.getCompletionVariants(completionPrefix, location, context));
      }
    }
    return ArrayUtil.toObjectArray(variants);
  }

  @Override
  public String getName() {
    return StringUtil.join(myMembers, t -> t == null ? "Any" : t.getName(), " | ");
  }

  /**
   * @return true if all types in the union are built-in.
   */
  @Override
  public boolean isBuiltin() {
    for (PyType one : myMembers) {
      if (one == null || !one.isBuiltin()) return false;
    }
    return true;
  }

  @Override
  public void assertValid(String message) {
    for (PyType member : myMembers) {
      if (member != null) {
        member.assertValid(message);
      }
    }
  }

  public static @Nullable PyType union(@Nullable PyType type1, @Nullable PyType type2) {
    return union(Arrays.asList(type1, type2));
  }

  /**
   * Constructs a union of the given types.
   * <p>
   * If the resulting union would be empty, returns {@code null} (representing Any type).
   * Consider using {@link #unionOrNever} instead, which falls back to {@link PyNeverType#NEVER}.
   *
   * @param members a collection of types to union
   * @return a PyType representing the union, or null if no valid members
   */
  public static @Nullable PyType union(@NotNull Collection<@Nullable PyType> members) {
    return unionOrDefault(members, null);
  }

  /**
   * Constructs a union of the given types, falling back to {@link PyNeverType#NEVER} instead of null (Any).
   *
   * @param members a collection of types to union
   * @return a PyType representing the union, or {@link PyNeverType#NEVER} if no valid members
   */
  public static @Nullable PyType unionOrNever(@NotNull Collection<@Nullable PyType> members) {
    return unionOrDefault(members, PyNeverType.NEVER);
  }

  private static @Nullable PyType unionOrDefault(@NotNull Collection<@Nullable PyType> members, @Nullable PyType defaultResult) {
    final LinkedHashSet<PyType> newMembers = new LinkedHashSet<>();
    for (PyType member : members) {
      if (member instanceof PyNeverType) {
        defaultResult = PyNeverType.NEVER;
        continue;
      }
      if (member instanceof PyUnionType unionType) {
        newMembers.addAll(unionType.getMembers());
      }
      else {
        newMembers.add(member);
      }
    }
    return newMembers.size() < 2 ? ContainerUtil.getFirstItem(newMembers, defaultResult) : new PyUnionType(newMembers);
  }

  /**
   * A "weak" type is an unsafe union of some type with {@code Any}.
   * <p>
   * Such a type can be passed anywhere where any other type is expected, not triggering type errors, but it still provides
   * completion and navigation of the original type. It allows keeping the gradual guarantee in cases where we are not able
   * to infer the exact type and still provide IDE assistance, such as code completion, navigation and documentation.
   *
   * @param type a type to "weaken"
   * @return a weak type for the type with the described behavior
   * @see PyUnsafeUnionType
   */
  public static @Nullable PyType createWeakType(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    else if (type instanceof PyUnionType unionType) {
      if (unionType.isWeak()) {
        return unionType;
      }
    }
    if (isStrictSemanticsEnabled()) {
      return PyUnsafeUnionType.unsafeUnion(type, null);
    }
    return union(type, null);
  }

  /**
   * Unwrap a "weak" type to its original type. Otherwise, return the {@code type} unchanged.
   *
   * @param type a potentially "weak" type to unwrap
   * @return the original material type combined with {@code Any} if {@code type} is a "weak" type, or {@code type} itself otherwise
   * @see #createWeakType(PyType)
   */
  public static @Nullable PyType toNonWeakType(@Nullable PyType type) {
    if (isStrictSemanticsEnabled()) {
      if (type instanceof PyUnsafeUnionType unsafeUnionType) {
        return PyUnsafeUnionType.unsafeUnion(ContainerUtil.skipNulls(unsafeUnionType.getMembers()));
      }
    }
    else if (type instanceof PyUnionType unionType) {
      return unionType.excludeNull();
    }
    return type;
  }

  /**
   * @see #isStrictSemanticsEnabled()
   * @deprecated When the strict union semantics of union types is enabled, a regular union type cannot be "weak",
   * (e.g. {@code int | Any} is not considered compatible with {@code str}), only {@link PyUnsafeUnionType} can exhibit this behavior.
   * Use {@link PyTypeChecker#isUnknown(PyType, boolean, TypeEvalContext)} instead.
   */
  @Deprecated
  public boolean isWeak() {
    return !isStrictSemanticsEnabled() && myMembers.contains(null);
  }

  /**
   * @see PyTypeUtil#toStream(PyType)
   * @see PyUnionType#map(Function)
   */
  public @NotNull Collection<@Nullable PyType> getMembers() {
    return Collections.unmodifiableCollection(myMembers);
  }

  public @Nullable PyType map(@NotNull Function<@Nullable PyType, @Nullable PyType> mapper) {
    return union(ContainerUtil.map(getMembers(), t -> mapper.apply(t)));
  }

  /**
   * Excludes all subtypes of type from the union
   *
   * @param type type to exclude. If type is a union all subtypes of union members will be excluded from the union
   *             If type is null only null will be excluded from the union.
   * @return union with excluded types
   */
  public @Nullable PyType exclude(@Nullable PyType type, @NotNull TypeEvalContext context) {
    if (type == null) return excludeNull();

    final List<PyType> members = new ArrayList<>();
    for (PyType m : getMembers()) {
      if (!PyTypeChecker.match(type, m, context)) {
        members.add(m);
      }
    }
    return union(members);
  }

  /**
   * Returns {@code this} if the current type {@code isWeak()}, excludes {@code null} otherwise.
   *
   * @see PyUnionType#toNonWeakType(PyType)
   */
  public @Nullable PyType excludeNull() {
    if (!isStrictSemanticsEnabled()) {
      return !isWeak() ? this : union(ContainerUtil.skipNulls(getMembers()));
    }
    return union(ContainerUtil.skipNulls(getMembers()));
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PyUnionType otherType) {
      return myMembers.equals(otherType.myMembers);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myMembers.hashCode();
  }

  @Override
  public String toString() {
    return "PyUnionType: " + getName();
  }


  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
      return visitorExt.visitPyUnionType(this);
    }
    return visitor.visitPyType(this);
  }
}
