package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.VSpacer;


/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadVSpacer extends RadAtomicComponent {
  public RadVSpacer(final Module module, final String id) {
    super(module, VSpacer.class, id);
  }

  public RadVSpacer(final Module module, final Class aClass, final String id) {
    super(module, aClass, id);
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

  @Override public boolean hasIntrospectedProperties() {
    return false;
  }
}
