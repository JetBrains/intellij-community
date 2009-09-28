package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Factory;

import java.util.Set;

public class NestedCopiesSink extends FragmentsMerger<Set<NestedCopiesBuilder.MyPointInfo>, NestedCopiesData> {
  public NestedCopiesSink() {
    super(new Factory<NestedCopiesData>() {
      public NestedCopiesData create() {
        return new NestedCopiesData();
      }
    });
  }
}
