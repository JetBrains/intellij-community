package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.WatermarkIcon;

import javax.swing.border.EmptyBorder;
import java.awt.*;

class ContentTabLabel extends BaseLabel {

  Content myContent;
  private BaseButtonBehavior myBehavior;

  public ContentTabLabel(final Content content, ToolWindowContentUi ui) {
    super(ui);
    myContent = content;
    update();

    myBehavior = new BaseButtonBehavior(this) {
      protected void execute() {
        myUi.myWindow.getContentManager().setSelectedContent(myContent, true);
      }
    };
  }

  public void update() {
    final int index = myUi.myTabs.indexOf(this);
    final int length = myUi.myTabs.size();
    if (length == 1) {
      setBorder(new EmptyBorder(0, 7, 0, 11));
    } else if (index == 0) {
      setBorder(new EmptyBorder(0, 8, 0, 8));
    } else if (index == length - 1) {
      setBorder(new EmptyBorder(0, 6, 0, 10));
    } else {
      setBorder(new EmptyBorder(0, 6, 0, 10));
    }


    setText(myContent.getDisplayName());
    setActiveFg(isSelected() ? Color.white : new Color(188, 195, 219));
    setPassiveFg(isSelected() ? Color.white : new Color(213, 210, 202));
    setToolTipText(myContent.getDescription());

    final boolean show = Boolean.TRUE.equals(myContent.getUserData(ToolWindow.SHOW_CONTENT_ICON));
    if (show) {
     if (isSelected()) {
       setIcon(myContent.getIcon());
     } else {
       setIcon(myContent.getIcon() != null ? new WatermarkIcon(myContent.getIcon(), .5f) : null);
     }
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
