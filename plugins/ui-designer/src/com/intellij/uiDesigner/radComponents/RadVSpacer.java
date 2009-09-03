package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.VSpacer;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.core.GridConstraints;


/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadVSpacer extends RadAtomicComponent {
  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(Module module, Class aClass, String id) {
      return new RadVSpacer(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      throw new UnsupportedOperationException("Spacer instances should not be created by SnapShooter");
    }

    public RadComponent newInstance(Module module, String className, String id) throws ClassNotFoundException {
      return new RadVSpacer(module, VSpacer.class, id);
    }
  }

  public RadVSpacer(final Module module, final String id) {
    super(module, VSpacer.class, id);
  }

  public RadVSpacer(final Module module, final Class aClass, final String id) {
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
