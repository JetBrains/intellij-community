package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.DropInfo;
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

  public boolean canDrop(final int x, final int y, final int componentCount){
    return canDrop(componentCount);
  }

  public boolean canDrop(int componentCount) {
    return componentCount == 1 && getComponentCount() == 0;
  }

  public DropInfo drop(final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy){
    addComponent(components[0]);
    return new DropInfo(this, null, null);
  }

  public void drop(RadComponent[] components) {
    addComponent(components [0]);
  }

  @Nullable
  public Rectangle getDropFeedbackRectangle(final int x, final int y, final int componentCount) {
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
