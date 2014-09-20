package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.Getter;
import lombok.LazyGetter;

import java.util.Collection;

public class LazyGetterHandler {

  private static final String LAZY_GETTER_FQN = LazyGetter.class.getName();
  private static final String GETTERN_FQN = Getter.class.getName();

  public static boolean isLazyGetterHandled(HighlightInfo highlightInfo, PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (!(element instanceof PsiIdentifier)) {
      return false;
    }
    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) {
      return false;
    }
    Collection<PsiAnnotation> psiAnnotations = PsiTreeUtil.findChildrenOfType(field, PsiAnnotation.class);
    for (PsiAnnotation psiAnnotation : psiAnnotations) {
      final String qualifiedName = psiAnnotation.getQualifiedName();
      if (GETTERN_FQN.equals(qualifiedName)) {
        Boolean lazyObj = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "lazy", Boolean.class);
        return null != lazyObj && lazyObj;
      } else if (LAZY_GETTER_FQN.equals(qualifiedName)) {
        return true;
      }
    }
    return false;
  }
}
