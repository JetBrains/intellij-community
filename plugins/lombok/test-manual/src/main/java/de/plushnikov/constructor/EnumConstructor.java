package de.plushnikov.constructor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum EnumConstructor {

  A(1), B(2);

  @Getter
  private final int x;
}
