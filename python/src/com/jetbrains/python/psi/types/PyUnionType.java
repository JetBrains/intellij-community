/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyUnionType implements PyType {
  private final Set<PyType> myMembers;

  PyUnionType(Collection<PyType> members) {
    myMembers = new LinkedHashSet<>(members);
  }

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

  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    Set<Object> variants = new HashSet<>();
    for (PyType member : myMembers) {
      if (member != null) {
        Collections.addAll(variants, member.getCompletionVariants(completionPrefix, location, context));
      }
    }
    return variants.toArray(new Object[variants.size()]);
  }

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
    Set<PyType> members = new LinkedHashSet<>();
    if (type1 instanceof PyUnionType) {
      members.addAll(((PyUnionType)type1).myMembers);
    }
    else {
      members.add(type1);
    }
    if (type2 instanceof PyUnionType) {
      members.addAll(((PyUnionType)type2).myMembers);
    }
    else {
      members.add(type2);
    }
    if (members.size() == 1) {
      return members.iterator().next();
    }
    return new PyUnionType(members);
  }

  @Nullable
  public static PyType union(Collection<PyType> members) {
    final int n = members.size();
    if (n == 0) {
      return null;
    }
    else if (n == 1) {
      return members.iterator().next();
    }
    else {
      final Iterator<PyType> it = members.iterator();
      PyType res = unit(it.next());
      while (it.hasNext()) {
        res = union(res, it.next());
      }
      return res;
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

  public boolean isWeak() {
    for (PyType member : myMembers) {
      if (member == null) {
        return true;
      }
    }
    return false;
  }

  public Collection<PyType> getMembers() {
    return myMembers;
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
    final List<PyType> members = new ArrayList<>();
    for (PyType m : getMembers()) {
      if (type == null) {
        if (m != null) {
          members.add(m);
        }
      }
      else {
        if (!PyTypeChecker.match(type, m, context)) {
          members.add(m);
        }
      }
    }
    return union(members);
  }

  @Nullable
  public PyType excludeNull(@NotNull TypeEvalContext context) {
    return exclude(null, context);
  }

  private static PyType unit(@Nullable PyType type) {
    if (type instanceof PyUnionType) {
      Set<PyType> members = new LinkedHashSet<>();
      members.addAll(((PyUnionType)type).getMembers());
      return new PyUnionType(members);
    }
    else {
      return new PyUnionType(Collections.singletonList(type));
    }
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
