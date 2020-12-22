package de.plushnikov.builder.issue499;

import lombok.Builder;
import lombok.experimental.Accessors;

@Builder
public class TestClass {
  @Accessors(prefix = "m")
  private String mSample = "sample 1";

  public static void main(String[] args) {
    TestClass.builder().sample("sample 2").build();
    builder().sample("").build();
  }
}
