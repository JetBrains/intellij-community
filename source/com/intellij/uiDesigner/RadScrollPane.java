package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.core.AbstractLayout;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadScrollPane extends RadContainer{
  public static final Class COMPONENT_CLASS = JScrollPane.class;
  
  public RadScrollPane(final Module module, final String id){
    super(module, COMPONENT_CLASS, id);
  }

  protected AbstractLayout createInitialLayout(){
    return null;
  }

  public boolean canDrop(final int x, final int y, final int componentCount){
    return componentCount == 1 && getComponentCount() == 0;
  }

  public DropInfo drop(final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy){
    addComponent(components[0]);
    return new DropInfo(this, null, null);
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
    try{
      writeId(writer);
      writeBinding(writer);

      // Constraints and properties
      writeConstraints(writer);
      writeProperties(writer);

      // Margin and border
      writeBorder(writer);
      writeChildren(writer);
    }finally{
      writer.endElement(); // scrollpane
    }
  }

  public void writeConstraints(final XmlWriter writer, final RadComponent child) {}
}
