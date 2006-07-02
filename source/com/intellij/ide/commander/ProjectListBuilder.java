package com.intellij.ide.commander;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Alarm;

import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProjectListBuilder extends AbstractListBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.commander.ProjectListBuilder");

  private final MyPsiTreeChangeListener myPsiTreeChangeListener;
  private final MyFileStatusListener myFileStatusListener;
  private final CopyPasteManager.ContentChangedListener myCopyPasteListener;
  private final Alarm myUpdateAlarm;

  public ProjectListBuilder(final Project project,
                            final CommanderPanel panel,
                            final AbstractTreeStructure treeStructure,
                            final Comparator comparator,
                            final boolean showRoot) {
    super(project, panel.getList(), panel.getModel(), treeStructure, comparator, showRoot);

    myList.setCellRenderer(new ColoredCommanderRenderer(panel));

    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    myFileStatusListener = new MyFileStatusListener();
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    myCopyPasteListener = new MyCopyPasteListener();
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);
    buildRoot();
    myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProject);
  }

  protected void updateParentTitle() {
    if (myParentTitle == null) return;

    AbstractTreeNode node = getParentNode();
    Object parentElement = node.getValue();
    if (parentElement instanceof TreeElement){
      parentElement = ((StructureViewTreeElement)parentElement).getValue();
    }
    if (parentElement instanceof PsiElement) {
      myParentTitle.setText(Commander.getTitle(((PsiElement)parentElement)));
    }
    else {
      myParentTitle.setText(null);
    }
  }

  protected boolean nodeIsAcceptableForElement(AbstractTreeNode node, Object element) {
    return Comparing.equal(node.getValue(), element);
  }

  protected List<AbstractTreeNode> getAllAcceptableNodes(final Object[] childElements, VirtualFile file) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (int i = 0; i < childElements.length; i++) {
      ProjectViewNode childElement = (ProjectViewNode)childElements[i];
      if (childElement.contains(file)) result.add(childElement);
    }

    return result;
  }

  public void dispose() {
    super.dispose();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
  }

  public void addUpdateRequest() {
    final Runnable request = new Runnable() {
      public void run() {
        if (!myProject.isDisposed()) {
          // Rely on project view to commit PSI and wait until it's updated.
          if (myTreeStructure.hasSomethingToCommit() ) {
            myUpdateAlarm.cancelAllRequests();
            myUpdateAlarm.addRequest(this, 300, ModalityState.stateForComponent(myList));
            return;
          }
          updateList();
        }
      }
    };

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myUpdateAlarm.cancelAllRequests();
      myUpdateAlarm.addRequest(request, 300, ModalityState.stateForComponent(myList));
    }
    else {
      request.run();
    }
  }

  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childRemoved(final PsiTreeChangeEvent event) {
      final PsiElement child = event.getOldChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    public void childAdded(final PsiTreeChangeEvent event) {
      final PsiElement child = event.getNewChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged();
    }

    public void childReplaced(final PsiTreeChangeEvent event) {
      final PsiElement oldChild = event.getOldChild();
      final PsiElement newChild = event.getNewChild();
      if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
      if (oldChild instanceof PsiCodeBlock && newChild instanceof PsiCodeBlock) return; //optimization
      childrenChanged();
    }

    public void childMoved(final PsiTreeChangeEvent event) {
      childrenChanged();
    }

    public void childrenChanged(final PsiTreeChangeEvent event) {
      childrenChanged();
    }

    private void childrenChanged() {
      addUpdateRequest();
    }

    public void propertyChanged(final PsiTreeChangeEvent event) {
      final String propertyName = event.getPropertyName();
      if (propertyName.equals(PsiTreeChangeEvent.PROP_ROOTS)) {
        addUpdateRequest();
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
        childrenChanged();
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME) || propertyName.equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)){
        childrenChanged();
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_TYPES)){
        addUpdateRequest();
      }
    }
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      addUpdateRequest();
    }

    public void fileStatusChanged(final VirtualFile vFile) {
      final PsiManager manager = PsiManager.getInstance(myProject);

      if (vFile.isDirectory()) {
        final PsiDirectory directory = manager.findDirectory(vFile);
        if (directory != null) {
          myPsiTreeChangeListener.childrenChanged();
        }
      }
      else {
        final PsiFile file = manager.findFile(vFile);
        if (file != null){
          myPsiTreeChangeListener.childrenChanged();
        }
      }
    }
  }

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged(final Transferable oldTransferable, final Transferable newTransferable) {
      updateByTransferable(oldTransferable);
      updateByTransferable(newTransferable);
    }

    private void updateByTransferable(final Transferable t) {
      final PsiElement[] psiElements = CopyPasteUtil.getElementsInTransferable(t);
      for (int i = 0; i < psiElements.length; i++) {
        myPsiTreeChangeListener.childrenChanged();
      }
    }
  }
}
