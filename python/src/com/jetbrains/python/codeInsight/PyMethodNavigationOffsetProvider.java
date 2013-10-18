package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.navigation.MethodNavigationOffsetProvider;
import com.intellij.codeInsight.navigation.MethodUpDownUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.Collections;

/**
 * User : ktisha
 */
public class PyMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {

  @Override
  public int[] getMethodNavigationOffsets(final PsiFile file, final int caretOffset) {
    if (file instanceof PyFile) {
      final ArrayList<PsiElement> array = new ArrayList<PsiElement>();
      addNavigationElements(array, file);
      return MethodUpDownUtil.offsetsFromElements(array);
    }
    return null;
  }

  private static void addNavigationElements(final ArrayList<PsiElement> array, final PsiElement psiElement) {
    if (psiElement instanceof PyFile) {
      for (PyClass pyClass : ((PyFile)psiElement).getTopLevelClasses()) {
        addNavigationElements(array, pyClass);
      }
      for (PyFunction pyFunction : ((PyFile)psiElement).getTopLevelFunctions()) {
        addNavigationElements(array, pyFunction);
      }
    }
    else if (psiElement instanceof PyFunction){
      array.add(psiElement);
    }
    else if (psiElement instanceof PyClass) {
      Collections.addAll(array, ((PyClass)psiElement).getMethods());
    }
  }
}
