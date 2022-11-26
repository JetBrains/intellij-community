// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import lombok.experimental.Wither;

@Wither
public record FindUsageWitherRecord(
  int foo,
  String b<caret>ar
) {

  public static void main(String[] args) {
    FindUsageWitherRecord findUsageWitherRecord = new FindUsageWitherRecord(1, "bar");
    findUsageWitherRecord
      .withBar("myBar")
      .withFoo(1981);
    System.out.println("Bar is: " + findUsageWitherRecord.bar());
    System.out.println("Foo is: " + findUsageWitherRecord.foo());
  }
}
