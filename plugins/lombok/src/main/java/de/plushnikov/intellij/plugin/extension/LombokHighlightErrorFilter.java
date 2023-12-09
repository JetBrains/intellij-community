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
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final class Holder {
    static final Map<HighlightSeverity, Map<TextAttributesKey, List<LombokHighlightFixHook>>> registeredHooks;

    static {
      registeredHooks = new HashMap<>();

      for (LombokHighlightFixHook highlightFixHook : LombokHighlightFixHook.values()) {
        registeredHooks.computeIfAbsent(highlightFixHook.severity, s -> new HashMap<>())
          .computeIfAbsent(highlightFixHook.key, k -> new ArrayList<>())
          .add(highlightFixHook);
      }
    }
  }

  public LombokHighlightErrorFilter() {
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

    // handle rest cases
    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      //Handling onX parameters
      if (OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)) {
        return false;
      }
    }

    // register different quick fix for highlight
    Holder.registeredHooks
      .getOrDefault(highlightInfo.getSeverity(), Collections.emptyMap())
      .getOrDefault(highlightInfo.type.getAttributesKey(), Collections.emptyList())
      .stream()
      .filter(filter -> filter.descriptionCheck(highlightInfo.getDescription()))
      .forEach(filter -> filter.processHook(highlightedElement, highlightInfo));

    return true;
  }

  private enum LombokHighlightFixHook {

    UNHANDLED_EXCEPTION(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES) {
      private final Pattern pattern = preparePattern(1);
      private final Pattern pattern2 = preparePattern(2);

      @NotNull
      private static Pattern preparePattern(int count) {
        return Pattern.compile(JavaErrorBundle.message("unhandled.exceptions", ".*", count));
      }

      @Override
      public boolean descriptionCheck(@Nullable String description) {
        return description != null && (pattern.matcher(description).matches() || pattern2.matcher(description).matches());
      }

      @Override
      public void processHook(@NotNull PsiElement highlightedElement, @NotNull HighlightInfo highlightInfo) {
        PsiElement importantParent = PsiTreeUtil.getParentOfType(highlightedElement,
                                                                 PsiMethod.class, PsiLambdaExpression.class,
                                                                 PsiMethodReferenceExpression.class, PsiClassInitializer.class
        );

        // applicable only for methods
        if (importantParent instanceof PsiMethod) {
          AddAnnotationFix fix =
            PsiQuickFixFactory.createAddAnnotationFix(LombokClassNames.SNEAKY_THROWS, (PsiModifierListOwner)importantParent);
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
}
