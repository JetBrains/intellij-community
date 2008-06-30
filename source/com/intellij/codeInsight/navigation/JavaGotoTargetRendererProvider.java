package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiUtil;

import java.util.Arrays;

/**
 * @author yole
 */
public class JavaGotoTargetRendererProvider implements GotoTargetRendererProvider {
  public PsiElementListCellRenderer getRenderer(final PsiElement[] elements) {
    boolean onlyMethods = true;
    boolean onlyClasses = true;
    for (PsiElement element : elements) {
      if (!(element instanceof PsiMethod)) onlyMethods = false;
      if (!(element instanceof PsiClass)) onlyClasses = false;
    }
    if (onlyMethods) {
      return new MethodCellRenderer(!PsiUtil.allMethodsHaveSameSignature(Arrays.asList(elements).toArray(PsiMethod.EMPTY_ARRAY)));
    }
    else if (onlyClasses) {
      return new PsiClassListCellRenderer();
    }
    return null;
  }

}
