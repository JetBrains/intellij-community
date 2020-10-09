package de.plushnikov.builder.builderdefault;

import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@ToString
public class LombokTest {
  @NonNull
  String value;

  @Builder.Default
  String valueD = "foo";

  public static void main(String[] args) {
    LombokTest lombokTest = new LombokTest("value", "valueD");

    LombokTest someVar = LombokTest.builder()
      .value("someValue")
      .valueD("someDValue")
      .build();
    System.out.println(someVar);
  }
}
