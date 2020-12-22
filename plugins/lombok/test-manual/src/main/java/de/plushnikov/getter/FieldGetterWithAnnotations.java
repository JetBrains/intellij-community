package de.plushnikov.getter;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class FieldGetterWithAnnotations {
  @Getter
  private Integer int1Property;

  @Getter
  @Setter
  @NonNull
  private Integer int2Integer;

  public Integer setInwet2Integer() {
    return 1;
  }

  public static void main(String[] args) {
    FieldGetterWithAnnotations annotations = new FieldGetterWithAnnotations();
    annotations.getInt1Property();
    annotations.setInwet2Integer();
  }
}
