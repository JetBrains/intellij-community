package de.plushnikov.fielddefault;

import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true)
public class BitwiseOperators {
  short s1 = 2;
  short s2 = 4;

  void foo() {
    short s3 = s1 ^ s2;
  }

  void foo2() {
    short s3 = 1;
    switch (s3) {
      case s1 | s2:
        break;
    }
  }
}
