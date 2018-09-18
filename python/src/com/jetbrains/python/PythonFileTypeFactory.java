// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Override
  public void createFileTypes(@NonNls @NotNull final FileTypeConsumer consumer) {
    consumer.consume(PythonFileType.INSTANCE, "py;pyw;");
    consumer.consume(QtUIFileType.INSTANCE, "ui");
    consumer.consume(XmlFileType.INSTANCE, "qrc");
  }
}