package com.jetbrains.python.refactoring.introduce;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:20:13 PM
 */
public interface IntroduceValidator {
  boolean isNameValid(@NotNull final PyIntroduceSettings settings);

  @Nullable
  String check(@NotNull final PyIntroduceSettings settings);
}
