package com.intellij.tools.apiDump.testData;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;

@SuppressWarnings("unused")
@Experimental
public class JExperimentalClass {

  //@formatter:off
                public static class PublicClass {}
             protected static class ProtectedClass {}
  @Internal     public static class InternalClass {}
  @Internal  protected static class InternalProtectedClass {}
               private static class PrivateClass {}
                       static class PPClass {}
  @Experimental public static class ExperimentalClass {}
  //@formatter:on
}
