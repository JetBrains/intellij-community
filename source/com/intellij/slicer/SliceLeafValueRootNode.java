package com.intellij.slicer;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.impl.NullUsage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceLeafValueRootNode extends AbstractTreeNode<Usage> implements Navigatable, MyColoredTreeCellRenderer {
  final List<SliceNode> myCachedChildren;

  protected SliceLeafValueRootNode(@NotNull Project project, PsiElement leafExpression, SliceNode root) {
    super(project, leafExpression == PsiUtilBase.NULL_PSI_ELEMENT ? NullUsage.INSTANCE : new UsageInfo2UsageAdapter(new UsageInfo(leafExpression)));

    SliceNode node = root.copy(ContainerUtil.singleton(leafExpression, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY));
    myCachedChildren = Collections.singletonList(node);
    restructureChildrenByLeaf(node, root, leafExpression);
  }

  private static void restructureChildrenByLeaf(SliceNode node, SliceNode oldRoot, @Nullable PsiElement leafExpression) {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    assert oldRoot.getLeafExpressions().contains(leafExpression);
    for (AbstractTreeNode cachedChild : oldRoot.myCachedChildren) {
      SliceNode cachedSliceNode = (SliceNode)cachedChild;
      if (cachedSliceNode.getDuplicate() != null) {
        // put entire (potentially unbounded) subtree here
        children.add(cachedSliceNode);
      }
      else if (cachedSliceNode.getLeafExpressions().contains(leafExpression)) {
        SliceNode newNode = cachedSliceNode.copy(ContainerUtil.singleton(leafExpression, SliceLeafAnalyzer.LEAF_ELEMENT_EQUALITY));
        children.add(newNode);

        restructureChildrenByLeaf(newNode, cachedSliceNode, leafExpression);
      }
    }
    node.myCachedChildren = children;
  }

  @NotNull
  public Collection<SliceNode> getChildren() {
    return myCachedChildren;
  }

  @Override
  protected void update(PresentationData presentation) {
  }

  @Override
  public String toString() {
    Usage myLeafExpression = getValue();
    String text;
    if (myLeafExpression instanceof UsageInfo2UsageAdapter) {
      PsiElement element = ((UsageInfo2UsageAdapter)myLeafExpression).getUsageInfo().getElement();
      text = element == null ? "" : element.getText();
    }
    else {
      text = "Other";
    }
    return "Value: "+ text;
  }

  public boolean isValid() {
    return getValue().isValid();
  }

  public void customizeCellRenderer(SliceUsageCellRenderer renderer,
                                    JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    Usage usage = getValue();
    renderer.append("Value: ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (usage instanceof UsageInfo2UsageAdapter) {
      PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
      if (element == null) {
        renderer.append("Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else {
        renderer.append(element.getText(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }
    else {
      renderer.append("Other", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return getValue().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getValue().canNavigateToSource();
  }
}