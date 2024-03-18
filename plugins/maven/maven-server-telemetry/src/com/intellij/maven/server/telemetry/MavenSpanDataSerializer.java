// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.telemetry;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collection;

public class MavenSpanDataSerializer {
  public static byte[] serialize(@NotNull Collection<SpanData> spanData) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      serializeIntoProtobuf(spanData, outputStream);
      return outputStream.toByteArray();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void serializeIntoProtobuf(@NotNull Collection<SpanData> spanData, @NotNull OutputStream outputStream)
    throws ReflectiveOperationException {
    Object marshaller = Class.forName("io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler")
      .getMethod("create", Collection.class)
      .invoke(null, spanData);
    marshaller.getClass()
      .getMethod("writeBinaryTo", OutputStream.class)
      .invoke(marshaller, outputStream);
  }
}

