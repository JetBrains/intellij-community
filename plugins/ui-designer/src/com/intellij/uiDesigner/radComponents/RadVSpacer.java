// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.VSpacer;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.Palette;


public final class RadVSpacer extends RadAtomicComponent {
  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider module, Class aClass, String id) {
      return new RadVSpacer(module, aClass, id);
    }

    @Override
    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      throw new UnsupportedOperationException("Spacer instances should not be created by SnapShooter");
    }

    @Override
    public RadComponent newInstance(ModuleProvider module, String className, String id) throws ClassNotFoundException {
      return new RadVSpacer(module, VSpacer.class, id);
    }
  }

  public RadVSpacer(final ModuleProvider module, final String id) {
    super(module, VSpacer.class, id);
  }

  public RadVSpacer(final ModuleProvider module, final Class aClass, final String id) {
    super(module, aClass, id);
  }

  /**
   * Constructor for use in SnapShooter
   */
  public RadVSpacer(final String id, final int row) {
    super(null, VSpacer.class, id);
    getConstraints().setRow(row);
    getConstraints().setVSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW |
                                    GridConstraints.SIZEPOLICY_WANT_GROW);
    getConstraints().setFill(GridConstraints.FILL_VERTICAL);
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement("vspacer");
    try {
      writeId(writer);
      writeConstraints(writer);
    }
    finally {
      writer.endElement(); // vspacer
    }
  }

  @Override
  public boolean hasIntrospectedProperties() {
    return false;
  }
}
