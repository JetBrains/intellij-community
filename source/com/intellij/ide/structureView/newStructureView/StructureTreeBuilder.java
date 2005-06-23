package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.util.treeView.*;
import com.intellij.ide.util.treeView.smartTree.SmartTreeStructure;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.*;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.datatransfer.Transferable;

final class StructureTreeBuilder extends AbstractTreeBuilder {
  private final Project myProject;
  private final StructureViewModel myStructureModel;

  private final MyCopyPasteListener myCopyPasteListener;
  private final PsiTreeChangeListener myPsiTreeChangeListener;
  private final ModelListener myModelListener;

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Alarm myUpdateEditorAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private DocumentAdapter myDocumentsListener;

  public StructureTreeBuilder(Project project,
                              JTree tree,
                              DefaultTreeModel treeModel,
                              AbstractTreeStructure treeStructure,
                              StructureViewModel structureModel) {
    super(
      tree,
      treeModel,
      treeStructure, null
    );

    myProject = project;
    myStructureModel = structureModel;

    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    myModelListener = new ModelListener() {
      public void onModelChanged() {
        addRootToUpdate();
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);

    myCopyPasteListener = new MyCopyPasteListener();
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);
    initRootNode();
    myStructureModel.addModelListener(myModelListener);
    myDocumentsListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        myUpdateEditorAlarm.cancelAllRequests();
        myUpdateAlarm.cancelAllRequests();
        myUpdateEditorAlarm.addRequest(new Runnable() {
          public void run() {
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
          }
        }, 300, ModalityState.stateForComponent(myTree));
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentsListener);
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateEditorAlarm.cancelAllRequests();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
    myStructureModel.removeModelListener(myModelListener);
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentsListener);
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
        if(!myProject.isDisposed()) {
          super.updateSubtree(node);
        }
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
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      childrenChanged();
    }

    private void childrenChanged() {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm.addRequest(new Runnable() {
        public void run() {
          addRootToUpdate();
        }
      }, 300, ModalityState.stateForComponent(myTree));
    }

    public void propertyChanged(PsiTreeChangeEvent event) {
      childrenChanged();
    }
  }

  private void addRootToUpdate() {
    ((SmartTreeStructure)getTreeStructure()).rebuildTree();
    myUpdater.addSubtreeToUpdate(myRootNode);
  }


  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged(final Transferable oldTransferable, final Transferable newTransferable) {
      updateByTransferable(oldTransferable);
      updateByTransferable(newTransferable);
    }

    private void updateByTransferable(final Transferable t) {
      final PsiElement[] psiElements = CopyPasteUtil.getElementsInTransferable(t);
      for (PsiElement psiElement : psiElements) {
        myUpdater.addSubtreeToUpdateByElement(psiElement);
      }
    }
  }
}