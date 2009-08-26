package com.intellij.slicer;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;

/**
 * @author cdr
 */
public class SliceTreeBuilder extends AbstractTreeBuilder {
  public boolean splitByLeafExpressions;
  public final boolean dataFlowToThis;
  private final DuplicateMap myDuplicateMap;

  public SliceTreeBuilder(JTree tree, Project project, boolean dataFlowToThis, final SliceNode rootNode, DuplicateMap duplicateMap) {
    super(tree, (DefaultTreeModel)tree.getModel(), new SliceTreeStructure(project, rootNode), AlphaComparator.INSTANCE, false);
    this.dataFlowToThis = dataFlowToThis;
    myDuplicateMap = duplicateMap;
    initRootNode();
    //MessageBusConnection connection = project.getMessageBus().connect(this);
    //connection.subscribe(ProjectTopics.MODIFICATION_TRACKER,new PsiModificationTracker.Listener() {
    //  public void modificationCountChanged() {
    //    refreshAll();
    //  }
    //});
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return false;
  }

  public void switchToSplittedNodes() {
    final SliceRootNode root = (SliceRootNode)getRootNode().getUserObject();

    final Ref<Collection<PsiElement>> leafExpressions = Ref.create(null);
    boolean b = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        Collection<PsiElement> l = SliceLeafAnalyzer.calcLeafExpressions(root, ProgressManager.getInstance().getProgressIndicator());
        leafExpressions.set(l);
      }
    }, "Expanding all nodes... (may very well take the whole day)", true, root.getProject());
    if (!b) return;

    Collection<PsiElement> leaves = leafExpressions.get();
    if (leaves.isEmpty()) {
      Messages.showErrorDialog("Unable to find leaf expressions to group by", "Cannot group");
      return;
    }

    root.setChanged();
    root.restructureByLeaves(leaves);
    root.setChanged();
    splitByLeafExpressions = true;
    root.targetEqualUsages.clear();

    getUpdater().cancelAllRequests();
    getUpdater().addSubtreeToUpdateByElement(root);
  }

  public void switchToUnsplittedNodes() {
    SliceRootNode root = (SliceRootNode)getRootNode().getUserObject();
    SliceLeafValueRootNode valueNode = (SliceLeafValueRootNode)root.myCachedChildren.get(0);
    SliceNode rootNode = valueNode.myCachedChildren.get(0);

    root.switchToAllLeavesTogether(rootNode.getValue());
    root.setChanged();
    splitByLeafExpressions = false;
    root.targetEqualUsages.clear();

    getUpdater().cancelAllRequests();
    getUpdater().addSubtreeToUpdateByElement(root);
  }
}
