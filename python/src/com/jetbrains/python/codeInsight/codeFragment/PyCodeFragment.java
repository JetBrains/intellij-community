package com.jetbrains.python.codeInsight.codeFragment;

import com.intellij.codeInsight.codeFragment.CodeFragment;

import java.util.Set;

/**
 * @author vlan
 */
public class PyCodeFragment extends CodeFragment {
  private final Set<String> myGlobalWrites;
  private final Set<String> myNonlocalWrites;

  public PyCodeFragment(final Set<String> input,
                        final Set<String> output,
                        final Set<String> globalWrites,
                        final Set<String> nonlocalWrites,
                        final boolean returnInside) {
    super(input, output, returnInside);
    myGlobalWrites = globalWrites;
    myNonlocalWrites = nonlocalWrites;
  }

  public Set<String> getGlobalWrites() {
    return myGlobalWrites;
  }

  public Set<String> getNonlocalWrites() {
    return myNonlocalWrites;
  }
}
