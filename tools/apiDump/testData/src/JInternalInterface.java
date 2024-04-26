package com.intellij.tools.apiDump.testData;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;

@SuppressWarnings({"unused"})
@Internal
public interface JInternalInterface {

  //@formatter:off
                class PublicClass {}
  @Internal     class InternalClass {}
  @Experimental class ExperimentalClass {}

            static void publicStaticMethod() {}
  @Internal static void internalStaticMethod() {}
  private   static void privateStaticMethod() {}
  //@formatter:on
}
