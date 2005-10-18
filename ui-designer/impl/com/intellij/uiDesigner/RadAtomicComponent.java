package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class RadAtomicComponent extends RadComponent {
  public RadAtomicComponent(final Module module, final Class aClass, final String id){
    super(module, aClass, id);
  }

  public final boolean canDrop(final int x, final int y, final int componentCount){
    return false;
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
