// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.spellchecker.dictionary.Loader;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class StreamLoader implements Loader {
  private final InputStream stream;
  private final String name;

  public StreamLoader(InputStream stream, String name) {
    this.stream = stream;
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void load(@NotNull Consumer<String> consumer) {
    doLoad(stream, consumer);
  }

  static void doLoad(@NotNull InputStream stream, @NotNull Consumer<? super String> consumer) {
    doLoad(stream, consumer, StandardCharsets.UTF_8);
  }

  static void doLoad(@NotNull InputStream stream, @NotNull Consumer<? super String> consumer, Charset charset) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset))) {
      br.lines().forEach(consumer);
    }
    catch (Exception e) {
      Logger.getInstance(StreamLoader.class).error(e);
    }
  }
}
