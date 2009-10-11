/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class XmlNSRenderer extends ColoredListCellRenderer {

  public static final XmlNSRenderer INSTANCE = new XmlNSRenderer();
  
  private static final Icon ICON = IconLoader.getIcon("/nodes/static.png");

  public XmlNSRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    setFont(new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize()));
  }

  protected void customizeCellRenderer(final JList list,
                                       final Object value,
                                       final int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    setIcon(ICON);
    setPaintFocusBorder(false);
  }
}
