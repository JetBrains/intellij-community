package com.intellij.util.xmlb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

class FieldAccessor implements Accessor {
  private final Field myField;

  public FieldAccessor(Field myField) {
    this.myField = myField;
  }

  public Object read(Object o) {
    try {
      return myField.get(o);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
  }

  public void write(Object o, Object value) {
    try {
      myField.set(o, XmlSerializer.convert(value, myField.getType()));
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
  }

  public Annotation[] getAnnotations() {
    return myField.getAnnotations();
  }

  public String getName() {
    return myField.getName();
  }

  public Class<?> getValueClass() {
    return myField.getType();
  }

  public Type getGenericType() {
    return myField.getGenericType();
  }
}
