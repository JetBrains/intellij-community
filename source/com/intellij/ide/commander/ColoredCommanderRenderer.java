package com.intellij.ide.commander;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

final class ColoredCommanderRenderer extends ColoredListCellRenderer {
  private static final Icon ourUpLevelIcon = IconLoader.getIcon("/nodes/upLevel.png");
  private CommanderPanel myCommanderPanel;

  public ColoredCommanderRenderer(final CommanderPanel commanderPanel) {
    if (commanderPanel == null){
      throw new IllegalArgumentException("commanderPanel cannot be null");
    } 
    myCommanderPanel = commanderPanel;
  }

  public Component getListCellRendererComponent(final JList list, final Object value, final int index, boolean selected, boolean hasFocus){
    hasFocus = selected; // border around inactive items 

    final Commander commander = myCommanderPanel.getCommander();
    if (commander != null && !commander.isPanelActive(myCommanderPanel)) {
      selected = false;
    }
    
    return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
  }

  protected void customizeCellRenderer(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
    Color color = UIManager.getColor("List.foreground");
    if (value instanceof NodeDescriptor) {
      final NodeDescriptor descriptor = (NodeDescriptor)value;
      setIcon(descriptor.getClosedIcon());
      final Color elementColor = descriptor.getColor();
      if (elementColor != null) {
        color = elementColor;
      }
    }
    else {
      setIcon(ourUpLevelIcon);
    }
    append(value.toString(), new SimpleTextAttributes(Font.PLAIN, color));
    if (value instanceof PsiDirectoryNode) {
      String locationString = ((PsiDirectoryNode)value).getPresentation().getLocationString();
      if (locationString != null && locationString.length() > 0) {
        append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }
  }
}
