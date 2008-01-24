package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface SafeDeleteProcessorDelegate {
  ExtensionPointName<SafeDeleteProcessorDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.safeDeleteProcessor");

  boolean handlesElement(PsiElement element);
  @Nullable
  Condition<PsiElement> findUsages(final PsiElement element, final PsiElement[] allElementsToDelete, List<UsageInfo> result);
}
