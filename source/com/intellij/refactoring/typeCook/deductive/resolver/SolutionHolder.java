package com.intellij.refactoring.typeCook.deductive.resolver;

import java.util.LinkedList;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jan 18, 2005
 * Time: 6:54:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class SolutionHolder {
  private final LinkedList<Binding> mySolutions = new LinkedList<Binding>();

  public void putSolution(final Binding b1) {
    for (Iterator<Binding> i = mySolutions.iterator(); i.hasNext();) {
      final Binding b2 = i.next();

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

  public Binding[] getTopSolutions() {
    return mySolutions.toArray(new Binding[]{});
  }

  public Binding getBestSolution() {
    Binding best = null;
    int width = 0;

    for (final Iterator<Binding> b = mySolutions.iterator(); b.hasNext();) {
      final Binding binding = b.next();
      final int w = binding.getWidth();

      if (w > width && binding.isValid()) {
        width = w;
        best = binding;
      }
    }

    return best;
  }
}
