// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import lombok.experimental.ExtensionMethod;

@ExtensionMethod(Test.Extensions.class)
class Test {
  void test(String s) {
    String result = s.extensionMethod<caret>(1);
  }

  static final class Extensions {
    public static Integer extensionMethod(Object receiver, int value) {
      return value;
    }

    public static String extensionMethod(String receiver, int value) {
      return receiver + value;
    }
  }
}
