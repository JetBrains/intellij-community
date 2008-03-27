package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.util.Processor;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class SliceNode extends AbstractTreeNode<SliceUsage> {
  private List<SliceNode> myCachedChildren;
  private long storedModificationCount = -1;
  private boolean initialized;

  protected SliceNode(Project project, SliceUsage sliceUsage) {
    super(project, sliceUsage);
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
            SliceNode node = new SliceNode(myProject, sliceUsage);
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
      SliceUsage sliceUsage = getValue();
      sliceUsage.initializeDuplicateFlag();
      initialized = true;
    }
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
