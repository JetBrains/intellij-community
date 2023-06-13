// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeCook.deductive.resolver;

import java.util.LinkedList;

public class SolutionHolder {
  private final LinkedList<Binding> mySolutions = new LinkedList<>();

  public void putSolution(final Binding b1) {
    for (final Binding b2 : mySolutions) {
      switch (b1.compare(b2)) {
        case Binding.WORSE, Binding.SAME -> {
          return;
        }
        case Binding.BETTER -> {
          mySolutions.remove(b2);
          mySolutions.addFirst(b1);
          return;
        }
        case Binding.NONCOMPARABLE -> {
        }
      }
    }

    mySolutions.addFirst(b1);
  }

  public Binding getBestSolution() {
    Binding best = null;
    int width = 0;

    for (final Binding binding : mySolutions) {
      final int w = binding.getWidth();

      if (w > width && binding.isValid()) {
        width = w;
        best = binding;
      }
    }

    return best;
  }
}
