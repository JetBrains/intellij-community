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

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public abstract class PsiTreeElementBase implements StructureViewTreeElement, ItemPresentation {
  public ItemPresentation getPresentation() {
    return this;
  }

  public abstract PsiElement getElement();

  public Icon getIcon(boolean open) {
    return getElement().getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
  }

  public Object getValue() {
    return getElement();
  }

  public String getLocationString() {
    return null;
  }

  public String toString() {
    return getElement().toString();
  }

  public final StructureViewTreeElement[] getChildren() {
    List<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    StructureViewTreeElement[] baseChildren = getChildrenBase();
    result.addAll(Arrays.asList(baseChildren));
    StructureViewFactory structureViewFactory = StructureViewFactory.getInstance(getElement().getProject());
    List<StructureViewExtension> allExtensions = structureViewFactory.getAllExtensions(getElement().getClass());
    for (Iterator<StructureViewExtension> iterator = allExtensions.iterator(); iterator.hasNext();) {
      StructureViewExtension extension = iterator.next();
      StructureViewTreeElement[] children = extension.getChildren(getElement());
      if (children != null){
        result.addAll(Arrays.asList(children));
      }
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);
  }

  public abstract  StructureViewTreeElement[] getChildrenBase();
}
