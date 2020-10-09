package de.plushnikov.wither;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;

@Builder
@Value
@Wither
public class BuilderDefaultValue {
  @lombok.Builder.Default
  int canSet = 0;
  int canNotSet = 0;
  int mustSet;

  public static void testMe() {
    BuilderDefaultValue.builder()
      .mustSet(1)
      .canSet(1);

//    BuilderDefaultValue.builder().canNotSet(1);

    new BuilderDefaultValue(1, 1);

    BuilderDefaultValue bdv = BuilderDefaultValue.builder()
      .mustSet(1)
      .canSet(1)
      .build();

    bdv.withCanSet(2).withMustSet(2);
  }
}
