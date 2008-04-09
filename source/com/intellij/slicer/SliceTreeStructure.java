package com.intellij.slicer;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceTreeStructure extends AbstractTreeStructureBase {
  private final SliceNode myRoot;
  private final Map<SliceUsage, List<SliceNode>> targetEqualUsages =
      new THashMap<SliceUsage, List<SliceNode>>(new TObjectHashingStrategy<SliceUsage>() {
        public int computeHashCode(SliceUsage object) {
          return object.getUsageInfo().hashCode();
        }
        public boolean equals(SliceUsage o1, SliceUsage o2) {
          return o1.getUsageInfo().equals(o2.getUsageInfo());
        }
      });

  public SliceTreeStructure(Project project, SliceUsage root) {
    super(project);
    myRoot = new SliceNode(project, root, targetEqualUsages);
  }

  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  public Object getRootElement() {
    return myRoot;
  }

  public void commit() {

  }

  public boolean hasSomethingToCommit() {
    return false;
  }
}
