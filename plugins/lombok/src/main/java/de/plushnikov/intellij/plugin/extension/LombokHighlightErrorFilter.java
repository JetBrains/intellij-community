package de.plushnikov.intellij.plugin.extension;

import java.util.regex.Pattern;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import de.plushnikov.intellij.plugin.handler.EqualsAndHashCodeCallSuperHandler;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {
  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");
  private static final Pattern LOMBOK_ANYANNOTATIONREQUIRED = Pattern.compile("Incompatible types\\. Found: '__*', required: 'lombok.*AnyAnnotation\\[\\]'");
  private static final String REDUNDANT_DEFAULT_PARAMETER_VALUE_ASSIGNMENT = "Redundant default parameter value assignment";
  private static final Pattern METHOD_INVOCATION_WILL_PRODUCE_NPE = Pattern.compile("Method invocation '.*' will produce 'NullPointerException'");

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null != file) {
      String description = StringUtil.notNullize(highlightInfo.getDescription());
      if (HighlightSeverity.WARNING.equals(highlightInfo.getSeverity())) {
        if (REDUNDANT_DEFAULT_PARAMETER_VALUE_ASSIGNMENT.equals(description)) {
          return !EqualsAndHashCodeCallSuperHandler.isEqualsAndHashCodeCallSuperDefault(highlightInfo, file);
        } else if (CodeInsightColors.WARNINGS_ATTRIBUTES.equals(highlightInfo.type.getAttributesKey())) {

          // field should have lazy getter and should be initialized in constructors
          if (METHOD_INVOCATION_WILL_PRODUCE_NPE.matcher(description).matches()) {
            return !LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)
              || !LazyGetterHandler.isInitializedInConstructors(highlightInfo, file);
          }
        }
      } else if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {

        // Handling LazyGetter
        if (uninitializedField(description) && LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)) {
          return false;
        }

        //Handling onX parameters
        return !OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)
          && !OnXAnnotationHandler.isOnXParameterValue(highlightInfo, file)
          && !LOMBOK_ANYANNOTATIONREQUIRED.matcher(description).matches();
      }
    }
    return true;
  }

  private boolean uninitializedField(String description) {
    return UNINITIALIZED_MESSAGE.matcher(description).matches();
  }
}
