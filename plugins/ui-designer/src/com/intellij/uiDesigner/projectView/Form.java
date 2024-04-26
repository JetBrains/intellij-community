// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.projectView;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uiDesigner.binding.FormClassIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class Form implements Navigatable, Iterable<PsiElement> {
  public static final DataKey<Form[]> DATA_KEY = DataKey.create("form.array");

  private final Collection<PsiFile> myFormFiles;
  private final @NotNull PsiClass myClassToBind;

  public Form(@NotNull PsiClass classToBind) {
    myClassToBind = classToBind;
    myFormFiles = FormClassIndex.findFormsBoundToClass(classToBind.getProject(), classToBind);
  }

  public Form(@NotNull PsiClass classToBind, Collection<? extends PsiFile> formFiles) {
    myClassToBind = classToBind;
    myFormFiles = new HashSet<>(formFiles);
  }

  public boolean equals(Object object) {
    if (object instanceof Form form){
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

  public @NotNull PsiClass getClassToBind() {
    return myClassToBind;
  }

  public PsiFile[] getFormFiles() {
    return PsiUtilCore.toPsiFileArray(myFormFiles);
  }

  @Override
  public void navigate(boolean requestFocus) {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigate()) {
        psiFile.navigate(requestFocus);
      }
    }
  }

  @Override
  public boolean canNavigateToSource() {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigateToSource()) return true;
    }
    return false;
  }

  @Override
  public boolean canNavigate() {
    for (PsiFile psiFile : myFormFiles) {
      if (psiFile != null && psiFile.canNavigate()) return true;
    }
    return false;
  }

  public boolean isValid() {
    if (myFormFiles.isEmpty()) return false;
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

  @Override
  public @NotNull Iterator<PsiElement> iterator() {
    ArrayList<PsiElement> list = new ArrayList<>(myFormFiles);
    list.add(0, myClassToBind);
    return list.iterator();
  }
}
