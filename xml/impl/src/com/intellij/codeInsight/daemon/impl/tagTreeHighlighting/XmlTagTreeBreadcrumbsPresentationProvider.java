// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.breadcrumbs.BreadcrumbsPresentationProvider;
import com.intellij.xml.breadcrumbs.CrumbPresentation;
import com.intellij.xml.breadcrumbs.DefaultCrumbsPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class XmlTagTreeBreadcrumbsPresentationProvider extends BreadcrumbsPresentationProvider {
  private static boolean isMyContext(@NotNull PsiElement deepestElement) {
    final PsiFile psiFile = deepestElement.getContainingFile();
    if (psiFile == null || !XmlTagTreeHighlightingUtil.isTagTreeHighlightingActive(psiFile)) {
      return false;
    }
    return true;
  }

  @Override
  public CrumbPresentation[] getCrumbPresentations(PsiElement @NotNull [] elements) {
    if (elements.length == 0 || !isMyContext(elements[elements.length - 1])) {
      return null;
    }

    if (!XmlTagTreeHighlightingUtil.containsTagsWithSameName(elements)) {
      return null;
    }

    final CrumbPresentation[] result = new CrumbPresentation[elements.length];
    final Color[] baseColors = XmlTagTreeHighlightingUtil.getBaseColors();
    int index = 0;

    for (int i = result.length - 1; i >= 0; i--) {
      if (elements[i] instanceof XmlTag) {
        final Color color = baseColors[index % baseColors.length];
        result[i] = new MyCrumbPresentation(color);
        index++;
      }
    }
    return result;
  }

  private static final class MyCrumbPresentation extends DefaultCrumbsPresentation {
    private final Color myColor;

    private MyCrumbPresentation(@Nullable Color color) {
      myColor = color;
    }

    @Override
    public Color getBackgroundColor(boolean selected, boolean hovered, boolean light) {
      final Color baseColor = super.getBackgroundColor(selected, hovered, light);
      return baseColor == null
             ? XmlTagTreeHighlightingPass.toLineMarkerColor(0x92, myColor)
             : myColor != null
               ? UIUtil.makeTransparent(myColor, baseColor, 0.1)
               : baseColor;
    }
  }
}
