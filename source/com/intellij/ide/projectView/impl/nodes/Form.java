package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class Form implements Navigatable{
  private final Collection<PsiFile> myFormFiles;
  private final PsiClass myClassToBind;

  public Form(PsiClass classToBind) {
    myClassToBind = classToBind;
    myFormFiles = Arrays.asList(classToBind.getManager().getSearchHelper().findFormsBoundToClass(classToBind.getQualifiedName()));
  }

  public Form(PsiClass classToBind, Collection<PsiFile> formFiles) {
    myClassToBind = classToBind;
    myFormFiles = new HashSet<PsiFile>(formFiles);
  }

  public boolean equals(Object object) {
    if (object instanceof Form){
      Form form = (Form)object;
      return myFormFiles.equals(form.myFormFiles) && myClassToBind.equals(form.myClassToBind);
    } else {
      return false;
    }
  }

  public int hashCode() {
    return myFormFiles.hashCode() ^ myClassToBind.hashCode();
  }

  public String getName() {
    return myClassToBind.getName();
  }

  public PsiClass getClassToBind() {
    return myClassToBind;
  }

  public PsiFile[] getFormFiles() {
    return myFormFiles.toArray(new PsiFile[myFormFiles.size()]);
  }

  public void navigate(boolean requestFocus) {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigate()) {
        psiFile.navigate(requestFocus);
      }
    }
  }

  public boolean canNavigateToSource() {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigateToSource()) return true;
    }
    return false;
  }

  public boolean canNavigate() {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigate()) return true;
    }
    return false;
  }

  public boolean isValid() {
    if (myFormFiles.size() == 0) return false;    
    for (PsiFile psiFile : myFormFiles) {
      if (!psiFile.isValid()) {
        return false;
      }
    }
    if (!myClassToBind.isValid()) {
      return false;
    }
    return true;
  }

  public boolean containsFile(final VirtualFile vFile) {
    final PsiFile classFile = myClassToBind.getContainingFile();
    final VirtualFile classVFile = classFile == null ? null : classFile.getVirtualFile();
    if (classVFile != null && classVFile.equals(vFile)) {
      return true;
    }
    for (PsiFile psiFile : myFormFiles) {
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && virtualFile.equals(vFile)) {
        return true;
      }
    }
    return false;
  }
}
