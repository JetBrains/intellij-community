/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StreamLoader implements Loader {
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

  static void doLoad(@NotNull InputStream stream, @NotNull Consumer<String> consumer) {
    doLoad(stream, consumer, StandardCharsets.UTF_8);
  }

  static void doLoad(@NotNull InputStream stream, @NotNull Consumer<String> consumer, Charset charset) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset))) {
      br.lines().forEach(consumer::consume);
    }
    catch (Exception e) {
      Logger.getInstance(StreamLoader.class).error(e);
    }
  }
}
