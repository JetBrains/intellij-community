package com.intellij.tasks.youtrack.lang;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class YouTrackFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(YouTrackFileType.INSTANCE, YouTrackFileType.DEFAULT_EXTENSION);
  }
}
