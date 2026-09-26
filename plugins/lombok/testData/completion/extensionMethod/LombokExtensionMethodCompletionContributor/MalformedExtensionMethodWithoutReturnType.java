// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String text) {
    text.<caret>
  }

  static class Extensions {
    // method with missing return type
    public static myExtensionMethod(String text) {
    }
  }
}
