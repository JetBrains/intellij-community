package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class BuilderHelper {
  public static @NotNull String renderChainedMethods(Collection<String> remainingBuilderMethods, @NotNull PsiAnnotation psiAnnotation) {
    final StringBuilder chainedBuilderMethodCalls = new StringBuilder();
    StringUtil.join(remainingBuilderMethods, "().", chainedBuilderMethodCalls);
    if (!chainedBuilderMethodCalls.isEmpty()) {
      chainedBuilderMethodCalls.append("().");
    }
    chainedBuilderMethodCalls.append(BuilderHandler.getBuildMethodName(psiAnnotation)).append("()");
    return chainedBuilderMethodCalls.toString();
  }

  public static List<String> getAllBuilderMethodNames(@NotNull PsiModifierListOwner psiModifierListOwner,
                                                      @NotNull PsiAnnotation psiAnnotation,
                                                      @NotNull Predicate<BuilderInfo> filter) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiModifierListOwner, PsiClass.class, false);
    if (null == containingClass) {
      return Collections.emptyList();
    }

    final PsiMethod psiMethod = psiModifierListOwner instanceof PsiMethod psiMethod1 ? psiMethod1 : null;

    final String builderClassName = BuilderHandler.getBuilderClassName(containingClass, psiAnnotation, psiMethod);
    final PsiClass builderClass = containingClass.findInnerClassByName(builderClassName, false);
    if (null == builderClass) {
      return Collections.emptyList();
    }

    List<String> result = getBuilderMethodNames(psiAnnotation, filter, containingClass, psiMethod, builderClass);

    if (psiAnnotation.hasQualifiedName(LombokClassNames.SUPER_BUILDER)) {
      PsiClass superClass = containingClass.getSuperClass();
      while (null != superClass && !CommonClassNames.JAVA_LANG_CLASS.equals(superClass.getQualifiedName())) {
        List<String> nextResult = getBuilderMethodNames(psiAnnotation, filter, superClass, psiMethod, builderClass);
        result.addAll(nextResult);
        superClass = superClass.getSuperClass();
      }
    }

    return result;
  }

  private static @NotNull List<String> getBuilderMethodNames(@NotNull PsiAnnotation psiAnnotation,
                                                             @NotNull Predicate<BuilderInfo> filter,
                                                             @NotNull PsiClass containingClass,
                                                             @Nullable PsiMethod psiMethod,
                                                             @NotNull PsiClass builderClass) {
    final List<BuilderInfo> builderInfos = BuilderHandler.createBuilderInfos(psiAnnotation, containingClass, psiMethod, builderClass);
    return builderInfos.stream()
      .filter(filter)
      .map(BuilderInfo::renderBuilderMethodName)
      .collect(Collectors.toList());
  }

  public static @Nullable Pair<PsiAnnotation, PsiNamedElement> findBuilderAnnotation(@NotNull PsiMethod psiMethod) {
    PsiClass psiClass = psiMethod.getContainingClass();
    while (psiClass != null) {
      if (!(psiClass instanceof LombokLightClassBuilder)) {
        // check for annotation on class
        final PsiAnnotation psiAnnotation = getBuilderAnnotation(psiClass);
        if (null != psiAnnotation) {
          return Pair.pair(psiAnnotation, psiClass);
        }

        // check for annotation on a method
        final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
        for (PsiMethod method : psiMethods) {
          final PsiAnnotation psiMethodAnnotation = getBuilderAnnotation(method);
          if (null != psiMethodAnnotation) {
            return Pair.pair(psiMethodAnnotation, method);
          }
        }
      }
      psiClass = psiClass.getContainingClass();
    }
    return null;
  }

  public static @Nullable PsiAnnotation getBuilderAnnotation(@NotNull PsiModifierListOwner psiModifierListOwner) {
    return PsiAnnotationSearchUtil.findAnnotation(psiModifierListOwner, LombokClassNames.BUILDER, LombokClassNames.SUPER_BUILDER);
  }

  public static List<String> getAllMethodsInChainFromMiddle(@Nullable PsiMethodCallExpression methodCallExpression) {
    List<String> backwardMethods = new ArrayList<>();
    PsiMethodCallExpression currentCall = methodCallExpression;

    while (currentCall != null) {
      PsiReferenceExpression methodExpression = currentCall.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (methodName != null) {
        backwardMethods.add(methodName);
      }

      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier instanceof PsiMethodCallExpression) {
        currentCall = (PsiMethodCallExpression)qualifier;
      }
      else {
        break;
      }
    }

    Collections.reverse(backwardMethods);

    if (methodCallExpression == null) {
      return backwardMethods;
    }

    List<String> forwardMethods = new ArrayList<>();
    PsiElement parent = methodCallExpression.getParent();

    while (parent instanceof PsiReferenceExpression &&
           parent.getParent() instanceof PsiMethodCallExpression parentCall) {
      String methodName = ((PsiReferenceExpression)parent).getReferenceName();
      if (methodName != null) {
        forwardMethods.add(methodName);
      }

      parent = parentCall.getParent();
    }

    List<String> allMethods = new ArrayList<>(backwardMethods);
    allMethods.addAll(forwardMethods);

    return allMethods;
  }
}
