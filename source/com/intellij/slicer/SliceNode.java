package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.util.SmartList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.*;
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
  private DefaultMutableTreeNode duplicate;
  private final Map<SliceUsage, List<SliceNode>> targetEqualUsages;
  private final AbstractTreeBuilder myTreeBuilder;

  protected SliceNode(@NotNull Project project, @NotNull SliceUsage sliceUsage, @NotNull Map<SliceUsage, List<SliceNode>> targetEqualUsages, AbstractTreeBuilder treeBuilder) {
    super(project, sliceUsage);
    this.targetEqualUsages = targetEqualUsages;
    myTreeBuilder = treeBuilder;
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    long count = PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount();
    if (myCachedChildren == null || storedModificationCount != count) {
      myCachedChildren = new ArrayList<SliceNode>();
      storedModificationCount = count;
      if (isValid()) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          public void run() {
            getValue().processChildren(new Processor<SliceUsage>() {
              public boolean process(SliceUsage sliceUsage) {
                SliceNode node = new SliceNode(myProject, sliceUsage, targetEqualUsages, myTreeBuilder);
                myCachedChildren.add(node);
                return true;
              }
            });
          }
        }, "Looking for Usages", false, getProject());
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
    if (eq.size() > 1) {
      final SliceNode dup = eq.get(0);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!myTreeBuilder.isDisposed()) {
            duplicate = myTreeBuilder.getNodeForElement(dup);
          }
        }
      });
    }
  }
  
  public DefaultMutableTreeNode getDuplicate() {
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
