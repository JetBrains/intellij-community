// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public final class XmlTagTreeHighlightingUtil {
  private XmlTagTreeHighlightingUtil() {
  }

  @ApiStatus.Internal
  public static boolean containsTagsWithSameName(PsiElement[] elements) {
    final Set<String> names = new HashSet<>();

    for (PsiElement element : elements) {
      if (element instanceof XmlTag) {
        final String name = ((XmlTag)element).getName();
        if (!names.add(name)) {
          return true;
        }
      }
    }

    return false;
  }

  @ApiStatus.Internal
  public static boolean isTagTreeHighlightingActive(PsiFile psiFile) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }

    if (!hasXmlViewProvider(psiFile) && !HtmlUtil.supportsXmlTypedHandlers(psiFile)) {
      return false;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return false;
    }
    return true;
  }

  public static boolean hasXmlViewProvider(@NotNull PsiFile psiFile) {
    for (PsiFile f : psiFile.getViewProvider().getAllFiles()) {
      if (f instanceof XmlFile) {
        return true;
      }
    }
    return false;
  }

  @ApiStatus.Internal
  public static Color[] getBaseColors() {
    final ColorKey[] colorKeys = XmlTagTreeHighlightingColors.getColorKeys();
    final Color[] colors = new Color[colorKeys.length];

    final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    for (int i = 0; i < colors.length; i++) {
      colors[i] = colorsScheme.getColor(colorKeys[i]);
    }

    return colors;
  }
}
