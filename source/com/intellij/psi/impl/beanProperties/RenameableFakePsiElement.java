/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.beanProperties;

import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author peter
*/
public abstract class RenameableFakePsiElement extends FakePsiElement implements PsiMetaBaseOwner, PsiPresentableMetaData {
  private final PsiFile myContainingFile;

  public RenameableFakePsiElement(final PsiFile containingFile) {
    myContainingFile = containingFile;
  }

  public PsiFile getContainingFile() {
    return myContainingFile;
  }

  @NotNull
  public Language getLanguage() {
    return myContainingFile.getLanguage();
  }

  @NotNull
  public Project getProject() {
    return myContainingFile.getProject();
  }

  public PsiManager getManager() {
    return PsiManager.getInstance(getProject());
  }

  @Nullable
  public PsiMetaDataBase getMetaData() {
    return this;
  }

  public PsiElement getDeclaration() {
    return this;
  }

  @NonNls
  public String getName(final PsiElement context) {
    return getName();
  }

  public void init(final PsiElement element) {
  }

  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  public Icon getIcon(final int flags) {
    return getIcon();
  }
}
