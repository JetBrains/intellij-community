package com.intellij.uiDesigner;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FormFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(new GuiFormFileType(), GuiFormFileType.DEFAULT_EXTENSION);
  }
}
