package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class RadAtomicComponent extends RadComponent {
  public RadAtomicComponent(final Module module, final Class aClass, final String id){
    super(module, aClass, id);
  }

  public RadAtomicComponent(@NotNull final Class aClass, @NotNull final String id, final Palette palette) {
    super(null, aClass, id, palette);
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
