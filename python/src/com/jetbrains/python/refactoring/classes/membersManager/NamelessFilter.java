package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.psi.PsiNamedElement;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

/**
 * Filters out named elements (ones that subclasses {@link com.intellij.psi.PsiNamedElement}) and {@link com.jetbrains.python.psi.PyElement})
 * that are null or has null name.
 * You need it sometimes when code has errors (i.e. bad formatted code with annotation may treat annotation as method with null name.
 *
* @author Ilya.Kazakevich
*/
class NamelessFilter<T extends PyElement & PsiNamedElement> extends NotNullPredicate<T> {

  @Override
  public boolean applyNotNull(@NotNull final T input) {
    return input.getName() != null;
  }
}
