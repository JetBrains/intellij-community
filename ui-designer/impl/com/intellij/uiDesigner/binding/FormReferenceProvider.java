package com.intellij.uiDesigner.binding;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.10.2005
 * Time: 12:36:27
 * To change this template use File | Settings | File Templates.
 */
public class FormReferenceProvider implements PsiReferenceProvider, ProjectComponent {
  public FormReferenceProvider(ReferenceProvidersRegistry registry) {
    registry.registerReferenceProvider(PsiPlainTextFile.class, this);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (element instanceof PsiPlainTextFile) {
      PsiPlainTextFile plainTextFile = (PsiPlainTextFile) element;
      return ReferenceUtil.getReferences(plainTextFile);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "FormReferenceProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
