// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.pyi;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public final class PyiRelatedItemLineMarkerProvider extends RelatedItemLineMarkerProvider {
  // TODO: Create an icon for a related Python stub item
  public static final Icon ICON = AllIcons.Gutter.Unique;

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (element instanceof PyFunction || element instanceof PyTargetExpression || element instanceof PyClass) {
      final PsiElement pythonStub = PyiUtil.getPythonStub((PyElement)element);
      if (pythonStub != null) {
        PsiElement identifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
        if (identifier != null) {
          result.add(createLineMarkerInfo(identifier, pythonStub, "Has stub item"));
        }
      }
      final PsiElement originalElement = PyiUtil.getOriginalElement((PyElement)element);
      if (originalElement != null) {
        PsiElement identifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
        if (identifier != null) {
          result.add(createLineMarkerInfo(identifier, originalElement, "Stub for item"));
        }
      }
    }
  }

  @NotNull
  private static RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element,
                                                                            @NotNull PsiElement relatedElement,
                                                                            @NotNull String itemTitle) {
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(element.getProject());
    final SmartPsiElementPointer<PsiElement> relatedElementPointer = pointerManager.createSmartPsiElementPointer(relatedElement);
    final String stubFileName = relatedElement.getContainingFile().getName();
    return new RelatedItemLineMarkerInfo<>(
      element, element.getTextRange(), ICON,
      element1 -> itemTitle + " in " + stubFileName, (e, elt) -> {
        final PsiElement restoredRelatedElement = relatedElementPointer.getElement();
        if (restoredRelatedElement == null) {
          return;
        }
        final int offset = restoredRelatedElement instanceof PsiFile ? -1 : restoredRelatedElement.getTextOffset();
        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(restoredRelatedElement);
        if (virtualFile != null && virtualFile.isValid()) {
          PsiNavigationSupport.getInstance()
                              .createNavigatable(restoredRelatedElement.getProject(), virtualFile, offset)
                              .navigate(true);
        }
      }, GutterIconRenderer.Alignment.RIGHT, ()->GotoRelatedItem.createItems(Collections.singletonList(relatedElement)));
  }
}
