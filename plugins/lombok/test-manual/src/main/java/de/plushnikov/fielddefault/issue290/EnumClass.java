package de.plushnikov.fielddefault.issue290;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum EnumClass {
  ENUM1, ENUM2
}
