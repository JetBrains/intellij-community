package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import com.intellij.uiDesigner.designSurface.DropLocation;
import com.intellij.uiDesigner.designSurface.FeedbackLayer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadScrollPane extends RadContainer {
  public static final Class COMPONENT_CLASS = JScrollPane.class;

  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(Module module, Class aClass, String id) {
      return new RadScrollPane(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadScrollPane(componentClass, id, palette);
    }
  }

  public RadScrollPane(final Module module, final Class componentClass, final String id){
    super(module, componentClass, id);
  }

  public RadScrollPane(final Class componentClass, final String id, final Palette palette) {
    super(componentClass, id, palette);
  }

  @Nullable @Override
  protected RadLayoutManager createInitialLayoutManager() {
    return new RadScrollPaneLayoutManager();
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_SCROLLPANE);
    try {
      writeNoLayout(writer, JScrollPane.class.getName());
    } finally {
      writer.endElement(); // scrollpane
    }
  }

  @Override public RadComponent getActionTargetComponent(RadComponent child) {
    return this;
  }

  @Override
  protected void importSnapshotComponent(final SnapshotContext context, final JComponent component) {
    JScrollPane scrollPane = (JScrollPane) component;
    final Component view = scrollPane.getViewport().getView();
    if (view instanceof JComponent) {
      RadComponent childComponent = createSnapshotComponent(context, (JComponent) view);
      if (childComponent != null) {
        addComponent(childComponent);
      }
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
      feedbackLayer.putFeedback(getDelegee(), new Rectangle(0, 0, getWidth(), getHeight()), getDisplayName());
    }

    public void processDrop(GuiEditor editor,
                            RadComponent[] components,
                            GridConstraints[] constraintsToAdjust,
                            ComponentDragObject dragObject) {
      addComponent(components[0]);
    }

    @Nullable
    public DropLocation getAdjacentLocation(Direction direction) {
      return null;
    }
  }
}
