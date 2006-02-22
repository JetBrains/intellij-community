package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadScrollPane extends RadContainer {
  public static final Class COMPONENT_CLASS = JScrollPane.class;
  private MyDropLocation myDropLocation = null;

  public RadScrollPane(final Module module, final String id){
    super(module, COMPONENT_CLASS, id);
  }

  @Nullable
  protected AbstractLayout createInitialLayout(){
    return null;
  }

  @Override @Nullable
  public DropLocation getDropLocation(@Nullable Point location) {
    if (myDropLocation == null) {
      myDropLocation = new MyDropLocation();
    }
    return myDropLocation;
  }

  @Override protected void addToDelegee(final int index, final RadComponent component){
    final JScrollPane scrollPane = (JScrollPane)getDelegee();
    final JComponent delegee = component.getDelegee();
    delegee.setLocation(0,0);
    scrollPane.setViewportView(delegee);
  }

  protected void removeFromDelegee(final RadComponent component){
    final JScrollPane scrollPane = (JScrollPane)getDelegee();
    scrollPane.setViewportView(null);
  }

  public void write(final XmlWriter writer) {
    writer.startElement("scrollpane");
    try {
      writeNoLayout(writer);
    } finally {
      writer.endElement(); // scrollpane
    }
  }

  public void writeConstraints(final XmlWriter writer, final RadComponent child) {}

  private class MyDropLocation implements DropLocation {
    public RadContainer getContainer() {
      return RadScrollPane.this;
    }

    public boolean canDrop(ComponentDragObject dragObject) {
      return dragObject.getComponentCount() == 1 && getComponentCount() == 0;
    }

    public void placeFeedback(FeedbackLayer feedbackLayer, ComponentDragObject dragObject) {
      feedbackLayer.putFeedback(getDelegee(), new Rectangle(0, 0, getWidth(), getHeight()));
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      addComponent(components[0]);
    }
  }
}
