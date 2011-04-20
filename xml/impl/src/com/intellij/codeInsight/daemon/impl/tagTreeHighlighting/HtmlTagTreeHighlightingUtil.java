/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.xml.util.HtmlUtil;

import java.awt.*;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
class HtmlTagTreeHighlightingUtil {
  private HtmlTagTreeHighlightingUtil() {
  }

  static boolean containsParentTagsWithSameName(PsiElement element) {
    final Set<String> names = new HashSet<String>();

    while (element != null) {
      if (element instanceof XmlTag) {
        final String name = ((XmlTag)element).getName();
        if (!names.add(name)) {
          return true;
        }
      }
      element = element.getParent();
    }

    return false;
  }

  static boolean isTagTreeHighlightingActive(PsiFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }

    if (!(file instanceof XmlFile) || !HtmlUtil.hasHtml(file)) {
      return false;
    }

    if (!WebEditorOptions.getInstance().isTagTreeHighlightingEnabled()) {
      return false;
    }
    return true;
  }

  static Color makeTransparent(Color color, Color backgroundColor, double transparency) {
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
    final ColorKey[] colorKeys = HtmlTagTreeHighlightingColors.getColorKeys();
    final Color[] colors = new Color[colorKeys.length];

    final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    for (int i = 0; i < colors.length; i++) {
      colors[i] = colorsScheme.getColor(colorKeys[i]);
    }

    return colors;
  }
}
