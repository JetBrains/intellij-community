package com.intellij.slicer;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.Collections;

/**
 * @author cdr
 */
public class SliceTreeStructure extends AbstractTreeStructureBase {
  private final SliceUsage mySliceUsage;
  private final SliceNode myRoot;

  public SliceTreeStructure(Project project, SliceUsage sliceUsage) {
    super(project);
    mySliceUsage = sliceUsage;
    myRoot = new SliceNode(project, mySliceUsage);
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
