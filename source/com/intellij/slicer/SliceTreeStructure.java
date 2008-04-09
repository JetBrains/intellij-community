package com.intellij.slicer;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceTreeStructure extends AbstractTreeStructureBase {
  private SliceNode myRoot;

  public SliceTreeStructure(Project project) {
    super(project);
  }

  public void setRoot(final SliceNode root) {
    myRoot = root;
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
