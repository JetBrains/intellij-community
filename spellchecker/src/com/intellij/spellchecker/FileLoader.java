// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.dictionary.Loader;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

public final class FileLoader implements Loader {
  private final String url;
  private final String name;

  public FileLoader(String url, String name) {
    this.url = url;
    this.name = name;
  }

  public FileLoader(String url) {
    this(url, url);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void load(@NotNull Consumer<String> consumer) {
    final VirtualFile file = VfsUtil.findFileByIoFile(new File(url), true);
    if (file == null || file.isDirectory()) {
      return;
    }

    Charset charset = file.getCharset();
    try (InputStream stream = file.getInputStream()) {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset))) {
        br.lines().forEach(consumer);
      }
    }
    catch (ProcessCanceledException | CancellationException exception) {
      throw exception;
    }
    catch (Exception e) {
      Logger.getInstance(FileLoader.class).error(e);
    }
  }
}
