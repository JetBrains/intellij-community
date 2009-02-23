package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class VcsFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(new PatchFileType(), "patch;diff");
  }
}
