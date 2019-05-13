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
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.intellij.navigation.NavigationItem;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;


/**
 * Finds elements by name
 *
 * @author Ilya.Kazakevich
 */
class NamePredicate extends NotNullPredicate<PyElement> {
  @NotNull
  private final String myName;


  NamePredicate(@NotNull final String name) {
    myName = name;
  }

  @Override
  protected boolean applyNotNull(@NotNull final PyElement input) {
    return myName.equals(input.getName());
  }

  /**
   * Checks if collection has {@link com.jetbrains.python.psi.PyElement} with name equals to name of provided element.
   * If element has no name -- returns false any way.
   * @param needle element to take name from
   * @param stock collection elements to search between
   * @return true if stock contains element with name equal to needle's name
   */
  static boolean hasElementWithSameName(@NotNull final NavigationItem needle, @NotNull final Iterable<? extends PyElement> stock) {
    final String name = needle.getName();
    if (name != null) {
      final Optional<? extends PyElement> optional = Iterables.tryFind(stock, new NamePredicate(name));
      return optional.isPresent();
    }
    return false;
  }
}
