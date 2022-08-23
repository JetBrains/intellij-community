// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;

public class RadAtomicComponent extends RadComponent {
  public RadAtomicComponent(final ModuleProvider module, final Class aClass, final String id){
    super(module, aClass, id);
  }

  public RadAtomicComponent(@NotNull final Class aClass, @NotNull final String id, final Palette palette) {
    super(null, aClass, id, palette);
  }

  @Override
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
