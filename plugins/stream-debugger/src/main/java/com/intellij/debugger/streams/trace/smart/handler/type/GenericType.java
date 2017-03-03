package com.intellij.debugger.streams.trace.smart.handler.type;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface GenericType {
  @NotNull
  String getVariableName();

  @NotNull
  String getGenericTypeName();

  GenericType INT = new GenericTypeImpl("int", "Integer");
  GenericType OBJECT = new ClassTypeImpl("Object");
}
