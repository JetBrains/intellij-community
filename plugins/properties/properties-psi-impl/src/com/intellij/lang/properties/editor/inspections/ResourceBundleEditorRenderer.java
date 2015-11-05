/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper;
import com.intellij.lang.properties.editor.ResourceBundlePropertyStructureViewElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorRenderer extends NodeRenderer {

  @Override
  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
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
        final SimpleTextAttributes attr =
          SimpleTextAttributes.fromTextAttributes(textAttributesPresentation.getTextAttributes(getColorsScheme()));
        append(text, new SimpleTextAttributes(attr.getBgColor(), attr.getFgColor(), attr.getWaveColor(),
                                              attr.getStyle() | SimpleTextAttributes.STYLE_OPAQUE));
        return true;
      }
    }
    return false;
  }

  public interface TextAttributesPresentation extends ItemPresentation {
    TextAttributes getTextAttributes(EditorColorsScheme colorsScheme);
  }
}
