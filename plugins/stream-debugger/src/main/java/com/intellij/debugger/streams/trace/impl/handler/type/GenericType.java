package com.intellij.debugger.streams.trace.impl.handler.type;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface GenericType {
  @NotNull
  String getVariableTypeName();

  @NotNull
  String getGenericTypeName();

  GenericType INT = new GenericTypeImpl("int", "java.lang.Integer");
  GenericType DOUBLE = new GenericTypeImpl("double", "java.lang.Double");
  GenericType LONG = new GenericTypeImpl("long", "java.lang.Long");
  GenericType OBJECT = new ClassTypeImpl("java.lang.Object");
}
