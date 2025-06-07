// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;


public class PyUnionType implements PyType {

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
    return StringUtil.join(myMembers, (NullableFunction<PyType, String>)type -> type != null ? type.getName() : null, " | ");
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
      if (member instanceof PyUnionType) {
        newMembers.addAll(((PyUnionType)member).getMembers());
      }
      else {
        newMembers.add(member);
      }
    }
    return newMembers.size() < 2 ? ContainerUtil.getFirstItem(newMembers, defaultResult) : new PyUnionType(newMembers);
  }

  public static @Nullable PyType createWeakType(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    else if (type instanceof PyUnionType unionType) {
      if (unionType.isWeak()) {
        return unionType;
      }
    }
    return union(type, null);
  }

  public static @Nullable PyType toNonWeakType(@Nullable PyType type) {
    return type instanceof PyUnionType ? ((PyUnionType)type).excludeNull() : type;
  }

  public boolean isWeak() {
    return myMembers.contains(null);
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
    return !isWeak() ? this : union(ContainerUtil.skipNulls(getMembers()));
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
