package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.lw.LwSplitPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadSplitPane extends RadContainer {
  public RadSplitPane(final Module module, final String id) {
    super(module, JSplitPane.class, id);
  }

  protected AbstractLayout createInitialLayout() {
    return null;
  }

  public boolean canDrop(final int x, final int y, final int componentCount) {
    if (componentCount != 1) {
      return false;
    }

    final Component component;
    if (isLeft(x, y)) {
      component = getSplitPane().getLeftComponent();
    }
    else {
      component = getSplitPane().getRightComponent();
    }

    return isEmptySplitComponent(component);
  }

  private static boolean isEmptySplitComponent(final Component component) {
    return component == null || ((JComponent)component).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT) == null;
  }

  public boolean canDrop(int componentCount) {
    /*
    TODO[yole]: support multi-drop (is it necessary?)
    if (componentCount == 2) {
      return isEmptySplitComponent(getSplitPane().getLeftComponent()) &&
             isEmptySplitComponent(getSplitPane().getRightComponent());
    }
    */
    if (componentCount == 1) {
      return isEmptySplitComponent(getSplitPane().getLeftComponent()) ||
             isEmptySplitComponent(getSplitPane().getRightComponent());
    }
    return false;
  }

  private boolean isLeft(final int x, final int y) {
    if (getSplitPane().getOrientation() == JSplitPane.VERTICAL_SPLIT) {
      return y < getDividerPos();
    }
    else {
      return x < getDividerPos();
    }
  }

  private int getDividerPos() {
    final JSplitPane splitPane = getSplitPane();
    int size = splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT ? splitPane.getHeight() : splitPane.getWidth();
    if (splitPane.getDividerLocation() > splitPane.getDividerSize() &&
        splitPane.getDividerLocation() < size - splitPane.getDividerSize()) {
      return splitPane.getDividerLocation() + splitPane.getDividerSize() / 2;
    }
    else {
      return size / 2;
    }
  }

  private JSplitPane getSplitPane() {
    return (JSplitPane)getDelegee();
  }

  public DropInfo drop(final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy) {
    components[0].setCustomLayoutConstraints(isLeft(x, y) ? LwSplitPane.POSITION_LEFT : LwSplitPane.POSITION_RIGHT);
    addComponent(components[0]);
    return new DropInfo(this, null, null);
  }

  public void drop(RadComponent[] components) {
    components[0].setCustomLayoutConstraints(isEmptySplitComponent(getSplitPane().getLeftComponent())
                                             ? LwSplitPane.POSITION_LEFT
                                             : LwSplitPane.POSITION_RIGHT);
    addComponent(components[0]);
  }

  @Nullable
  public Rectangle getDropFeedbackRectangle(final int x, final int y, final int componentCount) {
    final JSplitPane splitPane = getSplitPane();
    int dividerPos = getDividerPos();
    int dividerLeftPos = dividerPos - splitPane.getDividerSize()/2;
    int dividerRightPos = dividerPos + splitPane.getDividerSize() - splitPane.getDividerSize()/2;
    if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
      return isLeft(x, y)
             ? new Rectangle(0, 0, getWidth(), dividerLeftPos)
             : new Rectangle(0, dividerRightPos, getWidth(), getHeight() - dividerRightPos);
    }
    else {
      return isLeft(x, y)
             ? new Rectangle(0, 0, dividerLeftPos, getHeight())
             : new Rectangle(dividerRightPos, 0, getWidth() - dividerRightPos, getHeight());
    }
  }

  protected void addToDelegee(final RadComponent component) {
    final JSplitPane splitPane = getSplitPane();
    final JComponent delegee = component.getDelegee();
    if (LwSplitPane.POSITION_LEFT.equals(component.getCustomLayoutConstraints())) {
      splitPane.setLeftComponent(delegee);
    }
    else if (LwSplitPane.POSITION_RIGHT.equals(component.getCustomLayoutConstraints())) {
      splitPane.setRightComponent(delegee);
    }
    else {
      throw new IllegalStateException("invalid layout constraints on component added to RadSplitPane");
    }
  }

  public void write(final XmlWriter writer) {
    writer.startElement("splitpane");
    try {
      writeId(writer);
      writeBinding(writer);

      // Constraints and properties
      writeConstraints(writer);
      writeProperties(writer);

      // Margin and border
      writeBorder(writer);
      writeChildren(writer);
    }
    finally {
      writer.endElement(); // scrollpane
    }
  }

  public void writeConstraints(final XmlWriter writer, final RadComponent child) {
    writer.startElement("splitpane");
    try {
      final String position = (String)child.getCustomLayoutConstraints();
      if (!LwSplitPane.POSITION_LEFT.equals(position) && !LwSplitPane.POSITION_RIGHT.equals(position)) {
        //noinspection HardCodedStringLiteral
        throw new IllegalStateException("invalid position: " + position);
      }
      writer.addAttribute("position", position);
    }
    finally {
      writer.endElement();
    }
  }
}
