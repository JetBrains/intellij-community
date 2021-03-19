package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.handler.*;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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

    Project project = file.getProject();
    if (!LombokLibraryUtil.hasLombokLibrary(project)) {
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
          AddAnnotationFix fix = new AddAnnotationFix(LombokClassNames.SNEAKY_THROWS, (PsiModifierListOwner) importantParent);
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

    CONSTANT_EXPRESSION_REQUIRED(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return JavaErrorBundle.message("constant.expression.required").equals(description);
      }

      @Override
      public boolean accept(@NotNull PsiElement highlightedElement) {
        return !FieldNameConstantsHandler.isFiledNameConstants(highlightedElement);
      }
    },

    // WARNINGS HANDLERS

    VARIABLE_INITIALIZER_IS_REDUNDANT(HighlightSeverity.WARNING, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES) {
      private final Pattern pattern = Pattern.compile("Variable '.+' initializer '.+' is redundant");

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null && pattern.matcher(description).matches();
      }

      @Override
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
    },

    /**
     * Handles warnings that are related to Builder.Default cause.
     * The final fields that are marked with Builder.Default contains only possible value because user can set another value during the creation of the object.
     */
    CONSTANT_CONDITIONS_DEFAULT_BUILDER_CAN_BE_SIMPLIFIED(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES) {
      private final Pattern patternCanBeSimplified = Pattern.compile("'.+' can be simplified to '.+'");
      private final Pattern patternIsAlways = Pattern.compile("Condition '.+' is always '(true|false)'");

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null
          && (patternCanBeSimplified.matcher(description).matches() || patternIsAlways.matcher(description).matches());
      }

      @Override
      public boolean accept(@NotNull PsiElement highlightedElement) {
        PsiReferenceExpression parent = PsiTreeUtil.getParentOfType(highlightedElement, PsiReferenceExpression.class);
        if (parent == null) {
          return true;
        }

        PsiElement resolve = parent.resolve();
        if (!(resolve instanceof PsiField)) {
          return true;
        }

        return !PsiAnnotationSearchUtil.isAnnotatedWith((PsiField) resolve, LombokClassNames.BUILDER_DEFAULT);
      }
    };

    private final HighlightSeverity severity;
    private final TextAttributesKey key;

    LombokHighlightFilter(@NotNull HighlightSeverity severity, @Nullable TextAttributesKey key) {
      this.severity = severity;
      this.key = key;
    }

    /**
     * @param description of the current highlighted element
     * @return true if the filter can handle current type of the highlight info with that kind of the description
     */
    abstract public boolean descriptionCheck(@Nullable String description);

    /**
     * @param highlightedElement the deepest element (it's the leaf element in PSI tree where the highlight was occurred)
     * @return false if the highlight should be suppressed
     */
    abstract public boolean accept(@NotNull PsiElement highlightedElement);
  }
}
