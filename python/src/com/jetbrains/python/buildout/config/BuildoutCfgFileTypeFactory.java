package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileType;

/**
 * @author traff
 */
public class BuildoutCfgFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(BuildoutCfgFileType.INSTANCE, BuildoutCfgFileType.DEFAULT_EXTENSION);
  }
}