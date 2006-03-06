package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadScrollPane extends RadContainer {
  public static final Class COMPONENT_CLASS = JScrollPane.class;

  public RadScrollPane(final Module module, final String id){
    super(module, COMPONENT_CLASS, id);
  }

  @Nullable @Override
  protected RadLayoutManager createInitialLayoutManager() {
    return new RadScrollPaneLayoutManager();
  }

  public void write(final XmlWriter writer) {
    writer.startElement("scrollpane");
    try {
      writeNoLayout(writer);
    } finally {
      writer.endElement(); // scrollpane
    }
  }

  private class RadScrollPaneLayoutManager extends RadLayoutManager {
    private MyDropLocation myDropLocation = null;

    @Nullable public String getName() {
      return null;
    }

    public void writeChildConstraints(final XmlWriter writer, final RadComponent child) {
    }

    @Override @NotNull
    public DropLocation getDropLocation(RadContainer container, @Nullable final Point location) {
      if (myDropLocation == null) {
        myDropLocation = new MyDropLocation();
      }
      return myDropLocation;
    }

    public void addComponentToContainer(final RadContainer container, final RadComponent component, final int index) {
      final JScrollPane scrollPane = (JScrollPane)container.getDelegee();
      final JComponent delegee = component.getDelegee();
      delegee.setLocation(0,0);
      scrollPane.setViewportView(delegee);
    }

    @Override public void removeComponentFromContainer(final RadContainer container, final RadComponent component) {
      final JScrollPane scrollPane = (JScrollPane)container.getDelegee();
      scrollPane.setViewportView(null);
    }
  }

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
