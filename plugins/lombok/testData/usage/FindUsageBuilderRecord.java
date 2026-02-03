// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import lombok.Builder;

@Builder
public record FindUsageBuilderRecord(
  int foo,
  String b<caret>ar
) {

  public static void main(String[] args) {
    FindUsageBuilderRecord findUsageBuilderRecord = FindUsageBuilderRecord.builder()
      .bar("bar")
      .foo(1981)
      .build();

    System.out.println("Bar is: " + findUsageBuilderRecord.bar());
    System.out.println("Foo is: " + findUsageBuilderRecord.foo());
  }
}
