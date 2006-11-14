package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.fragments.LineFragment;

import java.util.ArrayList;

public class DiffFragmentsProcessor {
  public ArrayList<LineFragment> process(DiffFragment[] fragments) {
    LineFragmentsCollector collector = new LineFragmentsCollector();
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      collector.addDiffFragment(fragment);
    }
    return collector.getFragments();
  }
}
