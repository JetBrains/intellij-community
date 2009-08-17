package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author cdr
 */
public class SliceRootNode extends SliceNode {
  protected SliceRootNode(@NotNull Project project, @NotNull SliceUsage rootUsage, @NotNull DuplicateMap targetEqualUsages,
                          SliceTreeBuilder treeBuilder,
                          AnalysisScope scope) {
    super(project, new SliceUsage(rootUsage.getElement().getContainingFile(), scope), targetEqualUsages, treeBuilder, Collections.<PsiElement>emptyList());

    switchToAllLeavesTogether(rootUsage);
  }

  void switchToAllLeavesTogether(SliceUsage rootUsage) {
    AbstractTreeNode node = new SliceNode(getProject(), rootUsage, targetEqualUsages, myTreeBuilder, getLeafExpressions());
    myCachedChildren = Collections.singletonList(node);
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    return myCachedChildren;
  }

  @Override
  public Collection<? extends AbstractTreeNode> getChildrenUnderProgress(ProgressIndicator progress) {
    return getChildren();
  }

  @Override
  protected boolean shouldUpdateData() {
    return super.shouldUpdateData();
  }

  @Override
  protected void update(PresentationData presentation) {
    if (presentation != null) {
      presentation.setChanged(presentation.isChanged() || changed);
      changed = false;
    }
  }

  @Override
  public void customizeCellRenderer(SliceUsageCellRenderer renderer,
                                    JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
  }

  public void restructureByLeaves(Collection<PsiElement> leafExpressions) {
    assert myCachedChildren.size() == 1;
    SliceNode root = (SliceNode)myCachedChildren.get(0);
    myCachedChildren = new ArrayList<AbstractTreeNode>(leafExpressions.size());
    for (PsiElement leaf : leafExpressions) {
      SliceLeafValueRootNode node = new SliceLeafValueRootNode(getProject(), leaf, root);
      myCachedChildren.add(node);
    }
  }
}