// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

public class ShFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(ShFileType.INSTANCE);
    consumer.consume(ShFileType.INSTANCE, "sh;zsh;fish");
  }
}
