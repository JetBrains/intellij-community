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

import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Compares elements by their dependencies.
 * If A depends on B, then A &lt; B
 */
public class PyDependenciesComparator implements Comparator<PyElement>, Serializable {

  /**
   * Singleton comparator instance
   */
  public static final PyDependenciesComparator INSTANCE = new PyDependenciesComparator();

  private PyDependenciesComparator() {
  }

  @Override
  public int compare(@NotNull final PyElement o1, @NotNull final PyElement o2) {
    if (depends(o1, o2)) {
      return 1;
    }
    if (depends(o2, o1)) {
      return -1;
    }
    return getBlockType(o1).compareTo(getBlockType(o2));
  }

  @NotNull
  private static BlockType getBlockType(@NotNull final PyElement statement) {
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
  public static boolean depends(@NotNull final PyElement o1, @NotNull final PyElement o2) {
    final DependencyVisitor visitor = new DependencyVisitor(o2);
    o1.accept(visitor);
    return visitor.isDependencyFound();
  }

  /**
   * Types of class members in order, they should appear
   */
  private enum BlockType {
    DOC(PyExpressionStatement.class),
    DECLARATION(PyAssignmentStatement.class),
    METHOD(PyFunction.class),
    OTHER(PyElement.class);

    @NotNull
    private final Class<? extends PyElement> myClass;

    BlockType(@NotNull final Class<? extends PyElement> aClass) {
      myClass = aClass;
    }
  }
}
