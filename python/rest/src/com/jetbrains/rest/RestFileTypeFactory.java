package com.jetbrains.rest;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(RestFileType.INSTANCE, RestFileType.DEFAULT_EXTENSION);
  }
}