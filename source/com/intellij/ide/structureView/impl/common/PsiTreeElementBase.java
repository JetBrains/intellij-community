/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.structureView.impl.common;

import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;

public abstract class PsiTreeElementBase <Value extends PsiElement> implements StructureViewTreeElement<Value>, ItemPresentation {
  private final SmartPsiElementPointer mySmartPsiElementPointer;

  protected PsiTreeElementBase(PsiElement psiElement) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public final Value getElement() {
    return (Value)mySmartPsiElementPointer.getElement();
  }

  public Icon getIcon(boolean open) {
    final PsiElement element = getElement();
    if (element != null) {
      return element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }
    else {
      return null;
    }
  }

  public Value getValue() {
    return getElement();
  }

  public String getLocationString() {
    return null;
  }

  public String toString() {
    return getElement().toString();
  }

  public TextAttributesKey getTextAttributesKey() {
    return isDeprecated() ? CodeInsightColors.DEPRECATED_ATTRIBUTES : null;
  }

  private boolean isDeprecated(){
    final Value element = getElement();
    if (element == null) return false;
    if (!(element instanceof PsiDocCommentOwner)) return false;
    return ((PsiDocCommentOwner)element).isDeprecated();

  }

  public final StructureViewTreeElement[] getChildren() {
    final Value element = getElement();
    if (element == null) return StructureViewTreeElement.EMPTY_ARRAY;
    List<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    Collection<StructureViewTreeElement> baseChildren = getChildrenBase();
    result.addAll(baseChildren);
    StructureViewFactoryEx structureViewFactory = StructureViewFactoryEx.getInstance(element.getProject());
    Class<? extends PsiElement> aClass = element.getClass();
    List<StructureViewExtension> allExtensions = structureViewFactory.getAllExtensions(aClass);
    for (StructureViewExtension extension : allExtensions) {
      StructureViewTreeElement[] children = extension.getChildren(element);
      if (children != null) {
        result.addAll(Arrays.asList(children));
      }
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);
  }

  public void navigate(boolean requestFocus) {
    ((Navigatable)getElement()).navigate(requestFocus);
  }

  public boolean canNavigate() {
    final Value element = getElement();
    if (element == null) return false;
    if (element instanceof Navigatable) {
      return ((Navigatable)element).canNavigate();
    }
    else {
      return false;
    }
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public abstract Collection<StructureViewTreeElement> getChildrenBase();
}
