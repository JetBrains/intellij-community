// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.telemetry;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class MavenTracingContext extends HashMap<String, String> {

  public static final TextMapGetter<MavenTracingContext> GETTER = new TextMapGetter<MavenTracingContext>() {
    @Override
    public Iterable<String> keys(@NotNull MavenTracingContext carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(@Nullable MavenTracingContext carrier, @NotNull String key) {
      if (carrier == null) {
        return null;
      }
      return carrier.get(key);
    }
  };

  public static final TextMapSetter<MavenTracingContext> SETTER = new TextMapSetter<MavenTracingContext>() {
    @Override
    public void set(@Nullable MavenTracingContext carrier, @NotNull String key, @NotNull String value) {
      if (carrier == null) {
        return;
      }
      carrier.put(key, value);
    }
  };
}
