package de.plushnikov.setter;

import lombok.NonNull;
import lombok.Setter;

public class FieldSetterWithAnnotations {
  @Setter
  private int int1Property;

  @Setter
  @NonNull
  private int int2Property;
}
