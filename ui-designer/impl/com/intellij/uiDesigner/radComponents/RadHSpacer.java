package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.XmlWriter;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadHSpacer extends RadAtomicComponent {
  public RadHSpacer(final Module module, final String id) {
    super(module, HSpacer.class, id);
  }

  public void write(final XmlWriter writer) {
    writer.startElement("hspacer");
    try{
      writeId(writer);
      writeConstraints(writer);
    }finally{
      writer.endElement(); // hspacer
    }
  }

  @Override public boolean hasIntrospectedProperties() {
    return false;
  }
}
