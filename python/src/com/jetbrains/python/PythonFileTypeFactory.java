package com.jetbrains.python;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@NonNls @NotNull final FileTypeConsumer consumer) {
    consumer.consume(PythonFileType.INSTANCE, "py");
  }
}
