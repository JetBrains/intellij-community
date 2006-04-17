package com.intellij.ide.errorTreeView;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 *
 */
public class NewErrorTreeRenderer extends MultilineTreeCellRenderer {
  private final static Icon ourFileIcon = IconLoader.getIcon("/fileTypes/java.png");
  private final static Icon ourErrorIcon = IconLoader.getIcon("/compiler/error.png");
  private final static Icon ourWarningIcon = IconLoader.getIcon("/compiler/warning.png");
  private final static Icon ourInfoIcon = IconLoader.getIcon("/compiler/information.png");

  private NewErrorTreeRenderer() {
  }

  public static JScrollPane install(JTree tree) {
    return MultilineTreeCellRenderer.installRenderer(tree, new NewErrorTreeRenderer());
  }

  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    final ErrorTreeElement element = getElement(value);
    if(element instanceof GroupingElement) {
      setFont(getFont().deriveFont(Font.BOLD));
    }

    if(element instanceof SimpleMessageElement || element instanceof NavigatableMessageElement) {
      String prefix = element.getKind().getPresentableText();

      if (element instanceof NavigatableMessageElement) {
        prefix += ((NavigatableMessageElement)element).getRendererTextPrefix() + " ";
      }

      setText(element.getText(), prefix);
    }
    else if (element != null){
      String[] text = element.getText();
      if (text == null) {
        text = ArrayUtil.EMPTY_STRING_ARRAY;
      }
      if(text.length > 0 && text[0] == null) {
        text[0] = "";
      }
      setText(text, null);
    }

    Icon icon = null;

    if (element instanceof GroupingElement) {
      icon = ourFileIcon;
    }
    else if (element instanceof SimpleMessageElement || element instanceof NavigatableMessageElement) {
      ErrorTreeElementKind kind = element.getKind();
      if (ErrorTreeElementKind.ERROR.equals(kind)) {
        icon = ourErrorIcon;
      }
      else if (ErrorTreeElementKind.WARNING.equals(kind)) {
        icon = ourWarningIcon;
      }
      else if (ErrorTreeElementKind.INFO.equals(kind)) {
        icon = ourInfoIcon;
      }
    }

    setIcon(icon);
  }

  private static ErrorTreeElement getElement(Object value) {
    if (!(value instanceof DefaultMutableTreeNode)) {
      return null;
    }
    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (!(userObject instanceof ErrorTreeNodeDescriptor)) {
      return null;
    }
    return ((ErrorTreeNodeDescriptor)userObject).getElement();
  }
}

