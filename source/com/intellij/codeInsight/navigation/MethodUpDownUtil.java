
package com.intellij.codeInsight.navigation;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.Arrays;

public class MethodUpDownUtil {
  public static int[] getNavigationOffsets(PsiElement element) {
    ArrayList array = new ArrayList();
    addNavigationElements(array, element);
    int[] offsets = new int[array.size()];
    for(int i = 0; i < array.size(); i++){
      PsiElement e = (PsiElement)array.get(i);
      offsets[i] = e.getTextOffset();
    }
    Arrays.sort(offsets);
    return offsets;
  }

  private static void addNavigationElements(ArrayList array, PsiElement element) {
    if (element instanceof PsiJavaFile || element instanceof PsiClass){
      PsiElement[] children = element.getChildren();
      for(int i = 0; i < children.length; i++){
        PsiElement child = children[i];
        if (child instanceof PsiMethod || child instanceof PsiClass){
          array.add(child);
          addNavigationElements(array, child);
        }
        if (element instanceof PsiClass && child instanceof PsiJavaToken && child.getText().equals("}")){
          array.add(child);
        }
      }
    } else if (element instanceof XmlFile || element instanceof XmlTag) {
      PsiElement parent = element instanceof XmlFile ? element : element.getParent();

      PsiElement[] children = parent.getChildren();
      for (int i = 0; i < children.length; i++) {
        PsiElement child = children[i];
        if (child instanceof XmlTag) {
          array.add(child);
        }
      }
      addNavigationElements(array, element.getParent());
    }
  }
}