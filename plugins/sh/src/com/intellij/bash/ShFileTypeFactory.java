package com.intellij.bash;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

public class ShFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(ShFileType.INSTANCE);
    consumer.consume(ShFileType.INSTANCE, "bash;zsh;fish");
  }
}
