package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.handler.SneakyTrowsExceptionHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final String UNHANDLED_EXCEPTION_PREFIX_TEXT = "Unhandled exception:";
  private static final String UNHANDLED_EXCEPTIONS_PREFIX_TEXT = "Unhandled exceptions:";
  private static final String UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT = "Unhandled exception from auto-closeable resource:";

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null != file && HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      final String description = StringUtil.notNullize(highlightInfo.getDescription());

      if (HighlightInfoType.UNHANDLED_EXCEPTION.equals(highlightInfo.type) &&
          (StringUtil.startsWith(description, UNHANDLED_EXCEPTION_PREFIX_TEXT) ||
              StringUtil.startsWith(description, UNHANDLED_EXCEPTIONS_PREFIX_TEXT) ||
              StringUtil.startsWith(description, UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT))) {
        final String unhandledExceptions = description.substring(description.indexOf(':') + 1).trim();
        final String[] exceptionFQNs = unhandledExceptions.split(",");
        if (exceptionFQNs.length > 0) {
          final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(file.findElementAt(highlightInfo.getStartOffset()), PsiMethod.class);
          if (null != psiMethod) {
            return !SneakyTrowsExceptionHandler.isExceptionHandled(psiMethod, exceptionFQNs);
          }
        }
      }

      if (uninitializedField(description) && isLazyGetter(highlightInfo, file)) {
        return false;
      }
    }
    return true;
  }

  private boolean isLazyGetter(HighlightInfo highlightInfo, PsiFile file) {
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
      if ("lombok.LazyGetter".equals(psiAnnotation.getQualifiedName())) {
        return true;
      } else if ("lombok.Getter".equals(psiAnnotation.getQualifiedName())) {
        Boolean lazyObj = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "lazy", Boolean.class);
        return null != lazyObj && lazyObj;
      }
    }
    return false;
  }

  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");

  private boolean uninitializedField(String description) {
    Matcher matcher = UNINITIALIZED_MESSAGE.matcher(description);
    return matcher.matches();
  }
}
