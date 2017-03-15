package com.intellij.debugger.streams.wrapper;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public enum IntermediateCallType {
  OBJECT_MAPPER(ValueType.OBJECT, ValueType.OBJECT),
  PACK_OPERATION(ValueType.PRIMITIVE, ValueType.OBJECT),
  UNPACK_OPERATION(ValueType.OBJECT, ValueType.PRIMITIVE),
  PRIMITIVE_MAPPER(ValueType.PRIMITIVE, ValueType.PRIMITIVE);

  final ValueType myBefore;
  final ValueType myAfter;

  IntermediateCallType(@NotNull ValueType before, @NotNull ValueType after) {
    myBefore = before;
    myAfter = after;
  }

  @NotNull
  public ValueType getTypeBefore() {
    return myBefore;
  }

  @NotNull
  public ValueType getTypeAfter() {
    return myAfter;
  }
}
