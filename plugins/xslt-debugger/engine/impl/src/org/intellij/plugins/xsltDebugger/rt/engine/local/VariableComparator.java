package org.intellij.plugins.xsltDebugger.rt.engine.local;

import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;

import java.util.Comparator;

public final class VariableComparator implements Comparator<Debugger.Variable> {
  public static final VariableComparator INSTANCE = new VariableComparator();

  private VariableComparator() {
  }

  public int compare(Debugger.Variable o1, Debugger.Variable o2) {
    final boolean og = o2.isGlobal();
    final boolean g = o1.isGlobal();
    if (og && !g) {
      return 1;
    } else if (!og && g) {
      return -1;
    } else {
      return o1.getName().compareTo(o2.getName());
    }
  }
}