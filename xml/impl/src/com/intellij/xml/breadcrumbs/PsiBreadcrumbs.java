/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;

import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * @author Sergey.Malenkov
 */
final class PsiBreadcrumbs extends Breadcrumbs {
  final boolean above = Registry.is("editor.breadcrumbs.above");

  void updateBorder(int offset) {
    // do not use scaling here because this border is used to align breadcrumbs with a gutter
    setBorder(new EmptyBorder(above ? 2 : 0, offset, above ? 0 : 2, 0));
  }

  @Override
  protected void paint(Graphics2D g, int x, int y, int width, int height, Crumb crumb, int thickness) {
    super.paint(g, x, y, width, above ? height : thickness, crumb, thickness);
  }

  @Override
  public void setFont(Font font) {
    super.setFont(Registry.is("editor.breadcrumbs.small")
                  ? RelativeFont.SMALL.derive(font)
                  : font);
  }

  @Override
  public Color getForeground() {
    if (!isForegroundSet()) {
      Color foreground = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
      if (foreground != null) return foreground;
    }
    return super.getForeground();
  }

  @Override
  public Color getBackground() {
    if (!isBackgroundSet()) {
      Color background = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
      if (background != null) return background;
    }
    return super.getBackground();
  }

  @Override
  protected Color getForeground(Crumb crumb) {
    CrumbPresentation presentation = PsiCrumb.getPresentation(crumb);
    if (presentation == null) return super.getForeground(crumb);

    Color background = super.getBackground(crumb);
    if (background != null) return super.getForeground(crumb);

    return presentation.getBackgroundColor(isSelected(crumb), isHovered(crumb), isAfterSelected(crumb));
  }

  @Override
  protected Color getBackground(Crumb crumb) {
    CrumbPresentation presentation = PsiCrumb.getPresentation(crumb);
    if (presentation == null) return super.getBackground(crumb);

    Color background = super.getBackground(crumb);
    if (background == null) return null;

    return presentation.getBackgroundColor(isSelected(crumb), isHovered(crumb), isAfterSelected(crumb));
  }
}
