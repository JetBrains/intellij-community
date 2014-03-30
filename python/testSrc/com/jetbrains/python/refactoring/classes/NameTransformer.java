package com.jetbrains.python.refactoring.classes;

import com.google.common.base.Function;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Transforms {@link com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo} to its display names
 * @author Ilya.Kazakevich
 */
public class NameTransformer implements Function<PyMemberInfo<? extends PyElement>, String> {
  /**
   * To be used instead of creation
   */
  public static final NameTransformer INSTANCE = new NameTransformer();

  private NameTransformer() {
  }

  @Override
  public String apply(@NotNull final PyMemberInfo<? extends PyElement> input) {
    return input.getDisplayName();
  }
}
