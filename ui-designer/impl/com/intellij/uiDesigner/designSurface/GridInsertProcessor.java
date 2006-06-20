package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2005
 * Time: 20:10:59
 * To change this template use File | Settings | File Templates.
 */
public class GridInsertProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.GridInsertProcessor");

  private GuiEditor myEditor;

  public GridInsertProcessor(final GuiEditor editor) {
    myEditor = editor;
  }

  @NotNull public static DropLocation getDropLocation(RadRootContainer rootContainer, Point aPoint) {
    RadContainer container = getDropTargetContainer(rootContainer, aPoint);

    if (container == null) {
      return NoDropLocation.INSTANCE;
    }

    final Point targetPoint = SwingUtilities.convertPoint(rootContainer.getDelegee(), aPoint, container.getDelegee());
    return container.getDropLocation(targetPoint);
  }

  public static RadContainer getDropTargetContainer(final RadRootContainer rootContainer, final Point aPoint) {
    int EPSILON = 4;
    RadContainer container = FormEditingUtil.getRadContainerAt(rootContainer, aPoint.x, aPoint.y, EPSILON);
    // to facilitate initial component adding, increase stickiness if there is one container at top level
    if (container instanceof RadRootContainer && rootContainer.getComponentCount() == 1) {
      final RadComponent singleComponent = rootContainer.getComponents()[0];
      if (singleComponent instanceof RadContainer) {
        Rectangle rc = singleComponent.getDelegee().getBounds();
        rc.grow(EPSILON*2, EPSILON*2);
        if (rc.contains(aPoint)) {
          container = (RadContainer) singleComponent;
          EPSILON *= 2;
        }
      }
    }
    return container;
  }

  public DropLocation processDragEvent(Point pnt, ComponentDragObject dragObject) {
    final DropLocation dropLocation = getDropLocation(myEditor.getRootContainer(), pnt);
    LOG.info("GridInsertProcessor.processDragEvent(): dropLocation " + dropLocation.toString());
    if (dropLocation.canDrop(dragObject)) {
      dropLocation.placeFeedback(myEditor.getActiveDecorationLayer(), dragObject);
    }
    else {
      myEditor.getActiveDecorationLayer().removeFeedback();
    }

    return dropLocation;
  }

  public Cursor processMouseMoveEvent(final Point pnt, final boolean copyOnDrop, final ComponentDragObject dragObject) {
    DropLocation location = processDragEvent(pnt, dragObject);
    if (!location.canDrop(dragObject)) {
      return FormEditingUtil.getMoveNoDropCursor();
    }
    return copyOnDrop ? FormEditingUtil.getCopyDropCursor() : FormEditingUtil.getMoveDropCursor();
  }
}
