package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * A completion contributor filter for Lombok onX-Annotations, and they default onX-Methods without underscore (_) at the end.
 * These default methods can/should be used only for old JDKs like 1.7 From 1.8 synthetic underscored methods should be used.
 * @see <a href="https://projectlombok.org/features/experimental/onX">Lombok onX-Documentation</a>
 */
public class LombokOnXCompletionContributorFilter extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (PsiUtil.getLanguageLevel(parameters.getPosition().getProject()).isLessThan(LanguageLevel.JDK_1_8)) {
      return;
    }

    result.runRemainingContributors(parameters, completionResult -> {
      LookupElement lookupElement = completionResult.getLookupElement();
      if (shouldKeepItem(lookupElement)) {
        result.passResult(completionResult);
      }
    });
  }

  private static boolean shouldKeepItem(LookupElement item) {
    if (ONX_PARAMETERS.contains(item.getLookupString())) {
      if (item.getPsiElement() instanceof PsiMethod psiMethod) {
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (null != containingClass && containingClass.isAnnotationType()) {
          if (ONXABLE_ANNOTATION_NAMES.contains(containingClass.getName())) {
            return !ONXABLE_ANNOTATION_FQNS.contains(containingClass.getQualifiedName());
          }
        }
      }
    }
    return true;
  }

  private static final Collection<String> ONX_PARAMETERS = Arrays.asList(
    "onConstructor",
    "onMethod",
    "onParam"
  );

  private static final Collection<String> ONXABLE_ANNOTATION_FQNS = Arrays.asList(
    LombokClassNames.GETTER,
    LombokClassNames.SETTER,
    LombokClassNames.WITH,
    LombokClassNames.WITHER,
    LombokClassNames.NO_ARGS_CONSTRUCTOR,
    LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
    LombokClassNames.ALL_ARGS_CONSTRUCTOR,
    LombokClassNames.EQUALS_AND_HASHCODE
  );
  private static final Collection<String> ONXABLE_ANNOTATION_NAMES = ContainerUtil.map(ONXABLE_ANNOTATION_FQNS, StringUtil::getShortName);
}
