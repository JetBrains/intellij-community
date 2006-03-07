package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.*;
import com.intellij.uiDesigner.lw.LwSplitPane;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadSplitPane extends RadContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.radComponents.RadSplitPane");

  public RadSplitPane(final Module module, final String id) {
    super(module, JSplitPane.class, id);
  }

  @Override protected RadLayoutManager createInitialLayoutManager() {
    return new RadSplitPaneLayoutManager();
  }

  private static boolean isEmptySplitComponent(final Component component) {
    return component == null || ((JComponent)component).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT) == null;
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

  @Override @Nullable
  public EventProcessor getEventProcessor(final MouseEvent event) {
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

  private class RadSplitPaneLayoutManager extends RadLayoutManager {

    @Nullable public String getName() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
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

    @Override @NotNull
    public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
      if (location == null) {
        return new MyDropLocation(isEmptySplitComponent(getSplitPane().getLeftComponent()));
      }
      return new MyDropLocation(isLeft(location));
    }
  }

  private class MyDropLocation implements DropLocation {
    private boolean myLeft;

    public MyDropLocation(final boolean left) {
      myLeft = left;
    }

    public RadContainer getContainer() {
      return RadSplitPane.this;
    }

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

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      final JSplitPane splitPane = getSplitPane();
      int dividerPos = getDividerPos();
      int dividerLeftPos = dividerPos - splitPane.getDividerSize()/2;
      int dividerRightPos = dividerPos + splitPane.getDividerSize() - splitPane.getDividerSize()/2;
      Rectangle rc;
      if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
        rc = myLeft
             ? new Rectangle(0, 0, getWidth(), dividerLeftPos)
             : new Rectangle(0, dividerRightPos, getWidth(), getHeight() - dividerRightPos);
      }
      else {
        rc = myLeft
             ? new Rectangle(0, 0, dividerLeftPos, getHeight())
             : new Rectangle(dividerRightPos, 0, getWidth() - dividerRightPos, getHeight());
      }
      feedbackLayer.putFeedback(getDelegee(), rc);
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      components[0].setCustomLayoutConstraints(myLeft ? LwSplitPane.POSITION_LEFT : LwSplitPane.POSITION_RIGHT);
      addComponent(components[0]);
    }
  }

  private class DividerDragProcessor extends EventProcessor {
    protected void processKeyEvent(KeyEvent e) {
    }

    protected void processMouseEvent(MouseEvent event) {
      JSplitPane splitPane = getSplitPane();
      Point pnt = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), splitPane);
      splitPane.dispatchEvent(new MouseEvent(splitPane, event.getID(), event.getWhen(), event.getModifiers(), pnt.x, pnt.y,
                                             event.getClickCount(), event.isPopupTrigger(), event.getButton()));
    }

    @Override public boolean needMousePressed() {
      return true;
    }

    protected boolean cancelOperation() {
      return false;
    }
  }
}
