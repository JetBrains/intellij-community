package com.intellij.bash;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

public class BashFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(BashFileType.INSTANCE);
    consumer.consume(BashFileType.INSTANCE, "bash");
  }
}
