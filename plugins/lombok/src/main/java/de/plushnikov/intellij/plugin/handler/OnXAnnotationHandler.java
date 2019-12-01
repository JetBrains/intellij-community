package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameValuePair;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.With;
import lombok.experimental.Wither;


public class OnXAnnotationHandler {
  private static final Pattern UNDERSCORES = Pattern.compile("__*");
  private static final Pattern CANNOT_RESOLVE_SYMBOL_UNDERSCORES_MESSAGE = Pattern.compile("Cannot resolve symbol '__*'");
  private static final Pattern CANNOT_RESOLVE_METHOD_UNDERSCORES_MESSAGE = Pattern.compile("Cannot resolve method '(onMethod|onConstructor|onParam)_+'");

  private static final String ANNOTATION_TYPE_EXPECTED = "Annotation type expected";
  private static final String CANNOT_FIND_METHOD_VALUE_MESSAGE = "Cannot find method 'value'";

  private static final Collection<String> ONXABLE_ANNOTATIONS = Arrays.asList(
    Getter.class.getCanonicalName(),
    Setter.class.getCanonicalName(),
    With.class.getCanonicalName(),
    Wither.class.getCanonicalName(),
    NoArgsConstructor.class.getCanonicalName(),
    RequiredArgsConstructor.class.getCanonicalName(),
    AllArgsConstructor.class.getCanonicalName(),
    EqualsAndHashCode.class.getCanonicalName()
  );
  private static final Collection<String> ONX_PARAMETERS = Arrays.asList(
    "onConstructor",
    "onMethod",
    "onParam"
  );

  public static boolean isOnXParameterAnnotation(HighlightInfo highlightInfo, PsiFile file) {
    final String description = StringUtil.notNullize(highlightInfo.getDescription());
    if (!(ANNOTATION_TYPE_EXPECTED.equals(description)
      || CANNOT_RESOLVE_SYMBOL_UNDERSCORES_MESSAGE.matcher(description).matches()
      || CANNOT_RESOLVE_METHOD_UNDERSCORES_MESSAGE.matcher(description).matches())) {
      return false;
    }

    PsiElement highlightedElement = file.findElementAt(highlightInfo.getStartOffset());

    PsiNameValuePair nameValuePair = findContainingNameValuePair(highlightedElement);
    if (nameValuePair == null || !(nameValuePair.getContext() instanceof PsiAnnotationParameterList)) {
      return false;
    }

    String parameterName = nameValuePair.getName();
    if (null != parameterName && parameterName.contains("_")) {
      parameterName = parameterName.substring(0, parameterName.indexOf('_'));
    }
    if (!ONX_PARAMETERS.contains(parameterName)) {
      return false;
    }

    PsiElement containingAnnotation = nameValuePair.getContext().getContext();
    return containingAnnotation instanceof PsiAnnotation && ONXABLE_ANNOTATIONS.contains(((PsiAnnotation) containingAnnotation).getQualifiedName());
  }

  public static boolean isOnXParameterValue(HighlightInfo highlightInfo, PsiFile file) {
    if (!CANNOT_FIND_METHOD_VALUE_MESSAGE.equals(highlightInfo.getDescription())) {
      return false;
    }

    PsiElement highlightedElement = file.findElementAt(highlightInfo.getStartOffset());
    PsiNameValuePair nameValuePair = findContainingNameValuePair(highlightedElement);
    if (nameValuePair == null || !(nameValuePair.getContext() instanceof PsiAnnotationParameterList)) {
      return false;
    }

    PsiElement leftSibling = nameValuePair.getContext().getPrevSibling();
    return (leftSibling != null && UNDERSCORES.matcher(StringUtil.notNullize(leftSibling.getText())).matches());
  }

  private static PsiNameValuePair findContainingNameValuePair(PsiElement highlightedElement) {
    PsiElement nameValuePair = highlightedElement;
    while (!(nameValuePair == null || nameValuePair instanceof PsiNameValuePair)) {
      nameValuePair = nameValuePair.getContext();
    }

    return (PsiNameValuePair) nameValuePair;
  }
}
