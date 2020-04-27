// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor.inspections;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper;
import com.intellij.lang.properties.editor.TextAttributesPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorRenderer extends NodeRenderer {

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (customize(value)) {
      return;
    }
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
  }

  private boolean customize(Object value) {
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (!(userObject instanceof TreeElementWrapper)) {
      return false;
    }
    final TreeElement treeElement = ((TreeElementWrapper)userObject).getValue();
    if (treeElement == null) {
      return false;
    }
    final ItemPresentation presentation = treeElement.getPresentation();
    if (presentation instanceof TextAttributesPresentation) {
      final TextAttributesPresentation textAttributesPresentation = (TextAttributesPresentation)presentation;
      final String text = textAttributesPresentation.getPresentableText();
      if (text != null) {
        SimpleTextAttributes attr = SimpleTextAttributes.fromTextAttributes(
          textAttributesPresentation.getTextAttributes(
            EditorColorsManager.getInstance().getSchemeForCurrentUITheme()));
        append(text, new SimpleTextAttributes(attr.getBgColor(), attr.getFgColor(), attr.getWaveColor(),
                                              attr.getStyle() | SimpleTextAttributes.STYLE_OPAQUE));
        return true;
      }
    }
    return false;
  }
}
