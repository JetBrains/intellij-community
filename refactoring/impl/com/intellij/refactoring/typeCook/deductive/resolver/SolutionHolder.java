package com.intellij.refactoring.typeCook.deductive.resolver;

import java.util.LinkedList;

/**
 * @author db
 */
public class SolutionHolder {
  private final LinkedList<Binding> mySolutions = new LinkedList<Binding>();

  public void putSolution(final Binding b1) {
    for (final Binding b2 : mySolutions) {
      switch (b1.compare(b2)) {
        case Binding.WORSE:
        case Binding.SAME:
          return;

        case Binding.BETTER:
          mySolutions.remove(b2);
          mySolutions.addFirst(b1);
          return;

        case Binding.NONCOMPARABLE:
          continue;
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
