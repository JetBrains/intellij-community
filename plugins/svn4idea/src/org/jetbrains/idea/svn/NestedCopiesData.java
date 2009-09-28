package org.jetbrains.idea.svn;

import com.intellij.util.Consumer;

import java.util.HashSet;
import java.util.Set;

public class NestedCopiesData implements Consumer<Set<NestedCopiesBuilder.MyPointInfo>> {
  // we can keep the type here also, but 
  private final Set<NestedCopiesBuilder.MyPointInfo> mySet;

  public NestedCopiesData() {
    mySet = new HashSet<NestedCopiesBuilder.MyPointInfo>();
  }

  public void consume(final Set<NestedCopiesBuilder.MyPointInfo> nestedCopyTypeSet) {
    mySet.addAll(nestedCopyTypeSet);
  }

  public Set<NestedCopiesBuilder.MyPointInfo> getSet() {
    return mySet;
  }
}
