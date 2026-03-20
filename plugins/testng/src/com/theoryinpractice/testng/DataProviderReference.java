// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.theoryinpractice.testng.util.TestNGUtil.DATA_PROVIDER_ANNOTATION_FQN;
import static com.theoryinpractice.testng.util.TestNGUtil.getAttributeValue;

public class DataProviderReference extends PsiReferenceBase<PsiLiteral> implements PsiMemberReference, PsiPolyVariantReference {

  public DataProviderReference(PsiLiteral element) {
    super(element, false);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMethod method) {
      return handleElementRename(method.getName());
    }
    return super.bindToElement(element);
  }

  @Override
  public @Nullable PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    ResolveResult[] results = multiResolve(false);
    return ContainerUtil.exists(results, r -> element == r.getElement());
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiFile file = getElement().getContainingFile();
    if (file == null) return ResolveResult.EMPTY_ARRAY;
    return ResolveCache.getInstance(file.getProject())
      .resolveWithCaching(this, new OurGenericsResolver(getValue()), false, incompleteCode, file);
  }

  @Override
  public Object @NotNull [] getVariants() {
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(getElement());
    final PsiClass[] classes = TestNGUtil.getProviderClasses(getElement(), topLevelClass);
    if (classes.length == 0) return EMPTY_ARRAY;
    final Set<LookupElementBuilder> result = new LinkedHashSet<>();

    final boolean needToBeStatic = classes[0] != topLevelClass;
    final PsiMethod current = PsiTreeUtil.getParentOfType(getElement(), PsiMethod.class);
    for (PsiClass cls : classes) {
      final PsiMethod[] methods = cls.getAllMethods();
      for (PsiMethod method : methods) {
        if (current != null && method.getName().equals(current.getName())) continue;
        if (needToBeStatic && !method.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (!needToBeStatic && cls != method.getContainingClass() && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;

        final PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, DATA_PROVIDER_ANNOTATION_FQN);
        if (dataProviderAnnotation == null) continue;
        String value = getAttributeValue(dataProviderAnnotation, "name");
        result.add(LookupElementBuilder.create(value != null ? value : method.getName()));
      }
    }
    return result.toArray();
  }

  private static class OurGenericsResolver implements ResolveCache.PolyVariantResolver<DataProviderReference> {
    @NotNull @NlsSafe private final String myValue;

    private OurGenericsResolver(@NotNull @NlsSafe String value) {
      myValue = value;
    }

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull DataProviderReference reference, boolean incompleteCode) {
      PsiLiteral element = reference.getElement();
      final PsiClass[] classes = TestNGUtil.getProviderClasses(element, PsiUtil.getTopLevelClass(element));
      final Set<PsiMethod> result = new HashSet<>();

      for (PsiClass cls : classes) {
        PsiMethod[] methods = cls.getAllMethods();
        for (PsiMethod method : methods) {
          PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, DATA_PROVIDER_ANNOTATION_FQN);
          if (dataProviderAnnotation == null) continue;
          if (myValue.equals(method.getName()) || myValue.equals(getAttributeValue(dataProviderAnnotation, "name"))) {
            result.add(method);
          }
        }
      }
      return result.stream().map(PsiElementResolveResult::new).toArray(ResolveResult[]::new);
    }
  }
}
