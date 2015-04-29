package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null != file && HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {

      // Handling LazyGetter
      if (uninitializedField(highlightInfo.getDescription()) && LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)) {
        return false;
      }
    }
    return true;
  }

  private boolean uninitializedField(String description) {
    return UNINITIALIZED_MESSAGE.matcher(StringUtil.notNullize(description)).matches();
  }
}
