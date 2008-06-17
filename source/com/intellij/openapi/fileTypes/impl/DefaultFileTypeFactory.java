/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.*;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.vcs.changes.patch.PatchFileType;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultFileTypeFactory extends FileTypeFactory {

  public void createFileTypes(final @NotNull PairConsumer<FileType, String> consumer) {
    consumer.consume(new JavaClassFileType(), "class");

    consumer.consume(new JavaFileType(), "java");

    consumer.consume(new GuiFormFileType(), "form");
    consumer.consume(new WorkspaceFileType(), "iws");
    consumer.consume(new ModuleFileType(), "iml");
    consumer.consume(new ProjectFileType(), "ipr");
    consumer.consume(PropertiesFileType.FILE_TYPE, "properties");
    consumer.consume(new PatchFileType(), "patch;diff");
  }

}
