package com.intellij.tools.apiDump.testData;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;

@SuppressWarnings({"unused", "FinalStaticMethod", "FinalPrivateMethod"})
@Internal
public class JInternalClass {

  //@formatter:off
                public static class PublicClass {}
             protected static class ProtectedClass {}
  @Internal     public static class InternalClass {}
  @Internal  protected static class InternalProtectedClass {}
               private static class PrivateClass {}
                       static class PPClass {}
  @Experimental public static class ExperimentalClass {}

  @Override
  public JInternalClass clone() throws CloneNotSupportedException {
    return (JInternalClass)super.clone();
  }

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
  //@formatter:on
}
