package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.lw.LwSplitPane;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadSplitPane extends RadContainer{
  public RadSplitPane(final Module module, final String id){
    super(module, JSplitPane.class, id);
  }

  protected AbstractLayout createInitialLayout(){
    return null;
  }

  public boolean canDrop(final int x, final int y, final int componentCount){
    if (componentCount != 1) {
      return false;
    }
    
    final Component component;
    if (isLeft(x,y)) {
      component = getSplitPane().getLeftComponent();
    }
    else {
      component = getSplitPane().getRightComponent();
    }
    
    return component == null || ((JComponent)component).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT) == null;
  }

  private boolean isLeft(final int x, final int y){
    final JSplitPane splitPane = getSplitPane();
    if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
      return y < splitPane.getHeight() / 2;
    }
    else {
      return x < splitPane.getWidth() / 2;
    }
  }

  private JSplitPane getSplitPane(){
    return (JSplitPane)getDelegee();
  }

  public DropInfo drop(final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy){
    components[0].setCustomLayoutConstraints(isLeft(x,y) ? LwSplitPane.POSITION_LEFT  : LwSplitPane.POSITION_RIGHT);
    addComponent(components[0]);
    return new DropInfo(this, null, null);
  }

  protected void addToDelegee(final RadComponent component){
    final JSplitPane splitPane = getSplitPane();
    final JComponent delegee = component.getDelegee();
    if (LwSplitPane.POSITION_LEFT.equals(component.getCustomLayoutConstraints())){
      splitPane.setLeftComponent(delegee);
    }
    else {
      splitPane.setRightComponent(delegee);
    }
  }

  public void write(final XmlWriter writer) {
    writer.startElement("splitpane");
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

  public void writeConstraints(final XmlWriter writer, final RadComponent child) {
    writer.startElement("splitpane");
    try{
      final String position = (String)child.getCustomLayoutConstraints();
      if (!LwSplitPane.POSITION_LEFT.equals(position) && !LwSplitPane.POSITION_RIGHT.equals(position)) {
        //noinspection HardCodedStringLiteral
        throw new IllegalStateException("invalid position: " + position);
      }
      writer.addAttribute("position", position);
    }finally{
      writer.endElement(); 
    }
  }
}
