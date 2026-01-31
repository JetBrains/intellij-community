package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractAddLombokBuilderMethodsAction extends AbstractLombokIntentionAction {

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    final Presentation presentation = super.getPresentation(context, element);
    if (presentation == null) return null;

    final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getTopmostParentOfType(element, PsiMethodCallExpression.class);
    final Collection<String> missingLombokBuilderMethods = findMissingBuilderMethods(methodCallExpression);
    if (!missingLombokBuilderMethods.isEmpty()) {
      return presentation;
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiMethodCallExpression psiMethodCallExpression = PsiTreeUtil.getTopmostParentOfType(element, PsiMethodCallExpression.class);

    final Pair<PsiAnnotation, PsiNamedElement> builderAnnotationPair = findBuilderAnnotationPair(psiMethodCallExpression);
    final Collection<String> missingLombokBuilderMethods = findRemainingBuilderMethods(psiMethodCallExpression, builderAnnotationPair);
    if (!missingLombokBuilderMethods.isEmpty() && null != builderAnnotationPair) {
      final String expressionText = psiMethodCallExpression.getText();
      final String buildMethodName = BuilderHandler.getBuildMethodName(builderAnnotationPair.getFirst());

      final String missingMethodsChain = missingLombokBuilderMethods.stream()
        .collect(Collectors.joining("().", ".", "()." + buildMethodName + "()"));
      final String replacedExpressionText;
      if (expressionText.contains("." + buildMethodName + "()")) {
        replacedExpressionText = replaceLast(expressionText, "." + buildMethodName + "()", missingMethodsChain);
      }
      else {
        replacedExpressionText = expressionText + missingMethodsChain;
      }

      final PsiElementFactory factory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
      final PsiExpression fixedMethodExpression = factory.createExpressionFromText(replacedExpressionText, element.getContext());
      psiMethodCallExpression.replace(fixedMethodExpression);
    }
  }

  private @NotNull Collection<String> findMissingBuilderMethods(@Nullable PsiMethodCallExpression methodCallExpression) {
    final @Nullable Pair<PsiAnnotation, PsiNamedElement> elementPair = findBuilderAnnotationPair(methodCallExpression);
    return findRemainingBuilderMethods(methodCallExpression, elementPair);
  }

  private @NotNull Collection<String> findRemainingBuilderMethods(@Nullable PsiMethodCallExpression psiMethodCallExpression,
                                                                  @Nullable Pair<PsiAnnotation, PsiNamedElement> builderAnnotationPair) {
    if (null != builderAnnotationPair) {
      final List<String> remainingBuilderMethods = getBuilderMethodNames(builderAnnotationPair);
      final List<String> existingMethodCalls = BuilderHelper.getAllMethodsInChainFromMiddle(psiMethodCallExpression);
      remainingBuilderMethods.removeAll(existingMethodCalls);
      return remainingBuilderMethods;
    }
    return Collections.emptyList();
  }

  private static @Nullable Pair<PsiAnnotation, PsiNamedElement> findBuilderAnnotationPair(@Nullable PsiMethodCallExpression psiMethodCallExpression) {
    if (null != psiMethodCallExpression) {
      final PsiMethod resolvedMethod = psiMethodCallExpression.resolveMethod();
      if (resolvedMethod != null) {
        return BuilderHelper.findBuilderAnnotation(resolvedMethod);
      }
    }
    return null;
  }

  abstract List<String> getBuilderMethodNames(@NotNull Pair<PsiAnnotation, PsiNamedElement> elementPair);

  private static String replaceLast(String input, String oldStr, String newStr) {
    int lastIndex = input.lastIndexOf(oldStr);
    if (lastIndex == -1) {
      return input;
    }
    return input.substring(0, lastIndex) + newStr + input.substring(lastIndex + oldStr.length());
  }
}