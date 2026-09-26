// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.HSpacer;
import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.palette.Palette;

public final class RadHSpacer extends RadAtomicComponent {
  public static class Factory extends RadComponentFactory {
    @Override
    public RadComponent newInstance(ModuleProvider moduleProvider, Class aClass, String id) {
      return new RadHSpacer(moduleProvider, aClass, id);
    }

    @Override
    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      throw new UnsupportedOperationException("Spacer instances should not be created by SnapShooter");
    }

    @Override
    public RadComponent newInstance(ModuleProvider moduleProvider, String className, String id) throws ClassNotFoundException {
      return new RadHSpacer(moduleProvider, HSpacer.class, id);
    }
  }

  public RadHSpacer(final ModuleProvider moduleProvider, final String id) {
    super(moduleProvider, HSpacer.class, id);
  }

  public RadHSpacer(final ModuleProvider moduleProvider, final Class aClass, final String id) {
    super(moduleProvider, aClass, id);
  }

  /**
   * Constructor for use in SnapShooter
   */
  public RadHSpacer(final String id, final int column) {
    super(null, HSpacer.class, id);
    getConstraints().setColumn(column);
    getConstraints().setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW |
                                    GridConstraints.SIZEPOLICY_WANT_GROW);
    getConstraints().setFill(GridConstraints.FILL_HORIZONTAL);
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement("hspacer");
    try {
      writeId(writer);
      writeConstraints(writer);
    }
    finally {
      writer.endElement(); // hspacer
    }
  }

  @Override
  public boolean hasIntrospectedProperties() {
    return false;
  }
}
