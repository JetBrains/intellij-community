package de.plushnikov.intellij.lombok.quickfix;

import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.util.IntelliJVersionRangeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class PsiQuickFixFactory {
  public static LocalQuickFix createAddAnnotationQuickFix(@NotNull PsiClass psiClass, @NotNull String annotationFQN, @Nullable String annotationParam) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
    PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText("@" + annotationFQN + "(" + StringUtil.notNullize(annotationParam) + ")", psiClass);
    final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();

    final BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    switch (IntelliJVersionRangeUtil.getIntelliJVersion(buildNumber)) {
      case INTELLIJ_8:
        throw new RuntimeException(String.format("This version (%s) of IntelliJ is not supported!", buildNumber.asString()));
      case INTELLIJ_9:
        return new LombokAnnotationQuickFix9Impl(annotationFQN, psiClass, attributes);
      case INTELLIJ_10:
      case INTELLIJ_10_5:
        return new LombokAnnotationQuickFix10Impl(annotationFQN, psiClass, attributes);
      case INTELLIJ_11:
      default:
        return new LombokAnnotationQuickFix11Impl(annotationFQN, psiClass, attributes);
    }
  }

  public static LocalQuickFix createModifierListFix(@NotNull PsiModifierListOwner owner, @NotNull String modifier, boolean shouldHave, final boolean showContainingClass) {
    return new ModifierFix(owner, modifier, shouldHave, showContainingClass);
  }

  public static LocalQuickFix createNewFieldFix(@NotNull PsiClass psiClass, @NotNull String name, @NotNull PsiType psiType, @Nullable String initializerText, String... modifiers) {
    return new CreateFieldQuickFix(psiClass, name, psiType, initializerText, modifiers);
  }

  public static LocalQuickFix createChangeAnnotationParameterFix(@NotNull PsiAnnotation psiAnnotation, @NotNull String name, @Nullable String newValue) {
    return new ChangeAnnotationParameterQuickFix(psiAnnotation, name, newValue);
  }

//  private void register(String message) {
//    TextRange textRange = new TextRange(0, 0);
//    HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, textRange, message);
//    IntentionAction fix = null;
//    QuickFixAction.registerQuickFixAction(highlightInfo, fix);
//  }
}
