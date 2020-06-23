package org.jetbrains.plugins.textmate.plist;

import java.util.Date;
import java.util.List;

public enum PlistValueType {
  STRING, INTEGER, REAL, BOOLEAN, DATE, ARRAY, DICT;

  public static PlistValueType fromObject(Object o) {
    if (o instanceof String) {
      return STRING;
    }
    if (o instanceof Long) {
      return INTEGER;
    }
    if (o instanceof Plist) {
      return DICT;
    }
    if (o instanceof List) {
      return ARRAY;
    }
    if (o instanceof Double) {
      return REAL;
    }
    if (o instanceof Boolean) {
      return BOOLEAN;
    }
    if (o instanceof Date) {
      return DATE;
    }
    throw new RuntimeException("Unknown type of object: " + o);
  }
}
