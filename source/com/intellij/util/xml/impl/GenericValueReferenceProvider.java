/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class GenericValueReferenceProvider implements PsiReferenceProvider {
  @NotNull
  public GenericReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof XmlTag)) return GenericReference.EMPTY_ARRAY;
    PsiElement originalElement = element.getUserData(PsiUtil.ORIGINAL_KEY);
    if (originalElement != null){
      element = originalElement;
    }
    final Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module == null) return GenericReference.EMPTY_ARRAY;

    final XmlTag tag = (XmlTag)element;
    final DomElement domElement = DomManager.getDomManager(module.getProject()).getDomElement(tag);
    if (!(domElement instanceof GenericDomValue)) return GenericReference.EMPTY_ARRAY;

    final GenericReference reference = getReferenceByElement((GenericDomValue)domElement);
    return reference != null ? new GenericReference[]{reference} : GenericReference.EMPTY_ARRAY;
  }

  @Nullable
  protected abstract GenericReference getReferenceByElement(GenericDomValue value);

  @NotNull
  public GenericReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public GenericReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
