package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.designSurface.ComponentDragObject;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class RadAtomicComponent extends RadComponent {
  public RadAtomicComponent(final Module module, final Class aClass, final String id){
    super(module, aClass, id);
  }

  public void write(final XmlWriter writer) {
    writer.startElement("component");
    try{
      writeId(writer);
      writeClass(writer);
      writeBinding(writer);
      writeConstraints(writer);
      writeProperties(writer);
    }finally{
      writer.endElement(); // component
    }
  }
}
