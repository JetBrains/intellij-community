package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.handler.EqualsAndHashCodeCallSuperHandler;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


public class LombokHighlightErrorFilter implements HighlightInfoFilter {
  private static final Pattern LOMBOK_ANY_ANNOTATION_REQUIRED = Pattern.compile("Incompatible types\\. Found: '__*', required: 'lombok.*AnyAnnotation\\[\\]'");

  private final Map<HighlightSeverity, Map<TextAttributesKey, List<LombokHighlightFilter>>> registeredFilters;
  private final Map<HighlightSeverity, Map<TextAttributesKey, List<LombokHighlightFixHook>>> registeredHooks;

  public LombokHighlightErrorFilter() {
    registeredFilters = new HashMap<>();
    registeredHooks = new HashMap<>();

    for (LombokHighlightFilter highlightFilter : LombokHighlightFilter.values()) {
      registeredFilters.computeIfAbsent(highlightFilter.severity, s -> new HashMap<>())
        .computeIfAbsent(highlightFilter.key, k -> new ArrayList<>())
        .add(highlightFilter);
    }

    for (LombokHighlightFixHook highlightFixHook : LombokHighlightFixHook.values()) {
      registeredHooks.computeIfAbsent(highlightFixHook.severity, s -> new HashMap<>())
        .computeIfAbsent(highlightFixHook.key, k -> new ArrayList<>())
        .add(highlightFixHook);
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

    // check exceptions for highlights
    boolean acceptHighlight = registeredFilters
      .getOrDefault(highlightInfo.getSeverity(), Collections.emptyMap())
      .getOrDefault(highlightInfo.type.getAttributesKey(), Collections.emptyList())
      .stream()
      .filter(filter -> filter.descriptionCheck(highlightInfo.getDescription()))
      .allMatch(filter -> filter.accept(highlightedElement));

    // check if highlight was filtered
    if (!acceptHighlight) {
      return false;
    }

    // handle rest cases
    String description = highlightInfo.getDescription();
    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      //Handling onX parameters
      if (OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)
        || OnXAnnotationHandler.isOnXParameterValue(highlightInfo, file)
        || (description != null && LOMBOK_ANY_ANNOTATION_REQUIRED.matcher(description).matches())) {
        return false;
      }
    }

    // register different quick fix for highlight
    registeredHooks
      .getOrDefault(highlightInfo.getSeverity(), Collections.emptyMap())
      .getOrDefault(highlightInfo.type.getAttributesKey(), Collections.emptyList())
      .stream()
      .filter(filter -> filter.descriptionCheck(highlightInfo.getDescription()))
      .forEach(filter -> filter.processHook(highlightedElement, highlightInfo));

    return true;
  }

  private enum LombokHighlightFixHook {

    UNHANDLED_EXCEPTION(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
      private final Pattern pattern = Pattern.compile("Unhandled exceptions?: .+");

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null && pattern.matcher(description).matches();
      }

      @Override
      public void processHook(@NotNull PsiElement highlightedElement, @NotNull HighlightInfo highlightInfo) {
        PsiElement importantParent = PsiTreeUtil.getParentOfType(highlightedElement,
          PsiMethod.class, PsiLambdaExpression.class, PsiMethodReferenceExpression.class, PsiClassInitializer.class
        );

        // applicable only for methods
        if (importantParent instanceof PsiMethod) {
          AddAnnotationFix fix = new AddAnnotationFix(SneakyThrows.class.getCanonicalName(), (PsiModifierListOwner) importantParent);
          highlightInfo.registerFix(fix, null, null, null, null);
        }
      }
    };

    private final HighlightSeverity severity;
    private final TextAttributesKey key;

    LombokHighlightFixHook(@NotNull HighlightSeverity severity, @Nullable TextAttributesKey key) {
      this.severity = severity;
      this.key = key;
    }

    abstract public boolean descriptionCheck(@Nullable String description);

    abstract public void processHook(@NotNull PsiElement highlightedElement, @NotNull HighlightInfo highlightInfo);
  }

  private enum LombokHighlightFilter {
    // ERROR HANDLERS

    VARIABLE_MIGHT_NOT_BEEN_INITIALIZED(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
      private final Pattern pattern = Pattern.compile("Variable '.+' might not have been initialized");

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null && pattern.matcher(description).matches();
      }

      @Override
      public boolean accept(@NotNull PsiElement highlightedElement) {
        return !LazyGetterHandler.isLazyGetterHandled(highlightedElement);
      }
    },

    // WARNINGS HANDLERS

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
}
