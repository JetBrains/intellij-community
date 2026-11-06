// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.completion;

import com.intellij.java.completion.modcommand.AnnotationAttributeItemProvider;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemFilter;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * A completion contributor filter for Lombok onX-Annotations, and they default onX-Methods without underscore (_) at the end.
 * These default methods can/should be used only for old JDKs like 1.7 From 1.8 synthetic underscored methods should be used.
 * @see <a href="https://projectlombok.org/features/experimental/onX">Lombok onX-Documentation</a>
 */
public final class LombokOnXCompletionContributorFilter implements ModCompletionItemFilter {
  @Override
  public @Nullable Predicate<ModCompletionItem> createFilter(@NotNull ModCompletionItemProvider provider,
                                                             ModCompletionItemProvider.@NotNull CompletionContext context) {
    if (!(provider instanceof AnnotationAttributeItemProvider) ||
        PsiUtil.getLanguageLevel(context.getPosition().getProject()).isLessThan(LanguageLevel.JDK_1_8)) {
      return null;
    }
    return LombokOnXCompletionContributorFilter::shouldKeepItem;
  }

  private static boolean shouldKeepItem(ModCompletionItem item) {
    if (ONX_PARAMETERS.contains(item.mainLookupString())) {
      if (item.contextObject() instanceof PsiMethod psiMethod) {
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
