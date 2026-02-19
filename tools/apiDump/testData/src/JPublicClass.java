package com.intellij.tools.apiDump.testData;

import org.jetbrains.annotations.ApiStatus.Internal;

//@formatter:off
@SuppressWarnings({"unused", "FinalStaticMethod", "FinalPrivateMethod"})
public class JPublicClass {

  // different field visibilities
  public              Object publicField;
  protected           Object protectedField;
  @Internal public    Object internalField;
  @Internal protected Object internalProtectedField;
  private             Object privateField;
                      Object packageLocalField;

  // primitive types
  public boolean booleanField;
  public byte byteField;
  public char charField;
  public double doubleField;
  public float floatField;
  public int intField;
  public long longField;
  public short shortField;

  public final Object finalField = null;

  public              JPublicClass(boolean b) {}
  protected           JPublicClass(byte b) {}
  @Internal public    JPublicClass(int b) {}
  @Internal protected JPublicClass(long b) {}
  private             JPublicClass(char d) {}
                      JPublicClass(double c) {}

  public              void publicMethod() {}
  protected           void protectedMethod() {}
  @Internal public    void internalMethod() {}
  @Internal protected void internalProtectedMethod() {}
  private             void privateMethod() {}
                      void packagePrivateMethod() {}

  public              final void publicFinalMethod() {}
  protected           final void protectedFinalMethod() {}
  @Internal public    final void internalFinalMethod() {}
  @Internal protected final void internalProtectedFinalMethod() {}
  private             final void privateFinalMethod() {}
                      final void packagePrivateFinalMethod() {}

  public              static void publicStaticMethod() {}
  protected           static void protectedStaticMethod() {}
  @Internal public    static void internalStaticMethod() {}
  @Internal protected static void internalProtectedStaticMethod() {}
  private             static void privateStaticMethod() {}
                      static void packagePrivateStaticMethod() {}

  public              static final void publicStaticFinalMethod() {}
  protected           static final void protectedStaticFinalMethod() {}
  @Internal public    static final void internalStaticFinalMethod() {}
  @Internal protected static final void internalProtectedStaticFinalMethod() {}
  private             static final void privateStaticFinalMethod() {}
                      static final void packagePrivateStaticFinalMethod() {}
}
//@formatter:on
