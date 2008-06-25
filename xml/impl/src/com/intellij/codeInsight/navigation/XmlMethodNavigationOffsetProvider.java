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
  public int[] getMethodNavigationOffsets(final PsiFile file, final int caretOffset) {
    if (file instanceof XmlFile) {
      PsiElement element = file;
      PsiElement elementAt = file.findElementAt(caretOffset);
      elementAt = PsiTreeUtil.getParentOfType(elementAt, XmlTag.class);
      if (elementAt != null) element = elementAt;

      ArrayList<PsiElement> array = new ArrayList<PsiElement>();
      addNavigationElements(array, element);
      return MethodUpDownUtil.offsetsFromElements(array);
    }
    return null;
  }

  private static void addNavigationElements(ArrayList<PsiElement> array, PsiElement element) {
    PsiElement parent = element instanceof XmlFile ? element : element.getParent();

    PsiElement[] children = parent.getChildren();
    for (PsiElement child : children) {
      if (child instanceof XmlTag) {
        array.add(child);
      }
    }
    final PsiElement parentElement = element.getParent();
    if (parentElement != null) {
      addNavigationElements(array, parentElement);
    }
  }
}
