package com.intellij.lang.properties;

import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 11.02.2009
 * Time: 13:56:00
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(PropertiesFileType.FILE_TYPE, PropertiesFileType.DEFAULT_EXTENSION);
  }
}
