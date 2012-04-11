package com.jetbrains.typoscript.lang;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(TypoScriptFileType.INSTANCE, TypoScriptFileType.INSTANCE.getDefaultExtension());
  }
}