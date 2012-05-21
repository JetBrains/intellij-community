package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CodeFragment;

import java.util.Set;

/**
 * @author vlan
 */
public class PyCodeFragment extends CodeFragment {
  private final Set<String> myGlobals;

  public PyCodeFragment(final Set<String> input, final Set<String> output, final Set<String> globals, final boolean returnInside) {
    super(input, output, returnInside);
    myGlobals = globals;
  }

  public Set<String> getGlobals() {
    return myGlobals;
  }
}
