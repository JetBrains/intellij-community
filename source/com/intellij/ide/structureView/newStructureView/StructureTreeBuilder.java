package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

final class StructureTreeBuilder extends AbstractTreeBuilder {
  private final Project myProject;

  private final MyCopyPasteListener myCopyPasteListener;
  private final PsiTreeChangeListener myPsiTreeChangeListener;
  private final StructureViewComponent myStructureViewComponent;
  private boolean myStateIsSaved = false;

  public StructureTreeBuilder(Project project,
                            JTree tree,
                            DefaultTreeModel treeModel,
                            AbstractTreeStructure treeStructure,
                            final StructureViewComponent structureViewComponent) {
    super(
      tree,
      treeModel,
      treeStructure, null
    );

    myStructureViewComponent = structureViewComponent;
    myProject = project;

    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);

    myCopyPasteListener = new MyCopyPasteListener();
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);
    initRootNode();

    myUpdater.runAfterUpdate(new Runnable() {
      public void run() {
        if (myStateIsSaved) {
          try {
            myStructureViewComponent.restoreStructureViewState();
          }
          finally {
            myStateIsSaved = false;
          }
        }
      }
    });
  }

  public void dispose() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
    super.dispose();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isSmartExpand() {
    return false;
  }

  protected final AbstractTreeUpdater createUpdater(){
    return new AbstractTreeUpdater(this) {
      protected void updateSubtree(DefaultMutableTreeNode node) {
        //((CachingChildrenTreeNode)node.getUserObject()).rebuildChildren();
        super.updateSubtree(node);
      }
    };
  }
  
  
  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childRemoved(PsiTreeChangeEvent event) {
      PsiElement child = event.getOldChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    public void childAdded(PsiTreeChangeEvent event) {
      PsiElement child = event.getNewChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    public void childReplaced(PsiTreeChangeEvent event) {
      PsiElement oldChild = event.getOldChild();
      PsiElement newChild = event.getNewChild();
      if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
      if (oldChild instanceof PsiCodeBlock && newChild instanceof PsiCodeBlock) return; //optimization
      childrenChanged();
    }

    public void childMoved(PsiTreeChangeEvent event) {
      childrenChanged();
      childrenChanged();
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      childrenChanged();
    }

    private void childrenChanged() {
      if (!myStateIsSaved) {
        try {
          myStructureViewComponent.saveStructureViewState();
        }
        finally {
          myStateIsSaved = true;
        }
      }
      ((SmartTreeStructure)getTreeStructure()).rebuildTree();
      myUpdater.addSubtreeToUpdate(myRootNode);
      return;
    }

    public void propertyChanged(PsiTreeChangeEvent event) {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged() {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }
  }
}