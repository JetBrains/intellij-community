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
