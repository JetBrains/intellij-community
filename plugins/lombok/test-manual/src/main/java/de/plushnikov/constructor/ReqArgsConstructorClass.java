package de.plushnikov.constructor;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReqArgsConstructorClass {
  private int intProperty;

  private final float floatProperty;

  @NonNull
  private String stringProperty;
}
