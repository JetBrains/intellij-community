/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;

/**
 * @author yole
 */
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

  private static void addNavigationElements(ArrayList<PsiElement> array, PsiElement element) {
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
