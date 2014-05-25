package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.handler.SneakyTrowsExceptionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    }
    return true;
  }
}
