package com.intellij.ide.commander;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.usageView.UsageViewUtil;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public class ProjectListBuilder extends AbstractListBuilder {
  private final MyPsiTreeChangeListener myPsiTreeChangeListener;
  private final MyFileStatusListener myFileStatusListener;
  private int myUpdateCount = 0;
  private final CopyPasteManager.ContentChangedListener myCopyPasteListener;

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
  }

  protected void updateParentTitle() {
    if (myParentTitle == null) return;

    Object parentElement = getParentElement();
    if (parentElement instanceof PsiFile) {
      parentElement = ((PsiFile)parentElement).getContainingDirectory();
    }
    if (!(parentElement instanceof PsiElement)) {
      myParentTitle.setText(null);
    }
    else {
      final String text;
      if (parentElement instanceof PsiDirectory){
        text = UsageViewUtil.getPackageName((PsiDirectory)parentElement, true);
      }
      else{
        text = UsageViewUtil.getLongName((PsiElement)parentElement);
      }
      myParentTitle.setText(text);
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
    myUpdateCount++;
    final int count = myUpdateCount;
    final Runnable updater = new Runnable() {
      public void run() {
        if (myUpdateCount != count || myProject.isDisposed()) return;
        updateList();
      }
    };
    final Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode()) {
      updater.run();
    }
    else {
      app.invokeLater(updater, ModalityState.current());
    }
  }

  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childRemoved(final PsiTreeChangeEvent event) {
      final PsiElement child = event.getOldChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged(event.getParent());
    }

    public void childAdded(final PsiTreeChangeEvent event) {
      final PsiElement child = event.getNewChild();
      if (child instanceof PsiWhiteSpace) return; //optimization
      childrenChanged(event.getParent());
    }

    public void childReplaced(final PsiTreeChangeEvent event) {
      final PsiElement oldChild = event.getOldChild();
      final PsiElement newChild = event.getNewChild();
      if (oldChild instanceof PsiWhiteSpace && newChild instanceof PsiWhiteSpace) return; //optimization
      if (oldChild instanceof PsiCodeBlock && newChild instanceof PsiCodeBlock) return; //optimization
      childrenChanged(event.getParent());
    }

    public void childMoved(final PsiTreeChangeEvent event) {
      childrenChanged(event.getOldParent());
      childrenChanged(event.getNewParent());
    }

    public void childrenChanged(final PsiTreeChangeEvent event) {
      childrenChanged(event.getParent());
    }

    private void childrenChanged(PsiElement parent) {
      while(true){
        if (parent == null) break;
        if (parent instanceof PsiCodeBlock) break;
        if (parent instanceof PsiFile){
          parent = ((PsiFile)parent).getContainingDirectory();
          if (parent == null) break;
        }

        if (parent instanceof PsiMethod
          || parent instanceof PsiField
          || parent instanceof PsiClass
          || parent instanceof PsiFile
          || parent instanceof PsiDirectory) break;
        parent = parent.getParent();
      }

      if (parent == null) {
        return;
      }
      final Object element = getParentElement();
      if (element instanceof PsiElement){
        if (!((PsiElement)element).isValid()){
          addUpdateRequest();
          return;
        }
        PsiElement psiElement = (PsiElement)element;
        PsiElement pparent = parent.getParent();
        if (pparent instanceof PsiFile){
          pparent = pparent.getParent();
        }
        if (psiElement.equals(pparent)){
          addUpdateRequest();
          return;
        }
        while(true){
          if (psiElement == null) return;
          if (psiElement.equals(parent)){
            addUpdateRequest();
            return;
          }
          psiElement = psiElement.getParent();
        }
      }
    }

    public void propertyChanged(final PsiTreeChangeEvent event) {
      final String propertyName = event.getPropertyName();
      final PsiElement element = event.getElement();
      if (propertyName.equals(PsiTreeChangeEvent.PROP_ROOTS)) {
        addUpdateRequest();
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
        childrenChanged(element);
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_NAME) || propertyName.equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)){
        childrenChanged(element);
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
          myPsiTreeChangeListener.childrenChanged(directory.getParent());
        }
      }
      else {
        final PsiFile file = manager.findFile(vFile);
        if (file != null){
          myPsiTreeChangeListener.childrenChanged(file.getContainingDirectory());
        }
      }
    }
  }

  private final class MyCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    public void contentChanged() {
      addUpdateRequest();
    }
  }
}
