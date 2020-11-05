// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.spellchecker.dictionary.Loader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public final class FileLoader implements Loader {
  private static final Logger LOG = Logger.getInstance(FileLoader.class);

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
    final VirtualFile file = findFileByIoFile(new File(url), true);
    if (file == null || file.isDirectory()) {
      return;
    }
    final Charset charset = file.getCharset();
    try (InputStream stream = file.getInputStream()) {
      StreamLoader.doLoad(stream, consumer, charset);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }
}
