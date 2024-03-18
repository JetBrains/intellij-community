// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public final class PyUserSkeletonsLineMarkerProvider implements LineMarkerProvider {
  // TODO: Create an icon for a related user skeleton
  public static final Icon ICON = AllIcons.Gutter.Unique;

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    for (PsiElement element : elements) {
      final PyElement skeleton = getUserSkeleton(element);
      if (skeleton != null) {
        PsiElement identifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
        if (identifier != null) {
          result.add(new LineMarkerInfo<>(identifier, identifier.getTextRange(), ICON, __ -> "Has user skeleton", (__, elt) -> {
                        final PyElement s = getUserSkeleton(elt.getParent());
                        if (s != null) {
                          PsiNavigateUtil.navigate(s);
                        }
                      }, GutterIconRenderer.Alignment.RIGHT));
        }
      }
    }
  }

  @Nullable
  private static PyElement getUserSkeleton(@NotNull PsiElement element) {
    if (element instanceof PyFunction || element instanceof PyTargetExpression) {
      return PyUserSkeletonsUtil.getUserSkeleton((PyElement)element);
    }
    return null;
  }
}
