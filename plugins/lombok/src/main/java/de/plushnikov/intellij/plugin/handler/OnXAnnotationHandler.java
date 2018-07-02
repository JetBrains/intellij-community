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

public class OnXAnnotationHandler {
  private static final Pattern UNDERSCORES = Pattern.compile("__*");
  private static final Pattern CANNOT_RESOLVE_UNDERSCORES_MESSAGE = Pattern.compile("Cannot resolve symbol '__*'");

  private static final String ANNOTATION_TYPE_EXPECTED = "Annotation type expected";
  private static final String CANNOT_FIND_METHOD_VALUE_MESSAGE = "Cannot find method 'value'";

  private static final String CANNOT_RESOLVE_METHOD_ON_METHOD_MESSAGE = "Cannot resolve method 'onMethod_'";
  private static final String CANNOT_RESOLVE_METHOD_ON_CONSTRUCTOR_MESSAGE = "Cannot resolve method 'onConstructor_'";
  private static final String CANNOT_RESOLVE_METHOD_ON_PARAM_MESSAGE = "Cannot resolve method 'onParam_'";

  private static final Collection<String> ONXABLE_ANNOTATIONS = Arrays.asList(
    "lombok.Getter",
    "lombok.Setter",
    "lombok.experimental.Wither",
    "lombok.NoArgsConstructor",
    "lombok.RequiredArgsConstructor",
    "lombok.AllArgsConstructor",
    "lombok.EqualsAndHashCode"
  );
  private static final Collection<String> ONX_PARAMETERS = Arrays.asList(
    "onConstructor",
    "onMethod",
    "onParam",
    "onConstructor_",
    "onMethod_",
    "onParam_"
  );

  public static boolean isOnXParameterAnnotation(HighlightInfo highlightInfo, PsiFile file) {
    final String description = StringUtil.notNullize(highlightInfo.getDescription());
    if (!(ANNOTATION_TYPE_EXPECTED.equals(description)
      || CANNOT_RESOLVE_UNDERSCORES_MESSAGE.matcher(description).matches()
      || CANNOT_RESOLVE_METHOD_ON_METHOD_MESSAGE.equals(description)
      || CANNOT_RESOLVE_METHOD_ON_CONSTRUCTOR_MESSAGE.equals(description)
      || CANNOT_RESOLVE_METHOD_ON_PARAM_MESSAGE.equals(description))) {
      return false;
    }

    PsiElement highlightedElement = file.findElementAt(highlightInfo.getStartOffset());

    PsiNameValuePair nameValuePair = findContainingNameValuePair(highlightedElement);
    if (nameValuePair == null || !(nameValuePair.getContext() instanceof PsiAnnotationParameterList)) {
      return false;
    }

    String parameterName = nameValuePair.getName();
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
