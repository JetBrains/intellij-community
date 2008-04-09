package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceNode extends AbstractTreeNode<SliceUsage> implements DuplicateNodeRenderer.DuplicatableNode {
  private List<SliceNode> myCachedChildren;
  private long storedModificationCount = -1;
  private boolean initialized;
  private SliceNode duplicate;
  private final Map<SliceUsage, List<SliceNode>> targetEqualUsages;

  protected SliceNode(@NotNull Project project, @NotNull SliceUsage sliceUsage, @NotNull Map<SliceUsage, List<SliceNode>> targetEqualUsages) {
    super(project, sliceUsage);
    this.targetEqualUsages = targetEqualUsages;
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    long count = PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount();
    if (myCachedChildren == null || storedModificationCount != count) {
      myCachedChildren = new ArrayList<SliceNode>();
      storedModificationCount = count;
      if (isValid()) {
        getValue().processChildren(new Processor<SliceUsage>() {
          public boolean process(SliceUsage sliceUsage) {
            SliceNode node = new SliceNode(myProject, sliceUsage, targetEqualUsages);
            myCachedChildren.add(node);
            return true;
          }
        });
      }
    }
    return myCachedChildren;
  }

  protected void update(PresentationData presentation) {
    if (!initialized) {
      initializeDuplicateFlag();
      initialized = true;
    }
  }

  private void initializeDuplicateFlag() {
    SliceUsage sliceUsage = getValue();
    List<SliceNode> eq = targetEqualUsages.get(sliceUsage);
    if (eq == null) {
      eq = new SmartList<SliceNode>();
      targetEqualUsages.put(sliceUsage, eq);
    }
    eq.add(this);
    if (eq.size() != 1) {
      duplicate = eq.get(0);
    }
  }
  
  public AbstractTreeNode getDuplicate() {
    return duplicate;
  }

  public void navigate(boolean requestFocus) {
    SliceUsage sliceUsage = getValue();
    sliceUsage.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return getValue().canNavigate();
  }

  public boolean canNavigateToSource() {
    return getValue().canNavigateToSource();
  }

  public boolean isValid() {
    return getValue().isValid();
  }

  public boolean expandOnDoubleClick() {
    return false;
  }
}
