package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
* @author cdr
*/ // rehash map on each PSI modification since SmartPsiPointer's hashCode() and equals() are changed
class DuplicateMap extends THashMap<SliceUsage, Collection<SliceNode>> {
  private long timeStamp = -1;
  private static final TObjectHashingStrategy<SliceUsage> USAGEINFO_EQUALITY = new TObjectHashingStrategy<SliceUsage>() {
    public int computeHashCode(SliceUsage object) {
      return object.getUsageInfo().hashCode();
    }

    public boolean equals(SliceUsage o1, SliceUsage o2) {
      return o1.getUsageInfo().equals(o2.getUsageInfo());
    }
  };

  DuplicateMap() {
    super(USAGEINFO_EQUALITY);
  }

  @Override
  public Collection<SliceNode> put(SliceUsage key, Collection<SliceNode> value) {
    long count = key.getElement().getManager().getModificationTracker().getModificationCount();
    if (count != timeStamp) {
      Map<SliceUsage, List<SliceNode>> map = new THashMap<SliceUsage, List<SliceNode>>(_hashingStrategy);
      for (Map.Entry<SliceUsage, List<SliceNode>> entry : map.entrySet()) {
        SliceUsage usage = entry.getKey();
        PsiElement element = usage.getElement();
        if (!element.isValid()) continue;
        List<SliceNode> list = entry.getValue();
        map.put(usage, list);
      }
      clear();
      putAll(map);
      timeStamp = count;
    }
    return super.put(key, value);
  }
}
