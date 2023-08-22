// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.patterns;

import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;


public class PyElementPattern<T extends PyElement, Self extends PyElementPattern<T, Self>> extends PsiElementPattern<T, Self> {
  public PyElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public PyElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  public static class Capture<T extends PyElement> extends PyElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}
