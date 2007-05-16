package com.intellij.openapi.wm.impl.content;

import com.intellij.ui.content.Content;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.util.IconLoader;

import javax.swing.border.EmptyBorder;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

class ContentTabLabel extends BaseLabel {

  Content myContent;
  private BaseButtonBehavior myBehavior;

  public ContentTabLabel(final Content content, ToolWindowContentUi ui) {
    super(ui);
    myContent = content;
    setBorder(new EmptyBorder(0, 6, 0, 6));
    update();

    myBehavior = new BaseButtonBehavior(this) {
      protected void execute() {
        myUi.myWindow.getContentManager().setSelectedContent(myContent, true);
      }

      public void setHovered(final boolean hovered) {
        setCursor(hovered && myUi.myTabs.size() > 1 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
      }
    };
  }

  public void update() {
    setText(myContent.getDisplayName());
    setActiveFg(isSelected() ? Color.white : Color.lightGray);
    setPassiveFg(isSelected() ? Color.white : Color.gray);

    final boolean show = Boolean.TRUE.equals(myContent.getUserData(ToolWindow.SHOW_CONTENT_ICON));
    if (show) {
      setIcon(myContent.getIcon());
    } else {
      setIcon(null);
    }
  }

  protected void paintComponent(final Graphics g) {
    if (myBehavior.isPressedByMouse() && !isSelected()) {
      g.translate(1, 1);
    }

    super.paintComponent(g);

    g.translate(-1, -1);
  }

  public boolean isSelected() {
    return myUi.myWindow.getContentManager().isSelected(myContent);
  }

}
