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

/**
 * @author yole
 */
public class PyUnionType implements PyType {

  @NotNull
  private final LinkedHashSet<@Nullable PyType> myMembers;

  PyUnionType(@NotNull LinkedHashSet<@Nullable PyType> members) {
    myMembers = new LinkedHashSet<>(members);
  }

  @Override
  @Nullable
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
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

  @Nullable
  public static PyType union(@Nullable PyType type1, @Nullable PyType type2) {
    return union(Arrays.asList(type1, type2));
  }

  @Nullable
  public static PyType union(@NotNull Collection<@Nullable PyType> members) {
    if (members.size() < 2) {
      return ContainerUtil.getFirstItem(members);
    }
    else {
      final LinkedHashSet<PyType> newMembers = new LinkedHashSet<>();
      for (PyType member : members) {
        if (member instanceof PyUnionType) {
          newMembers.addAll(((PyUnionType)member).getMembers());
        }
        else {
          newMembers.add(member);
        }
      }

      return newMembers.size() < 2 ? ContainerUtil.getFirstItem(newMembers) : new PyUnionType(newMembers);
    }
  }

  @Nullable
  public static PyType createWeakType(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    else if (type instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)type;
      if (unionType.isWeak()) {
        return unionType;
      }
    }
    return union(type, null);
  }

  @Nullable
  public static PyType toNonWeakType(@Nullable PyType type) {
    return type instanceof PyUnionType ? ((PyUnionType)type).excludeNull() : type;
  }

  public boolean isWeak() {
    return myMembers.contains(null);
  }

  /**
   * @see PyTypeUtil#toStream(PyType)
   * @see PyUnionType#map(Function)
   */
  @NotNull
  public Collection<PyType> getMembers() {
    return Collections.unmodifiableCollection(myMembers);
  }

  @Nullable
  public PyType map(@NotNull Function<@Nullable PyType, @Nullable PyType> mapper) {
    return union(ContainerUtil.map(getMembers(), t -> mapper.apply(t)));
  }

  /**
   * Excludes all subtypes of type from the union
   *
   * @param type    type to exclude. If type is a union all subtypes of union members will be excluded from the union
   *                If type is null only null will be excluded from the union.
   * @param context
   * @return union with excluded types
   */
  @Nullable
  public PyType exclude(@Nullable PyType type, @NotNull TypeEvalContext context) {
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
  @Nullable
  public PyType excludeNull() {
    return !isWeak() ? this : union(ContainerUtil.skipNulls(getMembers()));
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PyUnionType) {
      final PyUnionType otherType = (PyUnionType)other;
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
}
