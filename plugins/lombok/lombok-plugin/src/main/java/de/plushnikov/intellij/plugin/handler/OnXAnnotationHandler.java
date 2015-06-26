package de.plushnikov.intellij.plugin.handler;

import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameValuePair;

import java.util.regex.Pattern;

public class OnXAnnotationHandler {
  private static final Pattern UNDERSCORES = Pattern.compile("__*");
  private static final Pattern CANNOT_RESOLVE_UNDERSCORES_MESSAGE = Pattern.compile("Cannot resolve symbol '__*'");
  
  private static final String ANNOTATION_TYPE_EXPECTED = "Annotation type expected";
  private static final String CANNOT_FIND_METHOD_VALUE_MESSAGE = "Cannot find method 'value'";
  
  private static final ImmutableList<String> ONXABLE_ANNOTATIONS = ImmutableList.of(
      "lombok.Getter",
      "lombok.Setter",
      "lombok.experimental.Wither",
      "lombok.NoArgsConstructor",
      "lombok.RequiredArgsConstructor",
      "lombok.AllArgsConstructor",
      "lombok.EqualsAndHashCode"
  );
  private static final ImmutableList<String> ONX_PARAMETERS = ImmutableList.of(
      "onConstructor",
      "onMethod",
      "onParam"
  );

  public static boolean isOnXParameterAnnotation(HighlightInfo highlightInfo, PsiFile file) {
    if (!(ANNOTATION_TYPE_EXPECTED.equals(highlightInfo.getDescription())
        || CANNOT_RESOLVE_UNDERSCORES_MESSAGE.matcher(StringUtil.notNullize(highlightInfo.getDescription())).matches())) {
      return false;
    }
    
    PsiElement highlightedElement = file.findElementAt(highlightInfo.getStartOffset());
    
    PsiNameValuePair nameValuePair = findContainingNameValuePair(highlightedElement);
    if (nameValuePair == null || !(nameValuePair.getContext() instanceof PsiAnnotationParameterList)) {
      return false;
    }

    String parameterName = nameValuePair.getFirstChild().getText();
    if (!ONX_PARAMETERS.contains(parameterName)) {
      return false;
    }
    
    PsiElement containingAnnotation = nameValuePair.getContext().getPrevSibling().getContext();
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
