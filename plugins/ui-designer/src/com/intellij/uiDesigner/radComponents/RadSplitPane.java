// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.lw.LwSplitPane;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public final class RadSplitPane extends RadContainer {
  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadSplitPane(module, aClass, id);
    }

    @Override
    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadSplitPane(componentClass, id, palette);
    }
  }

  public RadSplitPane(final ModuleProvider module, final Class componentClass, final String id) {
    super(module, componentClass, id);
  }

  public RadSplitPane(Class componentClass, final @NotNull String id, final Palette palette) {
    super(componentClass, id, palette);
  }

  @Override protected RadLayoutManager createInitialLayoutManager() {
    return new RadSplitPaneLayoutManager();
  }

  private static boolean isEmptySplitComponent(final Component component) {
    return component == null || ((JComponent)component).getClientProperty(CLIENT_PROP_RAD_COMPONENT) == null;
  }

  private boolean isLeft(Point pnt) {
    if (getSplitPane().getOrientation() == JSplitPane.VERTICAL_SPLIT) {
      return pnt.y < getDividerPos();
    }
    else {
      return pnt.x < getDividerPos();
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

  @Override
  public @Nullable EventProcessor getEventProcessor(final MouseEvent event) {
    final JSplitPane splitPane = getSplitPane();
    Point pnt = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), splitPane);
    int pos = (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) ? pnt.y : pnt.x;
    if (event.getID() == MouseEvent.MOUSE_PRESSED &&
        pos >= splitPane.getDividerLocation() &&
        pos < splitPane.getDividerLocation() + splitPane.getDividerSize()) {
      return new DividerDragProcessor();
    }
    return null;
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_SPLITPANE);
    try {
      writeId(writer);
      writeClassIfDifferent(writer, JSplitPane.class.getName());
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


  private class RadSplitPaneLayoutManager extends RadLayoutManager {

    @Override
    public @Nullable String getName() {
      return null;
    }

    @Override
    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
      writer.startElement("splitpane");
      try {
        final String position = (String)child.getCustomLayoutConstraints();
        if (!LwSplitPane.POSITION_LEFT.equals(position) && !LwSplitPane.POSITION_RIGHT.equals(position)) {
          throw new IllegalStateException("invalid position: " + position);
        }
        writer.addAttribute("position", position);
      }
      finally {
        writer.endElement();
      }
    }

    @Override
    public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
      final JSplitPane splitPane = (JSplitPane) container.getDelegee();
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

    @Override
    public @NotNull ComponentDropLocation getDropLocation(RadContainer container, final @Nullable Point location) {
      if (location == null) {
        return new MyDropLocation(isEmptySplitComponent(getSplitPane().getLeftComponent()));
      }
      return new MyDropLocation(isLeft(location));
    }
  }

  private class MyDropLocation implements ComponentDropLocation {
    private final boolean myLeft;

    MyDropLocation(final boolean left) {
      myLeft = left;
    }

    @Override
    public RadContainer getContainer() {
      return RadSplitPane.this;
    }

    @Override
    public boolean canDrop(ComponentDragObject dragObject) {
      /*
      TODO[yole]: support multi-drop (is it necessary?)
      if (componentCount == 2) {
        return isEmptySplitComponent(getSplitPane().getLeftComponent()) &&
               isEmptySplitComponent(getSplitPane().getRightComponent());
      }
      */
      return dragObject.getComponentCount() == 1 &&
             isEmptySplitComponent(myLeft ? getSplitPane().getLeftComponent() : getSplitPane().getRightComponent());
    }

    @Override
    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      final JSplitPane splitPane = getSplitPane();
      int dividerPos = getDividerPos();
      int dividerStartPos = dividerPos - splitPane.getDividerSize()/2;
      int dividerEndPos = dividerPos + splitPane.getDividerSize() - splitPane.getDividerSize()/2;
      Rectangle rc;
      if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
        rc = myLeft
             ? new Rectangle(0, 0, getWidth(), dividerStartPos)
             : new Rectangle(0, dividerEndPos, getWidth(), getHeight() - dividerEndPos);
      }
      else {
        rc = myLeft
             ? new Rectangle(0, 0, dividerStartPos, getHeight())
             : new Rectangle(dividerEndPos, 0, getWidth() - dividerEndPos, getHeight());
      }
      feedbackLayer.putFeedback(getDelegee(), rc, getInsertFeedbackTooltip());
    }

    private String getInsertFeedbackTooltip() {
      String pos;
      if (getSplitPane().getOrientation() == JSplitPane.VERTICAL_SPLIT) {
        pos = myLeft ? UIDesignerBundle.message("insert.feedback.top") : UIDesignerBundle.message("insert.feedback.bottom");
      }
      else {
        pos = myLeft ? UIDesignerBundle.message("insert.feedback.left") : UIDesignerBundle.message("insert.feedback.right");
      }
      return getDisplayName() + " (" + pos + ")";
    }

    @Override
    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      components[0].setCustomLayoutConstraints(myLeft ? LwSplitPane.POSITION_LEFT : LwSplitPane.POSITION_RIGHT);
      addComponent(components[0]);
    }

    @Override
    public @Nullable ComponentDropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }

  private class DividerDragProcessor extends EventProcessor {
    @Override
    protected void processKeyEvent(KeyEvent e) {
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      JSplitPane splitPane = getSplitPane();
      Point pnt = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), splitPane);
      splitPane.dispatchEvent(new MouseEvent(splitPane, event.getID(), event.getWhen(), event.getModifiers(), pnt.x, pnt.y,
                                             event.getClickCount(), event.isPopupTrigger(), event.getButton()));
    }

    @Override public boolean needMousePressed() {
      return true;
    }

    @Override
    protected boolean cancelOperation() {
      return false;
    }
  }
}
