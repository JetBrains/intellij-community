package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import de.plushnikov.intellij.plugin.handler.SneakyThrowsExceptionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final String UNHANDLED_EXCEPTION_PREFIX_TEXT = "Unhandled exception:";
  private static final String UNHANDLED_EXCEPTIONS_PREFIX_TEXT = "Unhandled exceptions:";
  private static final String UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT = "Unhandled exception from auto-closeable resource:";

  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null != file && HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      final String description = StringUtil.notNullize(highlightInfo.getDescription());

      // Handling SneakyThrows
      if (HighlightInfoType.UNHANDLED_EXCEPTION.equals(highlightInfo.type) && unhandledException(description)) {
        final String unhandledExceptions = description.substring(description.indexOf(':') + 1).trim();
        final String[] exceptionFQNs = unhandledExceptions.split(",");
        if (exceptionFQNs.length > 0) {
          final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(file.findElementAt(highlightInfo.getStartOffset()), PsiMethod.class);
          if (null != psiMethod) {
            return !SneakyThrowsExceptionHandler.isExceptionHandled(psiMethod, exceptionFQNs);
          }
        }
      }
      // Handling LazyGetter
      if (uninitializedField(description) && LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)) {
        return false;
      }
    }
    return true;
  }

  private boolean unhandledException(String description) {
    return (StringUtil.startsWith(description, UNHANDLED_EXCEPTION_PREFIX_TEXT) ||
        StringUtil.startsWith(description, UNHANDLED_EXCEPTIONS_PREFIX_TEXT) ||
        StringUtil.startsWith(description, UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT));
  }

  private boolean uninitializedField(String description) {
    Matcher matcher = UNINITIALIZED_MESSAGE.matcher(description);
    return matcher.matches();
  }
}
