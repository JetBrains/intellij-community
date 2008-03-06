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
