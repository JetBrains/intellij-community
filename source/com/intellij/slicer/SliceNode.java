package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * @author cdr
 */
public class SliceNode extends AbstractTreeNode<SliceUsage> implements DuplicateNodeRenderer.DuplicatableNode<SliceNode>, MyColoredTreeCellRenderer {
  protected List<AbstractTreeNode> myCachedChildren;
  private volatile long storedModificationCount = -1;
  protected boolean initialized;
  private SliceNode duplicate;
  protected final Map<SliceUsage, List<SliceNode>> targetEqualUsages;
  protected final SliceTreeBuilder myTreeBuilder;
  private final Collection<PsiElement> leafExpressions = new THashSet<PsiElement>(SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY);
  protected boolean changed;

  protected SliceNode(@NotNull Project project, SliceUsage sliceUsage, @NotNull Map<SliceUsage, List<SliceNode>> targetEqualUsages,
                      SliceTreeBuilder treeBuilder, @NotNull Collection<PsiElement> leafExpressions) {
    super(project, sliceUsage);
    this.targetEqualUsages = targetEqualUsages;
    myTreeBuilder = treeBuilder;
    this.leafExpressions.addAll(leafExpressions);
  }

  SliceNode copy(Collection<PsiElement> withLeaves) {
    SliceUsage newUsage = getValue().copy();
    SliceNode newNode = new SliceNode(getProject(), newUsage, targetEqualUsages, myTreeBuilder, withLeaves);
    newNode.storedModificationCount = storedModificationCount;
    newNode.initialized = initialized;
    newNode.duplicate = duplicate;
    return newNode;
  }

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    final Collection[] nodes = new Collection[1];
    ProgressManager.getInstance().runProcess(new Runnable(){
      public void run() {
        nodes[0] = getChildrenUnderProgress(ProgressManager.getInstance().getProgressIndicator());
      }
    }, new ProgressIndicatorBase());
    return nodes[0];
  }

  public Collection<? extends AbstractTreeNode> getChildrenUnderProgress(ProgressIndicator progress) {
    long count = PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount();
    if (myCachedChildren != null && storedModificationCount == count || !isValid() || myTreeBuilder.splitByLeafExpressions) {
      return myCachedChildren == null ? Collections.<AbstractTreeNode>emptyList() : myCachedChildren;
    }
    myCachedChildren = Collections.synchronizedList(new ArrayList<AbstractTreeNode>());
    storedModificationCount = count;
    final SliceManager manager = SliceManager.getInstance(getProject());
    manager.runInterruptibly(new Runnable() {
      public void run() {
        getValue().processChildren(new Processor<SliceUsage>() {
          public boolean process(SliceUsage sliceUsage) {
            manager.checkCanceled();
            SliceNode node = new SliceNode(myProject, sliceUsage, targetEqualUsages, myTreeBuilder, getLeafExpressions());
            myCachedChildren.add(node);
            return true;
          }
        });
      }
    }, new Runnable(){
      public void run() {
        myCachedChildren = null;
        storedModificationCount = -1;
        changed = true;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(getValue());
            //myTreeBuilder.getUi().queueBackgroundUpdate(node, (NodeDescriptor)node.getUserObject(), new TreeUpdatePass(node));
            if (node == null) node = myTreeBuilder.getRootNode();
            myTreeBuilder.addSubtreeToUpdate(node);
          }
        });
      }
    }, progress);
    return myCachedChildren;
  }

  @NotNull
  @Override
  protected PresentationData createPresentation() {
    return new PresentationData(){
      @Override
      public Object[] getEqualityObjects() {
        return ArrayUtil.append(super.getEqualityObjects(), changed);
      }
    };
  }

  protected void update(PresentationData presentation) {
    if (!initialized) {
      initializeDuplicateFlag();
      initialized = true;
    }
    if (presentation != null) {
      presentation.setChanged(presentation.isChanged() || changed);
      changed = false;
      if (duplicate != null) {
        presentation.setTooltip("Duplicate node");
      }
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
      duplicate = eq.get(0);
    }
  }

  public boolean hasDuplicate() {
    return duplicate != null;
  }
  
  public SliceNode getDuplicate() {
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

  public void addLeafExpressions(@NotNull Collection<PsiElement> leafExpressions) {
    this.leafExpressions.addAll(leafExpressions);
  }

  @NotNull
  public Collection<PsiElement> getLeafExpressions() {
    return leafExpressions;
  }

  public void customizeCellRenderer(SliceUsageCellRenderer renderer, JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    renderer.setIcon(getPresentation().getIcon(expanded));
    if (isValid()) {
      SliceUsage sliceUsage = getValue();
      renderer.customizeCellRendererFor(sliceUsage);
      renderer.setToolTipText(sliceUsage.getPresentation().getTooltipText());
    }
    else {
      renderer.append(UsageViewBundle.message("node.invalid") + " ", SliceUsageCellRenderer.ourInvalidAttributes);
    }
  }

  public void setChanged() {
    //storedModificationCount = -1;
    //for (SliceNode cachedChild : myCachedChildren) {
    //  cachedChild.clearCaches();
    //}
    //myCachedChildren = null;
    //initialized = false;
    changed = true;
  }

  @Override
  public String toString() {
    return getValue()==null?"<null>":getValue().toString();
  }


}
