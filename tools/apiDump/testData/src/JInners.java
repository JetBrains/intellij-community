package com.intellij.tools.apiDump.testData;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;

@SuppressWarnings({"unused", "ResultOfObjectAllocationIgnored"})
public class JInners {
  public Object anonymousClass = new Object() {
  };

  public void ownerMethod() {
    record LocalRecord() {
    }
    class LocalClass {
    }
    new Object() {
    };
  }

  //@formatter:off
  public static class PublicClass {
                  public static class PublicClassD {}
               protected static class ProtectedClass {}
    @Internal     public static class InternalClass {}
    @Internal  protected static class InternalProtectedClass {}
                 private static class PrivateClass {}
                         static class PPClass {}
    @Experimental public static class ExperimentalClass {}
  }
  protected static class ProtectedClass {
                  public static class PublicClass {}
               protected static class ProtectedClassD {}
    @Internal     public static class InternalClass {}
    @Internal  protected static class InternalProtectedClass {}
                 private static class PrivateClass {}
                         static class PPClass {}
    @Experimental public static class ExperimentalClass {}
  }
  @Internal public static class InternalClass {
                  public static class PublicClass {}
               protected static class ProtectedClass {}
    @Internal     public static class InternalClassD {}
    @Internal  protected static class InternalProtectedClass {}
                 private static class PrivateClass {}
                         static class PPClass {}
    @Experimental public static class ExperimentalClass {}
  }
  @Internal protected static class InternalProtectedClass {
                  public static class PublicClass {}
               protected static class ProtectedClass {}
    @Internal     public static class InternalClass {}
    @Internal  protected static class InternalProtectedClassD {}
                 private static class PrivateClass {}
                         static class PPClass {}
    @Experimental public static class ExperimentalClass {}
  }
  private static class PrivateClass {
                  public static class PublicClass {}
               protected static class ProtectedClass {}
    @Internal     public static class InternalClass {}
    @Internal  protected static class InternalProtectedClass {}
                 private static class PrivateClassD {}
                         static class PPClass {}
    @Experimental public static class ExperimentalClass {}
  }
  static class PPClass {
                  public static class PublicClass {}
               protected static class ProtectedClass {}
    @Internal     public static class InternalClass {}
    @Internal  protected static class InternalProtectedClass {}
                 private static class PrivateClass {}
                         static class PPClassD {}
    @Experimental public static class ExperimentalClass {}
  }
  @Experimental public static class ExperimentalClass {
                  public static class PublicClass {}
               protected static class ProtectedClass {}
    @Internal     public static class InternalClass {}
    @Internal  protected static class InternalProtectedClass {}
                 private static class PrivateClass {}
                         static class PPClass {}
    @Experimental public static class ExperimentalClassD {}
  }
  //@formatter:on
}
