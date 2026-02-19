// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TestNGEntryPoint extends EntryPoint {
   public boolean ADD_TESTNG_TO_ENTRIES = true;

  @Override
  public boolean isSelected() {
    return ADD_TESTNG_TO_ENTRIES;
  }

  @Override
  public void setSelected(boolean selected) {
    ADD_TESTNG_TO_ENTRIES = selected;
  }

  @Override
  public @NotNull String getDisplayName() {
    return TestngBundle.message("testng.entry.point.test.cases");
  }

  @Override
  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiModifierListOwner) {
      if (TestNGUtil.hasTest((PsiModifierListOwner)psiElement, true, false, TestNGUtil.hasDocTagsSupport)) return true;
      return TestNGUtil.hasConfig((PsiModifierListOwner)psiElement);
    }
    return false;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (!ADD_TESTNG_TO_ENTRIES) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  @Override
  public String @Nullable [] getIgnoreAnnotations() {
    return TestNGUtil.CONFIG_ANNOTATIONS_FQN;
  }
}