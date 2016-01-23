package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {
  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");
  private static final Pattern LOMBOK_ANYANNOTATIONREQUIRED = Pattern.compile("Incompatible types\\. Found: '__*', required: 'lombok.*AnyAnnotation\\[\\]'");

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null != file && HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {

      String description = StringUtil.notNullize(highlightInfo.getDescription());
      
      // Handling LazyGetter
      if (uninitializedField(description) && LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)) {
        return false;
      }
      
      //Handling onX parameters
      if (OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)
          || OnXAnnotationHandler.isOnXParameterValue(highlightInfo, file)
          || LOMBOK_ANYANNOTATIONREQUIRED.matcher(description).matches()) {
        return false;
      }
    }
    return true;
  }

  private boolean uninitializedField(String description) {
    return UNINITIALIZED_MESSAGE.matcher(description).matches();
  }
}
