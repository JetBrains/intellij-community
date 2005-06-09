package com.intellij.ide.projectView.impl.nodes;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class Form implements Navigatable{
  private final Collection<PsiFile> myFormFiles;
  private final PsiClass myClassToBind;

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

  public void navigate(boolean requestFocus) {
    for (Iterator<PsiFile> iterator = myFormFiles.iterator(); iterator.hasNext();) {
      PsiFile psiFile = iterator.next();
      if (psiFile instanceof Navigatable && ((Navigatable)psiFile).canNavigate()) {
          ((Navigatable)psiFile).navigate(requestFocus);
      }
    }
  }

  public boolean canNavigateToSource() {
    for (Iterator<PsiFile> iterator = myFormFiles.iterator(); iterator.hasNext();) {
      PsiFile psiFile = iterator.next();
      if (psiFile instanceof Navigatable && ((Navigatable)psiFile).canNavigateToSource()) return true;
    }
    return false;
  }

  public boolean canNavigate() {
    for (Iterator<PsiFile> iterator = myFormFiles.iterator(); iterator.hasNext();) {
      PsiFile psiFile = iterator.next();
      if (psiFile instanceof Navigatable && ((Navigatable)psiFile).canNavigate()) return true;
    }
    return false;
  }

  public boolean isValid() {
    for (Iterator<PsiFile> iterator = myFormFiles.iterator(); iterator.hasNext();) {
      PsiFile psiFile = iterator.next();
      if (!psiFile.isValid()) {
        return false;
      }
    }
    if (!myClassToBind.isValid()) {
      return false;
    }
    return true;
  }
}

