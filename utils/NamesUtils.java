package org.jetbrains.debugger.memory.utils;

import com.sun.jdi.ObjectReference;

class NamesUtils {
  static String getUniqueName(ObjectReference ref) {
    String name = ref.referenceType().name().replace("[]", "Array");
    return String.format("%s@%d", name.substring(name.lastIndexOf('.') + 1), ref.uniqueID());
  }
}
