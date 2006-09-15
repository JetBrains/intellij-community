package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.HashSet;
import java.util.Set;

public class PackageDependenciesNode extends DefaultMutableTreeNode implements Navigatable{
  private static final EmptyIcon EMPTY_ICON = new EmptyIcon(0, IconUtilEx.getEmptyIcon(false).getIconHeight());

  private Set<PsiFile> myRegisteredFiles = new HashSet<PsiFile>();
  private boolean myHasUnmarked = false;
  private boolean myHasMarked = false;
  private boolean myEquals;

  public void setEquals(final boolean equals) {
    myEquals = equals;
  }

  public boolean isEquals() {
    return myEquals;
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    set.addAll(myRegisteredFiles);
  }

  public void addFile(PsiFile file, boolean isMarked) {
    myRegisteredFiles.add(file);
    updateMarked(!isMarked, isMarked);
  }

  public Icon getOpenIcon() {
    return EMPTY_ICON;
  }

  public Icon getClosedIcon() {
    return EMPTY_ICON;
  }

  public int getWeight() {
    return 0;
  }

  public boolean hasUnmarked() {
    return myHasUnmarked;
  }

  public boolean hasMarked() {
    return myHasMarked;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return null;
  }

  public FileStatus getStatus(){
    return FileStatus.NOT_CHANGED;
  }

  public int getContainingFiles(){
    int result = 0;
    for (int i = 0; i < getChildCount(); i++) {
      result += ((PackageDependenciesNode)getChildAt(i)).getContainingFiles();
    }
    return result;
  }

  public String getPresentableFilesCount(){
    final int filesCount = getContainingFiles();
    return filesCount > 0 ? " (" + AnalysisScopeBundle.message("package.dependencies.node.items.count", filesCount) + ")" : "";
  }

  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    boolean hasUnmarked = ((PackageDependenciesNode)newChild).hasUnmarked();
    boolean hasMarked = ((PackageDependenciesNode)newChild).hasMarked();
    updateMarked(hasUnmarked, hasMarked);
  }

  private void updateMarked(boolean hasUnmarked, boolean hasMarked) {
    if (hasUnmarked && !myHasUnmarked || hasMarked && !myHasMarked) {
      myHasUnmarked |= hasUnmarked;
      myHasMarked |= hasMarked;
      PackageDependenciesNode parent = ((PackageDependenciesNode)getParent());
      if (parent != null) {
        parent.updateMarked(myHasUnmarked, myHasMarked);
      }
    }
  }

  public void navigate(boolean focus) {
    if (canNavigate()) {
      openTextEditor(focus);
    }
  }

  @Nullable
  private Editor openTextEditor(boolean focus) {
    final OpenFileDescriptor descriptor = getDescriptor();
    if (descriptor != null) {
      return FileEditorManager.getInstance(getProject()).openTextEditor(descriptor, focus);
    }
    return null;
  }

  public boolean canNavigate() {
    if (getProject() == null) return false;
    final PsiElement psiElement = getPsiElement();
    if (psiElement == null) return false;
    final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    return virtualFile != null && virtualFile.isValid();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Nullable
  private Project getProject(){
    final PsiElement psiElement = getPsiElement();
    if (psiElement == null || psiElement.getContainingFile() == null){
      return null;
    }
    return psiElement.getContainingFile().getProject();
  }

  @Nullable
  private OpenFileDescriptor getDescriptor() {
    if (getProject() == null) return null;
    final PsiElement psiElement = getPsiElement();
    if (psiElement == null) return null;
    final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return new OpenFileDescriptor(getProject(), virtualFile);
  }

  public Object getUserObject() {
    return toString();
  }
}