// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NotNull;

public class RadAtomicComponent extends RadComponent {
  public RadAtomicComponent(final ModuleProvider module, final Class aClass, final String id){
    super(module, aClass, id);
  }

  public RadAtomicComponent(final @NotNull Class aClass, final @NotNull String id, final Palette palette) {
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
