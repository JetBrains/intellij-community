// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class XmlNSRenderer extends ColoredListCellRenderer<String> {

  public static final XmlNSRenderer INSTANCE = new XmlNSRenderer();

  public XmlNSRenderer() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    setFont(scheme.getFont(EditorFontType.PLAIN));
  }

  @Override
  protected void customizeCellRenderer(final @NotNull JList<? extends String> list,
                                       final @NlsSafe String value,
                                       final int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    setIcon(AllIcons.Nodes.Static);
    setPaintFocusBorder(false);
  }
}
