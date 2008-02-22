package com.jetbrains.python;

import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull PairConsumer<FileType, String> consumer) {
    consumer.consume(PythonFileType.INSTANCE, "py");
  }
}
