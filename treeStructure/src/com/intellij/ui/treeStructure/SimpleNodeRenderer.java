/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class SimpleNodeRenderer extends NodeRenderer {

  public void customizeCellRenderer(JTree tree, Object value, boolean selected,
                                    boolean expanded, boolean leaf, int row, boolean hasFocus) {

    mySelected = selected;
    myFocused = hasFocus;

    if (selected) {
      setPaintFocusBorder(true);
      if (hasFocus) {
        setBackground(UIManager.getColor("Tree.selectionBackground"));
      } else {
        setBackground(tree.getBackground());
      }
    } else {
      setBackground(tree.getBackground());
    }

    Color color = null;
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor) userObject;
        if (expanded) {
          setIcon(descriptor.getOpenIcon());
        } else {
          setIcon(descriptor.getClosedIcon());
        }
        color = descriptor.getColor();
      }

      if (userObject instanceof SimpleNode) {
        renderNodeText(((SimpleNode) userObject), this);
        return;
      }
    }
    String text = tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
    if (text == null) text = "";
    append(text, new SimpleTextAttributes(Font.PLAIN, color));
  }

  public void renderNodeText(SimpleNode simpleNode, SimpleColoredComponent component) {
    if (simpleNode.getFont() != null) {
      component.setFont(simpleNode.getFont());
    } else {
      component.setFont(UIManager.getFont("Label.font"));
    }

    if (component.getFont() == null) {
      component.setFont(new JLabel().getFont());
    }

    final SimpleNode.ColoredFragment[] fragments = simpleNode.getColoredText();
    for (int i = 0; i < fragments.length; i++) {
      SimpleNode.ColoredFragment each = fragments[i];
      component.append(each.getText(), each.getAttributes());
      setToolTipText(each.getToolTip());
    }
  }

  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
    super.append(fragment, attributes);
    setName(getName() + fragment);
  }

  public void clear() {
    super.clear();
    setName("");
  }

}
