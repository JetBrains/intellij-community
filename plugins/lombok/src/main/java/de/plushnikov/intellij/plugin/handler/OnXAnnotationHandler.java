package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;


public final class OnXAnnotationHandler {
  private static final Pattern UNDERSCORES = Pattern.compile("__*");
  private static final Pattern CANNOT_RESOLVE_SYMBOL_UNDERSCORES_MESSAGE = Pattern.compile(JavaErrorBundle.message("cannot.resolve.symbol", "__*"));
  private static final Pattern CANNOT_RESOLVE_METHOD_UNDERSCORES_MESSAGE = Pattern.compile(JavaErrorBundle.message("cannot.resolve.method", "(onMethod|onConstructor|onParam)_+"));

  private static final String ANNOTATION_TYPE_EXPECTED = JavaErrorBundle.message("annotation.annotation.type.expected");
  private static final String CANNOT_FIND_METHOD_VALUE_MESSAGE = JavaErrorBundle.message("annotation.missing.method", "value");

  private static final Collection<String> ONXABLE_ANNOTATIONS = Arrays.asList(
    LombokClassNames.GETTER,
    LombokClassNames.SETTER,
    LombokClassNames.WITH,
    LombokClassNames.WITHER,
    LombokClassNames.NO_ARGS_CONSTRUCTOR,
    LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
    LombokClassNames.ALL_ARGS_CONSTRUCTOR,
    LombokClassNames.EQUALS_AND_HASHCODE
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
