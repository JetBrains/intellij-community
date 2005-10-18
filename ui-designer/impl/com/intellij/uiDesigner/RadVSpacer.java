package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;


/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadVSpacer extends RadAtomicComponent {
  public RadVSpacer(final Module module, final String id) {
    super(module, VSpacer.class, id);
  }

  public void write(final XmlWriter writer) {
    writer.startElement("vspacer");
    try{
      writeId(writer);
      writeConstraints(writer);
    }finally{
      writer.endElement(); // vspacer
    }
  }
}
