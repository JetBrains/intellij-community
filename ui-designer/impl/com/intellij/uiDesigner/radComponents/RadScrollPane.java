package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.AbstractLayout;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  protected AbstractLayout createInitialLayout(){
    return null;
  }

  public boolean canDrop(@Nullable Point location, final int componentCount){
    return componentCount == 1 && getComponentCount() == 0;
  }

  public void drop(@Nullable Point location, final RadComponent[] components, final int[] dx, final int[] dy){
    addComponent(components[0]);
  }

  @Nullable
  public Rectangle getDropFeedbackRectangle(Point location, final int componentCount) {
    return new Rectangle(0, 0, getWidth(), getHeight());
  }

  protected void addToDelegee(final RadComponent component){
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
}
