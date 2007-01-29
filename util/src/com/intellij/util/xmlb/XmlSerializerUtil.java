package com.intellij.util.xmlb;

public class XmlSerializerUtil {
  private XmlSerializerUtil() {
  }

  public static void copyBean(final Object from, final Object to) {
    assert from.getClass().equals(to.getClass()) : "Beans of different classes specified";

    final Accessor[] accessors = BeanBinding.getAccessors(from.getClass());
    for (Accessor accessor : accessors) {
      accessor.write(to, accessor.read(from));
    }
  }
}
