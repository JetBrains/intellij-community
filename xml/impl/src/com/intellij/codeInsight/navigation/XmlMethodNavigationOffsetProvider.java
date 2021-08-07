// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;


public class XmlMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {
  @Override
  public int[] getMethodNavigationOffsets(final PsiFile file, final int caretOffset) {
    if (file instanceof XmlFile) {
      PsiElement element = file;
      PsiElement elementAt = file.findElementAt(caretOffset);
      elementAt = PsiTreeUtil.getParentOfType(elementAt, XmlTag.class);
      if (elementAt != null) element = elementAt;

      ArrayList<PsiElement> array = new ArrayList<>();
      addNavigationElements(array, element);
      return MethodUpDownUtil.offsetsFromElements(array);
    }
    return null;
  }

  private static void addNavigationElements(ArrayList<? super PsiElement> array, PsiElement element) {
    PsiElement parent = element instanceof XmlFile ? element : element.getParent();

    if (parent != null) {
      PsiElement[] children = parent.getChildren();
      for (PsiElement child : children) {
        if (child instanceof XmlTag) {
          array.add(child);
        }
      }
    }
    final PsiElement parentElement = element.getParent();
    if (parentElement != null) {
      addNavigationElements(array, parentElement);
    }
  }
}
