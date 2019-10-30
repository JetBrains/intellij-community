package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.handler.EqualsAndHashCodeCallSuperHandler;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");
  private static final Pattern LOMBOK_ANY_ANNOTATION_REQUIRED = Pattern.compile("Incompatible types\\. Found: '__*', required: 'lombok.*AnyAnnotation\\[\\]'");

  private final Map<HighlightSeverity, Map<TextAttributesKey, List<LombokHighlightFilter>>> registeredFilters;

  public LombokHighlightErrorFilter() {
    registeredFilters = new HashMap<>();

    for (LombokHighlightFilter value : LombokHighlightFilter.values()) {
      registeredFilters.computeIfAbsent(value.severity, s -> new HashMap<>())
        .computeIfAbsent(value.key, k -> new ArrayList<>())
        .add(value);
    }
  }

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null == file) {
      return true;
    }

    PsiElement highlightedElement = file.findElementAt(highlightInfo.getStartOffset());
    if (null == highlightedElement) {
      return true;
    }

    String description = StringUtil.notNullize(highlightInfo.getDescription());
    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {

      // Handling LazyGetter
      if (uninitializedField(description) && LazyGetterHandler.isLazyGetterHandled(highlightedElement)) {
        return false;
      }

      //Handling onX parameters
      return !OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)
        && !OnXAnnotationHandler.isOnXParameterValue(highlightInfo, file)
        && !LOMBOK_ANY_ANNOTATION_REQUIRED.matcher(description).matches();
    }

    // check other exceptions for highlights
    Map<TextAttributesKey, List<LombokHighlightFilter>> severityMap = registeredFilters
      .getOrDefault(highlightInfo.getSeverity(), Collections.emptyMap());

    return severityMap.getOrDefault(highlightInfo.type.getAttributesKey(), Collections.emptyList()).stream()
      .filter(filter -> filter.descriptionCheck(description))
      .allMatch(filter -> filter.accept(highlightedElement));
  }

  private enum LombokHighlightFilter {
    VARIABLE_INITIALIZER_IS_REDUNDANT(HighlightSeverity.WARNING, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES) {
      private final Pattern pattern = Pattern.compile("Variable '.+' initializer '.+' is redundant");

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null && pattern.matcher(description).matches();
      }

      public boolean accept(@NotNull PsiElement highlightedElement) {
        return !BuilderHandler.isDefaultBuilderValue(highlightedElement);
      }
    },

    /**
     * field should have lazy getter and should be initialized in constructors
     */
    METHOD_INVOCATION_WILL_PRODUCE_NPE(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES) {
      private final Pattern pattern = Pattern.compile("Method invocation '.*' will produce 'NullPointerException'");

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null && pattern.matcher(description).matches();
      }

      @Override
      public boolean accept(@NotNull PsiElement highlightedElement) {
        return !LazyGetterHandler.isLazyGetterHandled(highlightedElement)
          || !LazyGetterHandler.isInitializedInConstructors(highlightedElement);
      }
    },

    REDUNDANT_DEFAULT_PARAMETER_VALUE_ASSIGNMENT(HighlightSeverity.WARNING, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES) {
      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return "Redundant default parameter value assignment".equals(description);
      }

      @Override
      public boolean accept(@NotNull PsiElement highlightedElement) {
        return !EqualsAndHashCodeCallSuperHandler.isEqualsAndHashCodeCallSuperDefault(highlightedElement);
      }
    };

    private final HighlightSeverity severity;
    private final TextAttributesKey key;

    LombokHighlightFilter(@NotNull HighlightSeverity severity, @Nullable TextAttributesKey key) {
      this.severity = severity;
      this.key = key;
    }

    abstract public boolean descriptionCheck(@Nullable String description);

    abstract public boolean accept(@NotNull PsiElement highlightedElement);
  }

  private boolean uninitializedField(String description) {
    return UNINITIALIZED_MESSAGE.matcher(description).matches();
  }
}
