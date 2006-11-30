package com.intellij.util.xmlb;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

interface Accessor {
  Object read(Object o);

  void write(Object o, Object value);

  Annotation[] getAnnotations();

  String getName();

  Class<?> getValueClass();

  Type getGenericType();
}
