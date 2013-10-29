package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.extension.UserMapKeys;
import org.jetbrains.annotations.NotNull;


public class LombokAnnotator implements Annotator {
  private static final Logger log = Logger.getInstance(LombokAnnotator.class.getName());

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PsiModifierListOwner) {
      PsiModifierListOwner psiModifierList = (PsiModifierListOwner) element;
      PsiFile containingFile = psiModifierList.getContainingFile();
      if (null != containingFile && !UserMapKeys.containLombok(containingFile)) {

        PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotation(psiModifierList, LombokProcessorExtensionPoint.getAllOfProcessedLombokAnnotation(), true);
        if (null != psiAnnotation) {
          UserMapKeys.addLombokPresentFor(containingFile);
        }
        if (log.isDebugEnabled()) {
          log.debug(String.format("Processed file %s Lombok is %s", containingFile.getName(), null != psiAnnotation));
        }
      }
    }
  }
}
