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
package com.jetbrains.python.nameResolver;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Some enum value that represents one or more fully qualified names for some function
 *
 * @author Ilya.Kazakevich
 */
public interface FQNamesProvider {
  /**
   * @return one or more fully qualified names
   */
  @NotNull
  String[] getNames();

  @NotNull
  default QualifiedName[] getQualifiedNames() {
    return Arrays.stream(getNames()).map(QualifiedName::fromDottedString).toArray(QualifiedName[]::new);
  }


  @NotNull
  default String getFirstName() {
    return getNames()[0];
  }

  default boolean isShortNameMatches(@NotNull final NavigationItem item) {
    final String name = item.getName();
    if (name == null) {
      return false;
    }
    return getShortNames().contains(name);
  }

  /**
   * @return all names in unqualified ("after last dot") format
   */
  @NotNull
  default List<String> getShortNames() {
    return Arrays.stream(getQualifiedNames()).map(QualifiedName::getLastComponent).filter(o -> o != null).collect(Collectors.toList());
  }

  /**
   * @return is name of class (true) or function (false)
   */
  boolean isClass();

  /**
   * @return if element should be checked by full name conformity by {@link #isNameMatches(PyQualifiedNameOwner)}
   * or only name and package should be checked
   * @see #isNameMatches(PyQualifiedNameOwner)
   */
  default boolean alwaysCheckQualifiedName() {
    return true;
  }

  /**
   * Checks if element name matches. {@link #alwaysCheckQualifiedName()} controls if full name should be checked, or only last and first
   * parts (name and package) are enough. It may be used for cases when physical FQN is not documented.
   */
  default boolean isNameMatches(@NotNull final PyQualifiedNameOwner qualifiedNameOwner) {
    final String qualifiedName = qualifiedNameOwner.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }

    // Only check qualified name
    if (alwaysCheckQualifiedName()) {
      return ArrayUtil.contains(qualifiedName, getNames());
    }

    // Relaxed check: package and name
    final QualifiedName elementQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    final Stream<QualifiedName> nameStream = Arrays.stream(getQualifiedNames());
    return nameStream.anyMatch((name) -> {
                                 final String first = name.getFirstComponent();
                                 final String last = name.getLastComponent();
                                 return first != null
                                        && last != null
                                        && first.equals(elementQualifiedName.getFirstComponent())
                                        && last.equals(elementQualifiedName.getLastComponent());
                               }
    );
  }
}
