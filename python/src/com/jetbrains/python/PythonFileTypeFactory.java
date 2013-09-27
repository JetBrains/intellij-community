package com.jetbrains.python;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.jetbrains.pyqt.QtUIFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@NonNls @NotNull final FileTypeConsumer consumer) {
    consumer.consume(PythonFileType.INSTANCE, "py;pyw;");
    consumer.consume(QtUIFileType.INSTANCE, "ui");
    consumer.consume(XmlFileType.INSTANCE, "qrc");
  }
}