package org.jetbrains.yaml;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class YAMLFileTypeLoader extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(YAMLFileType.YML, YAMLFileType.DEFAULT_EXTENSION + ";yaml");
  }
}