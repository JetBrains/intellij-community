/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlTagTreeHighlightingUtil {
  private XmlTagTreeHighlightingUtil() {
  }

  static boolean containsTagsWithSameName(PsiElement[] elements) {
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

  static boolean isTagTreeHighlightingActive(PsiFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }

    if (!hasXmlViewProvider(file)) {
      return false;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return false;
    }
    return true;
  }

  private static boolean hasXmlViewProvider(@NotNull PsiFile file) {
    for (PsiFile f : file.getViewProvider().getAllFiles()) {
      if (f instanceof XmlFile) {
        return true;
      }
    }
    return false;
  }

  public static Color makeTransparent(@NotNull Color color, @NotNull Color backgroundColor, double transparency) {
    int r = makeTransparent(transparency, color.getRed(), backgroundColor.getRed());
    int g = makeTransparent(transparency, color.getGreen(), backgroundColor.getGreen());
    int b = makeTransparent(transparency, color.getBlue(), backgroundColor.getBlue());

    return new Color(r, g, b);
  }

  private static int makeTransparent(double transparency, int channel, int backgroundChannel) {
    final int result = (int)(backgroundChannel * (1 - transparency) + channel * transparency);
    if (result < 0) {
      return 0;
    }
    if (result > 255) {
      return 255;
    }
    return result;
  }

  static Color[] getBaseColors() {
    final ColorKey[] colorKeys = XmlTagTreeHighlightingColors.getColorKeys();
    final Color[] colors = new Color[colorKeys.length];

    final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    for (int i = 0; i < colors.length; i++) {
      colors[i] = colorsScheme.getColor(colorKeys[i]);
    }

    return colors;
  }
}
