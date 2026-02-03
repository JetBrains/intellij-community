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
package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Compares elements by their dependencies.
 * If A depends on B, then A &lt; B
 */
public final class PyDependenciesComparator implements Comparator<PyElement>, Serializable {
  private static final Key<UUID> UUID_KEY = new Key<>("pyUUIDKey");
  private static final Key<Set<UUID>> DEPENDENCIES_KEY = new Key<>("pyUUIDDependencies");
  /**
   * Singleton comparator instance
   */
  public static final PyDependenciesComparator INSTANCE = new PyDependenciesComparator();

  private PyDependenciesComparator() {
  }

  private static @NotNull <T> T getObject(@NotNull Key<T> key, @NotNull PyElement element, @NotNull Supplier<T> create) {
    var value = element.getCopyableUserData(key);
    if (value == null) {
      value = create.get();
      element.putCopyableUserData(key, value);
    }
    return value;
  }

  /**
   * Same as {@link #compare(PyElement, PyElement)} but stores dependency info in {@link PsiElement#getCopyableUserData(Key)}
   * so dependency information is stored until {@link #clearDependencyInfo(Iterable)} is called
   */
  public int compareAndStoreDependency(final @NotNull PyElement o1, final @NotNull PyElement o2) {
    var o1UUID = getObject(UUID_KEY, o1, () -> UUID.randomUUID());
    var o2UUID = getObject(UUID_KEY, o2, () -> UUID.randomUUID());

    int result = compare(o1, o2);
    if (result > 0) {
      //o1 depends on 02
      getObject(DEPENDENCIES_KEY, o1, () -> new HashSet<>()).add(o2UUID);
    }
    else if (result < 0) {
      // o2 depends on 01
      getObject(DEPENDENCIES_KEY, o2, () -> new HashSet<>()).add(o1UUID);
    }
    return result;
  }

  /**
   * Remove dependency info created by {@link #compareAndStoreDependency(PyElement, PyElement)}
   */
  public static void clearDependencyInfo(@NotNull Iterable<PyElement> elements) {
    for (var element : elements) {
      element.putCopyableUserData(UUID_KEY, null);
      element.putCopyableUserData(DEPENDENCIES_KEY, null);
    }
  }

  @Override
  public int compare(final @NotNull PyElement o1, final @NotNull PyElement o2) {
    if (depends(o1, o2)) {
      return 1;
    }
    if (depends(o2, o1)) {
      return -1;
    }
    return getBlockType(o1).compareTo(getBlockType(o2));
  }

  private static @NotNull BlockType getBlockType(final @NotNull PyElement statement) {
    for (BlockType type : BlockType.values()) {
      if (type.myClass.isAssignableFrom(statement.getClass())) {
        return type;
      }
    }

    return BlockType.OTHER;
  }

  /**
   * @return true if first param depends on second.
   */
  public static boolean depends(final @NotNull PyElement o1, final @NotNull PyElement o2) {
    var uuid2 = o2.getCopyableUserData(UUID_KEY);
    if (uuid2 != null) {
      var dependencies = o1.getCopyableUserData(DEPENDENCIES_KEY);
      if (dependencies != null) {
        return dependencies.contains(uuid2);
      }
    }

    final DependencyVisitor visitor = new DependencyVisitor(o2);
    o1.accept(visitor);
    return visitor.isDependencyFound();
  }

  /**
   * Copies dependency info from one element to another if {@link PsiElement#copy()} can't be used
   */
  public static void copyDependencyInfo(@NotNull PyElement from, @NotNull PyFunction to) {
    to.putCopyableUserData(UUID_KEY, from.getCopyableUserData(UUID_KEY));
    to.putCopyableUserData(DEPENDENCIES_KEY, from.getCopyableUserData(DEPENDENCIES_KEY));
  }

  /**
   * Types of class members in order, they should appear
   */
  private enum BlockType {
    DOC(PyExpressionStatement.class),
    DECLARATION(PyAssignmentStatement.class),
    METHOD(PyFunction.class),
    OTHER(PyElement.class);

    private final @NotNull Class<? extends PyElement> myClass;

    BlockType(final @NotNull Class<? extends PyElement> aClass) {
      myClass = aClass;
    }
  }
}
