// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.apiDump.testData.inheritFromPackageLocal;

import org.jetbrains.annotations.ApiStatus.Internal;

//@formatter:off
@SuppressWarnings({"PublicConstructorInNonPublicClass", "unused"})
class JPackageLocalClass {
  public              Object publicField;
  protected           Object protectedField;
  @Internal public    Object internalField;
  @Internal protected Object internalProtectedField;
  private             Object privateField;
  Object packageLocalField;

  public              JPackageLocalClass() {}
  protected           JPackageLocalClass(byte b) {}
  @Internal public    JPackageLocalClass(int b) {}
  @Internal protected JPackageLocalClass(long b) {}
  private             JPackageLocalClass(char d) {}
                      JPackageLocalClass(double c) {}

  public              void publicMethod() {}
  protected           void protectedMethod() {}
  @Internal public    void internalMethod() {}
  @Internal protected void internalProtectedMethod() {}
  private             void privateMethod() {}
                      void packagePrivateMethod() {}

  public              static void publicStaticMethod() {}
  protected           static void protectedStaticMethod() {}
  @Internal public    static void internalStaticMethod() {}
  @Internal protected static void internalProtectedStaticMethod() {}
  private             static void privateStaticMethod() {}
                      static void packagePrivateStaticMethod() {}

  JPackageLocalClass packagePrivateMethodWithPackagePrivateReturnType() { return null; }
  protected JPackageLocalClass protectedMethodWithPackagePrivateReturnType() { return null; }

            public    static class PublicClass {}
            protected static class ProtectedClass {}
                      static class PackageLocalClass {}
  @Internal public    static class InternalClass {}
}
//@formatter:on
