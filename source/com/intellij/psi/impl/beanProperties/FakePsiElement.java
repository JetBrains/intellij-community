/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.beanProperties;

import com.intellij.extapi.psi.PsiElementBase;
import com.intellij.lang.Language;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.navigation.ItemPresentation;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class FakePsiElement extends PsiElementBase implements PsiNamedElement, ItemPresentation {

  public ItemPresentation getPresentation() {
    return this;
  }

  @NotNull
  public Language getLanguage() {
    return StdLanguages.JAVA;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Nullable
  public PsiElement getFirstChild() {
    return null;
  }

  @Nullable
  public PsiElement getLastChild() {
    return null;
  }

  @Nullable
  public PsiElement getNextSibling() {
    return null;
  }

  @Nullable
  public PsiElement getPrevSibling() {
    return null;
  }

  @Nullable
  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return 0;
  }

  public int getTextLength() {
    return 0;
  }

  @Nullable
  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return 0;
  }

  @Nullable
  @NonNls
  public String getText() {
    return null;
  }

  @NotNull
  public char[] textToCharArray() {
    return new char[0];
  }

  public boolean textContains(char c) {
    return false;
  }

  @Nullable
  public ASTNode getNode() {
    return null;
  }

  public String getPresentableText() {
    return getName();
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return getIcon(0);
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;
  }
}
